package com.alekseevich47.callmemo.telecom

import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.alekseevich47.callmemo.data.system.SystemContactsDataSource
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class AudioEndpointOption(
    val id: String,
    val label: String,
    val route: Int,
    /** Opaque token for API 34+ CallEndpoint selection; null on older APIs. */
    val endpointToken: Any? = null,
)

data class InCallSessionState(
    val hasCall: Boolean = false,
    val callState: Int = Call.STATE_NEW,
    val number: String = "",
    val displayName: String = "",
    val direction: CallDirection = CallDirection.OUTGOING,
    val muted: Boolean = false,
    val onHold: Boolean = false,
    val audioRoute: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
    val endpoints: List<AudioEndpointOption> = emptyList(),
    val canAddCall: Boolean = false,
    /** elapsedRealtime when call became ACTIVE; null until connected. */
    val connectedAtElapsedMs: Long? = null,
)

/**
 * Bridges [InCallService] ↔ Compose UI for the default-dialer in-call experience.
 *
 * Security: never logs raw numbers; contact lookup requires READ_CONTACTS already granted
 * by the dialer permission set; audio-route changes go only through Telecom APIs.
 */
@Singleton
class CallSessionController @Inject constructor(
    private val contacts: SystemContactsDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serviceRef = AtomicReference<InCallService?>(null)
    private val activeCallRef = AtomicReference<Call?>(null)
    private var connectedAtElapsedMs: Long? = null

    private val _state = MutableStateFlow(InCallSessionState())
    val state: StateFlow<InCallSessionState> = _state.asStateFlow()

    init {
        CallSessionTracker.attach(this)
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (activeCallRef.get() !== call) return
            onTelecomState(state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (activeCallRef.get() !== call) return
            refreshIdentity(call)
        }
    }

    fun bindService(service: InCallService) {
        serviceRef.set(service)
        publishAudioFromService(service)
    }

    fun unbindService(service: InCallService) {
        serviceRef.compareAndSet(service, null)
    }

    fun onCallAdded(call: Call) {
        activeCallRef.get()?.unregisterCallback(callCallback)
        activeCallRef.set(call)
        call.registerCallback(callCallback)
        connectedAtElapsedMs = null
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val incoming = call.state == Call.STATE_RINGING
        val callerHint = call.details?.callerDisplayName
            ?.takeIf { it.isNotBlank() && !PhoneNumberUtils.sameSubscriber(it, number) }
        _state.value = InCallSessionState(
            hasCall = true,
            callState = call.state,
            number = number,
            displayName = callerHint ?: number,
            direction = if (incoming) CallDirection.INCOMING else CallDirection.OUTGOING,
            onHold = call.state == Call.STATE_HOLDING,
            canAddCall = serviceRef.get()?.canAddCall() == true,
        )
        if (call.state == Call.STATE_ACTIVE) {
            markConnected()
        }
        refreshIdentity(call)
        serviceRef.get()?.let { publishAudioFromService(it) }
    }

    fun onCallRemoved(call: Call) {
        if (activeCallRef.get() !== call) return
        call.unregisterCallback(callCallback)
        activeCallRef.set(null)
        connectedAtElapsedMs = null
        _state.value = InCallSessionState()
    }

    fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (audioState == null) return
        _state.update { current ->
            current.copy(
                muted = audioState.isMuted,
                audioRoute = audioState.route,
                supportedRoutes = audioState.supportedRouteMask,
                endpoints = endpointsFromAudioState(audioState),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onCallEndpointChanged(endpoint: CallEndpoint) {
        _state.update { current ->
            current.copy(
                audioRoute = routeFromEndpointType(endpoint.endpointType),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onAvailableCallEndpointsChanged(endpoints: List<CallEndpoint>) {
        _state.update { current ->
            current.copy(
                endpoints = endpoints.map { ep ->
                    AudioEndpointOption(
                        id = "ep-${ep.endpointType}-${ep.identifier}",
                        label = ep.endpointName?.toString().orEmpty()
                            .ifBlank { labelForRoute(routeFromEndpointType(ep.endpointType)) },
                        route = routeFromEndpointType(ep.endpointType),
                        endpointToken = ep,
                    )
                },
            )
        }
    }

    fun onMuteStateChanged(isMuted: Boolean) {
        _state.update { it.copy(muted = isMuted) }
    }

    fun onCanAddCallChanged(canAdd: Boolean) {
        _state.update { it.copy(canAddCall = canAdd) }
    }

    fun answer() {
        activeCallRef.get()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        activeCallRef.get()?.reject(false, null)
    }

    fun disconnect() {
        activeCallRef.get()?.disconnect()
    }

    fun setMuted(muted: Boolean) {
        serviceRef.get()?.setMuted(muted)
        // Optimistic UI for demo / laggy OEM callbacks.
        _state.update { it.copy(muted = muted) }
    }

    fun toggleMute() {
        setMuted(!_state.value.muted)
    }

    fun toggleHold() {
        val call = activeCallRef.get()
        if (call == null) {
            // Demo / test incoming path without a live Telecom call.
            _state.update { it.copy(onHold = !it.onHold) }
            return
        }
        when (call.state) {
            Call.STATE_HOLDING -> call.unhold()
            Call.STATE_ACTIVE -> call.hold()
            else -> Unit
        }
    }

    fun setSpeaker(enabled: Boolean) {
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }

    fun toggleSpeaker() {
        val onSpeaker = _state.value.audioRoute == CallAudioState.ROUTE_SPEAKER
        setSpeaker(!onSpeaker)
    }

    fun setAudioRoute(route: Int) {
        val service = serviceRef.get()
        if (service == null) {
            _state.update { it.copy(audioRoute = route) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val match = _state.value.endpoints.firstOrNull { it.route == route }
            val endpoint = match?.endpointToken as? CallEndpoint
            if (endpoint != null) {
                requestEndpoint(service, endpoint)
                return
            }
        }
        @Suppress("DEPRECATION")
        service.setAudioRoute(route)
        _state.update { it.copy(audioRoute = route) }
    }

    fun selectEndpoint(option: AudioEndpointOption) {
        val service = serviceRef.get()
        if (service == null) {
            _state.update { it.copy(audioRoute = option.route) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpoint = option.endpointToken as? CallEndpoint
            if (endpoint != null) {
                requestEndpoint(service, endpoint)
                return
            }
        }
        val bluetoothDevice = option.endpointToken as? BluetoothDevice
        if (bluetoothDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            service.requestBluetoothAudio(bluetoothDevice)
            _state.update { it.copy(audioRoute = CallAudioState.ROUTE_BLUETOOTH) }
            return
        }
        if (option.route == CallAudioState.ROUTE_BLUETOOTH &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            @Suppress("DEPRECATION")
            val device = service.callAudioState?.supportedBluetoothDevices?.firstOrNull()
            if (device != null) {
                @Suppress("DEPRECATION")
                service.requestBluetoothAudio(device)
                _state.update { it.copy(audioRoute = CallAudioState.ROUTE_BLUETOOTH) }
                return
            }
        }
        setAudioRoute(option.route)
    }

    fun playDtmf(digit: Char) {
        val call = activeCallRef.get() ?: return
        if (digit !in DTMF_CHARS) return
        call.playDtmfTone(digit)
    }

    fun stopDtmf() {
        activeCallRef.get()?.stopDtmfTone()
    }

    fun activeCall(): Call? = activeCallRef.get()

    /** Snapshot helpers for launching UI before Compose collects Flow. */
    fun currentNumber(): String = _state.value.number
    fun currentDisplayName(): String = _state.value.displayName
    fun currentDirection(): CallDirection = _state.value.direction

    private fun onTelecomState(state: Int) {
        when (state) {
            Call.STATE_ACTIVE -> markConnected()
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                // connectedAt kept until onCallRemoved clears session
            }
            else -> Unit
        }
        _state.update {
            it.copy(
                callState = state,
                onHold = state == Call.STATE_HOLDING,
                connectedAtElapsedMs = connectedAtElapsedMs,
            )
        }
    }

    private fun markConnected() {
        if (connectedAtElapsedMs == null) {
            connectedAtElapsedMs = SystemClock.elapsedRealtime()
        }
        _state.update {
            it.copy(
                callState = Call.STATE_ACTIVE,
                onHold = false,
                connectedAtElapsedMs = connectedAtElapsedMs,
            )
        }
    }

    private fun refreshIdentity(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val telecomName = call.details?.callerDisplayName
            ?.takeIf { it.isNotBlank() && !looksLikeSameNumber(it, number) }
        scope.launch(Dispatchers.IO) {
            val contactName = contacts.lookupDisplayName(number)
            val resolved = PhoneNumberUtils.displayOrUnknown(
                contactName ?: telecomName,
                number,
            )
            _state.update { current ->
                if (!current.hasCall) current
                else current.copy(
                    number = number.ifBlank { current.number },
                    displayName = resolved,
                )
            }
        }
    }

    private fun publishAudioFromService(service: InCallService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                onCallEndpointChanged(service.currentCallEndpoint)
            }
        }
        @Suppress("DEPRECATION")
        onCallAudioStateChanged(service.callAudioState)
        onCanAddCallChanged(service.canAddCall())
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestEndpoint(service: InCallService, endpoint: CallEndpoint) {
        val executor = Executor { runnable -> runnable.run() }
        service.requestCallEndpointChange(
            endpoint,
            executor,
            object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                override fun onResult(result: Void?) {
                    _state.update {
                        it.copy(audioRoute = routeFromEndpointType(endpoint.endpointType))
                    }
                }

                override fun onError(error: android.telecom.CallEndpointException) {
                    // Keep previous route; OEM may deny the switch.
                }
            },
        )
    }

    private fun endpointsFromAudioState(audioState: CallAudioState): List<AudioEndpointOption> {
        val mask = audioState.supportedRouteMask
        val options = mutableListOf<AudioEndpointOption>()
        fun add(route: Int, label: String) {
            if (mask and route == route) {
                options += AudioEndpointOption(
                    id = "route-$route",
                    label = label,
                    route = route,
                )
            }
        }
        add(CallAudioState.ROUTE_EARPIECE, "Телефон")
        add(CallAudioState.ROUTE_SPEAKER, "Динамик")
        add(CallAudioState.ROUTE_WIRED_HEADSET, "Гарнитура")
        add(CallAudioState.ROUTE_WIRED_OR_EARPIECE, "Проводная / трубка")
        if (mask and CallAudioState.ROUTE_BLUETOOTH == CallAudioState.ROUTE_BLUETOOTH) {
            val devices = audioState.supportedBluetoothDevices.orEmpty()
            if (devices.isEmpty()) {
                add(CallAudioState.ROUTE_BLUETOOTH, "Bluetooth")
            } else {
                devices.forEachIndexed { index, device ->
                    val name = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // Requires BLUETOOTH_CONNECT; fall back if denied.
                            device.name
                        } else {
                            @Suppress("DEPRECATION")
                            device.name
                        }
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth ${index + 1}"
                    options += AudioEndpointOption(
                        id = "bt-${device.address ?: index}",
                        label = name,
                        route = CallAudioState.ROUTE_BLUETOOTH,
                        endpointToken = device,
                    )
                }
            }
        }
        return options
    }

    private fun looksLikeSameNumber(name: String, number: String): Boolean {
        if (PhoneNumberUtils.sameSubscriber(name, number)) return true
        val nameDigits = PhoneNumberUtils.digitsOnly(name)
        val numberDigits = PhoneNumberUtils.digitsOnly(number)
        return nameDigits.isNotEmpty() && nameDigits == numberDigits
    }

    private fun routeFromEndpointType(type: Int): Int = when (type) {
        CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
        CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
        CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        else -> CallAudioState.ROUTE_EARPIECE
    }

    private fun labelForRoute(route: Int): String = when (route) {
        CallAudioState.ROUTE_SPEAKER -> "Динамик"
        CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
        CallAudioState.ROUTE_WIRED_HEADSET -> "Гарнитура"
        else -> "Телефон"
    }

    companion object {
        private val DTMF_CHARS = setOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#',
        )
    }
}

/**
 * Thin facade kept for existing call sites ([InCallViewModel], etc.).
 * Prefer [CallSessionController] for new code.
 */
object CallSessionTracker {
    @Volatile
    private var controller: CallSessionController? = null

    internal fun attach(controller: CallSessionController) {
        this.controller = controller
    }

    val activeCall: Call?
        get() = controller?.activeCall()

    fun answer() {
        controller?.answer()
    }

    fun reject() {
        controller?.reject()
    }

    fun disconnect() {
        controller?.disconnect()
    }
}
