package com.alekseevich47.callmemo.ui.incall

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.player.ProximityPlaybackEffect
import com.alekseevich47.callmemo.telecom.AudioEndpointOption
import com.alekseevich47.callmemo.telecom.CallSessionController
import com.alekseevich47.callmemo.telecom.InCallSessionState
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoDanger
import com.alekseevich47.callmemo.ui.theme.CallMemoSuccess
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.alekseevich47.callmemo.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class InCallViewModel @Inject constructor(
    val session: CallSessionController,
) : ViewModel() {
    fun finishCall(onDone: () -> Unit) {
        session.disconnect()
        // Demo path (Settings → тестовый звонок) has no Telecom call.
        if (!session.state.value.hasCall) onDone()
    }

    fun answer() = session.answer()
    fun reject() = session.reject()
    fun toggleMute() = session.toggleMute()
    fun toggleSpeaker() = session.toggleSpeaker()
    fun toggleHold() = session.toggleHold()
    fun selectEndpoint(option: AudioEndpointOption) = session.selectEndpoint(option)
    fun playDtmf(digit: Char) = session.playDtmf(digit)
    fun stopDtmf() = session.stopDtmf()
}

@Composable
fun InCallRoute(
    name: String,
    number: String,
    direction: CallDirection = CallDirection.OUTGOING,
    onEnd: () -> Unit,
    viewModel: InCallViewModel = hiltViewModel(),
) {
    val session by viewModel.session.state.collectAsStateWithLifecycle()
    var streamOn by rememberSaveable { mutableStateOf(false) }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var showAudioSheet by rememberSaveable { mutableStateOf(false) }
    var dtmfBuffer by rememberSaveable { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }

    // Prefer live Telecom identity; fall back to nav args (test call / race before bind).
    val displayName = remember(session.displayName, session.number, name, number) {
        when {
            session.hasCall && session.displayName.isNotBlank() -> session.displayName
            name.isNotBlank() && !PhoneNumberUtils.sameSubscriber(name, number) -> name
            session.hasCall && session.number.isNotBlank() -> session.number
            else -> name.ifBlank { number }
        }
    }
    val displayNumber = if (session.hasCall && session.number.isNotBlank()) session.number else number
    val muted = session.muted
    val onHold = session.onHold
    val speakerOn = session.audioRoute == CallAudioState.ROUTE_SPEAKER
    val bluetoothOn = session.audioRoute == CallAudioState.ROUTE_BLUETOOTH
    // Proximity screen-off only on earpiece during a live call (not speaker/BT/keypad).
    val proximityActive = session.hasCall &&
        (session.callState == Call.STATE_ACTIVE || session.callState == Call.STATE_DIALING ||
            session.callState == Call.STATE_CONNECTING || session.callState == Call.STATE_HOLDING) &&
        !speakerOn &&
        !bluetoothOn &&
        !showKeypad

    ProximityPlaybackEffect(active = proximityActive) { /* wake-lock only; Telecom owns audio */ }

    // Timer: Telecom → only after ACTIVE. Demo (Settings test call, no Telecom) → on screen enter.
    LaunchedEffect(session.connectedAtElapsedMs, session.hasCall) {
        if (session.hasCall) {
            val started = session.connectedAtElapsedMs
            if (started == null) {
                seconds = 0
                return@LaunchedEffect
            }
            while (true) {
                seconds = ((SystemClock.elapsedRealtime() - started) / 1_000L).toInt().coerceAtLeast(0)
                delay(250)
            }
        } else {
            val demoStart = SystemClock.elapsedRealtime()
            while (true) {
                seconds = ((SystemClock.elapsedRealtime() - demoStart) / 1_000L).toInt().coerceAtLeast(0)
                delay(250)
            }
        }
    }

    var sawLiveCall by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session.hasCall) {
        if (session.hasCall) {
            sawLiveCall = true
        } else if (sawLiveCall) {
            onEnd()
        }
    }

    val statusText = when {
        onHold -> null // hold row rendered separately
        session.hasCall && session.connectedAtElapsedMs != null -> TimeFormat.duration(seconds.toLong())
        !session.hasCall -> TimeFormat.duration(seconds.toLong())
        session.callState == Call.STATE_DIALING || session.callState == Call.STATE_CONNECTING -> "Вызов…"
        session.callState == Call.STATE_RINGING -> "Входящий…"
        session.callState == Call.STATE_SELECT_PHONE_ACCOUNT -> "Выбор линии…"
        else -> statusForDirection(direction)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1A2B), Color(0xFF1A2740), Color(0xFF0B1220)),
                ),
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AvatarCircle(label = displayName, color = CallMemoBlue)
            Spacer(Modifier.height(16.dp))
            Text(displayName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            if (displayNumber.isNotBlank() &&
                !PhoneNumberUtils.sameSubscriber(displayName, displayNumber) &&
                displayName != displayNumber
            ) {
                Text(
                    displayNumber,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (onHold) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Pause,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "На удержании",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (session.connectedAtElapsedMs != null) {
                    Text(
                        text = TimeFormat.duration(seconds.toLong()),
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))

            if (showKeypad) {
                InCallKeypad(
                    buffer = dtmfBuffer,
                    onDigit = { digit ->
                        dtmfBuffer += digit
                        viewModel.playDtmf(digit)
                        viewModel.stopDtmf()
                    },
                    onHide = { showKeypad = false },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    InCallAction(
                        label = "Без звука",
                        icon = Icons.Outlined.MicOff,
                        active = muted,
                        activeLight = true,
                    ) { viewModel.toggleMute() }
                    InCallAction("Клавиатура", Icons.Outlined.Dialpad) {
                        showKeypad = true
                    }
                    InCallAction(
                        label = "Динамик",
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        active = speakerOn,
                        activeLight = true,
                    ) { viewModel.toggleSpeaker() }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    InCallAction(
                        label = "Удержание",
                        icon = Icons.Outlined.Pause,
                        active = onHold,
                        activeLight = true,
                        activeIconBlue = true,
                    ) { viewModel.toggleHold() }
                    InCallAction(
                        label = "Добавить",
                        icon = Icons.Outlined.PersonAdd,
                        enabled = !session.hasCall || session.canAddCall,
                    ) {
                        // Hold current + user can place another call from dialer;
                        // full multi-call UI is a follow-up.
                        if (session.hasCall && session.callState == Call.STATE_ACTIVE) {
                            viewModel.toggleHold()
                        }
                    }
                    InCallAction(
                        label = "Bluetooth",
                        icon = Icons.Outlined.Bluetooth,
                        active = bluetoothOn,
                        activeLight = true,
                    ) { showAudioSheet = true }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { streamOn = !streamOn },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (streamOn) CallMemoSuccess else CallMemoBlue,
                ),
            ) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (streamOn) "Стрим включён" else "Транскрипция / Стрим")
            }
            if (streamOn) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Распознаю речь… «давай завтра в одиннадцать…»",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.finishCall(onDone = onEnd) },
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(CallMemoDanger),
            ) {
                Icon(
                    Icons.Outlined.CallEnd,
                    contentDescription = "Завершить",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAudioSheet) {
        AudioRouteSheet(
            endpoints = session.endpoints.ifEmpty { defaultEndpointOptions(session) },
            selectedRoute = session.audioRoute,
            onSelect = { option ->
                viewModel.selectEndpoint(option)
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false },
        )
    }
}

@Composable
fun IncomingRoute(
    name: String,
    number: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    viewModel: InCallViewModel = hiltViewModel(),
) {
    val session by viewModel.session.state.collectAsStateWithLifecycle()
    val displayName = when {
        session.hasCall && session.displayName.isNotBlank() -> session.displayName
        name.isNotBlank() && !PhoneNumberUtils.sameSubscriber(name, number) -> name
        else -> name.ifBlank { number }
    }
    val displayNumber = if (session.hasCall && session.number.isNotBlank()) session.number else number

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101826), Color(0xFF18233A)),
                ),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Входящий вызов", color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp))
        AvatarCircle(label = displayName, color = CallMemoBlue)
        Spacer(Modifier.height(16.dp))
        Text(displayName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        if (displayNumber.isNotBlank() &&
            !PhoneNumberUtils.sameSubscriber(displayName, displayNumber) &&
            displayName != displayNumber
        ) {
            Text(displayNumber, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        viewModel.reject()
                        onDecline()
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CallMemoDanger),
                ) {
                    Icon(Icons.Outlined.CallEnd, contentDescription = "Отклонить", tint = Color.White)
                }
                Text("Отклонить", color = Color.White.copy(alpha = 0.8f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        // Answer via Telecom first — stops ringtone / RINGING state.
                        viewModel.answer()
                        onAccept()
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CallMemoSuccess),
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = "Ответить", tint = Color.White)
                }
                Text("Ответить", color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = {
                viewModel.reject()
                onDecline()
            },
        ) {
            Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            Text("Ответить сообщением", color = Color.White)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRouteSheet(
    endpoints: List<AudioEndpointOption>,
    selectedRoute: Int,
    onSelect: (AudioEndpointOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A2740),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "Устройство звука",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            endpoints.forEach { option ->
                val selected = option.route == selectedRoute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) Color.White.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = iconForRoute(option.route),
                        contentDescription = null,
                        tint = if (selected) CallMemoBlue else Color.White,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        option.label,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InCallKeypad(
    buffer: String,
    onDigit: (Char) -> Unit,
    onHide: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buffer.ifBlank { " " },
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#'),
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    IconButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            digit.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        TextButton(onClick = onHide) {
            Text("Скрыть", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun InCallAction(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    activeLight: Boolean = false,
    activeIconBlue: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val bg = when {
        active && activeLight -> Color.White.copy(alpha = 0.92f)
        active -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        active && activeIconBlue -> CallMemoBlue
        active && activeLight -> CallMemoBlue
        else -> Color.White
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(bg),
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 0.75f else 0.35f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun statusForDirection(direction: CallDirection): String = when (direction) {
    CallDirection.INCOMING -> "Соединение…"
    CallDirection.OUTGOING -> "Вызов…"
    CallDirection.MISSED -> "Вызов…"
}

private fun defaultEndpointOptions(session: InCallSessionState): List<AudioEndpointOption> = listOf(
    AudioEndpointOption("earpiece", "Телефон", CallAudioState.ROUTE_EARPIECE),
    AudioEndpointOption("speaker", "Динамик", CallAudioState.ROUTE_SPEAKER),
    AudioEndpointOption("bt", "Bluetooth", CallAudioState.ROUTE_BLUETOOTH),
).filter {
    session.supportedRoutes == 0 || (session.supportedRoutes and it.route) == it.route ||
        it.route == CallAudioState.ROUTE_EARPIECE || it.route == CallAudioState.ROUTE_SPEAKER
}

private fun iconForRoute(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Outlined.VolumeUp
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Outlined.Bluetooth
    else -> Icons.Outlined.Call
}
