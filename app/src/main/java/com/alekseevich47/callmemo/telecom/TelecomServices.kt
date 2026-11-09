package com.alekseevich47.callmemo.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.alekseevich47.callmemo.MainActivity
import com.alekseevich47.callmemo.domain.model.CallDirection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System InCall UI entry when CallMemo is the default dialer.
 */
@AndroidEntryPoint
class CallMemoInCallService : InCallService() {

    @Inject
    lateinit var session: CallSessionController

    override fun onCreate() {
        super.onCreate()
        CallSessionTracker.attach(session)
        session.bindService(this)
    }

    override fun onDestroy() {
        session.unbindService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        session.onCallAdded(call)
        val number = session.currentNumber().ifBlank {
            call.details?.handle?.schemeSpecificPart.orEmpty()
        }
        val name = session.currentDisplayName().ifBlank { number }
        val incoming = call.state == Call.STATE_RINGING
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            action = if (incoming) MainActivity.ACTION_SHOW_INCOMING else MainActivity.ACTION_SHOW_IN_CALL
            putExtra(MainActivity.EXTRA_NUMBER, number)
            putExtra(MainActivity.EXTRA_NAME, name)
            putExtra(
                MainActivity.EXTRA_DIRECTION,
                if (incoming) CallDirection.INCOMING.name else CallDirection.OUTGOING.name,
            )
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        session.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        session.onCallAudioStateChanged(audioState)
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        session.onMuteStateChanged(isMuted)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        super.onCanAddCallChanged(canAddCall)
        session.onCanAddCallChanged(canAddCall)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            session.onCallEndpointChanged(callEndpoint)
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            session.onAvailableCallEndpointsChanged(availableEndpoints)
        }
    }
}

/**
 * Stub ConnectionService required by some OEMs when registering a PhoneAccount for ROLE_DIALER.
 *
 * Real PSTN calls must go through the SIM Telephony ConnectionService — see
 * [DialerRoleHelper.placeCall], which prefers a non-CallMemo PhoneAccountHandle.
 * This stub must never pretend an outgoing call is ACTIVE (that broke the call timer).
 */
class CallMemoConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                setActive()
            }

            override fun onReject() {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }
        connection.setInitializing()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(
            request?.address?.schemeSpecificPart,
            TelecomManager.PRESENTATION_ALLOWED,
        )
        // Stay in DIALING — never auto-ACTIVE. Without a real modem path this account
        // should not be selected; placeCall excludes our handle explicitly.
        connection.setDialing()
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                setActive()
            }

            override fun onReject() {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }
        connection.setRinging()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        return connection
    }
}
