package com.alekseevich47.callmemo.ui.calls

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.callmemo.domain.model.CallDetail
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.player.PlaybackUiState
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.components.SectionCard
import com.alekseevich47.callmemo.ui.components.WaveformSeek
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMist
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.ui.theme.CallMemoSky
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.alekseevich47.callmemo.util.TimeFormat

@Composable
fun CallDetailRoute(
    onBack: () -> Unit,
    onCall: (String?, String) -> Unit,
    onOpenCall: (Long) -> Unit,
    onPlay: (Long, String, String, String?, Long, Long) -> Unit,
    playback: PlaybackUiState,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val entry = detail?.entry
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var headerMoreExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallMemoMist)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 140.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Детали звонка",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { headerMoreExpanded = true }) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё")
                }
                DropdownMenu(
                    expanded = headerMoreExpanded,
                    onDismissRequest = { headerMoreExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Копировать номер") },
                        onClick = {
                            entry?.number?.let { clipboard.setText(AnnotatedString(it)) }
                            headerMoreExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться") },
                        onClick = {
                            entry?.let { shareContact(context, it) }
                            headerMoreExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    )
                }
            }
        }

        if (entry == null) {
            Text("Загрузка…", modifier = Modifier.padding(20.dp), color = CallMemoMuted)
        } else {
            CallDetailBody(
                entry = entry,
                detail = detail,
                historyExpanded = historyExpanded,
                onHistoryExpandedChange = { historyExpanded = it },
                moreExpanded = moreExpanded,
                onMoreExpandedChange = { moreExpanded = it },
                playback = playback,
                onCall = onCall,
                onOpenCall = onOpenCall,
                onPlay = onPlay,
                onTogglePlayback = onTogglePlayback,
                onSeekPlayback = onSeekPlayback,
            )
        }
    }
}

@Composable
private fun CallDetailBody(
    entry: CallLogEntry,
    detail: CallDetail?,
    historyExpanded: Boolean,
    onHistoryExpandedChange: (Boolean) -> Unit,
    moreExpanded: Boolean,
    onMoreExpandedChange: (Boolean) -> Unit,
    playback: PlaybackUiState,
    onCall: (String?, String) -> Unit,
    onOpenCall: (Long) -> Unit,
    onPlay: (Long, String, String, String?, Long, Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Long) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val title = PhoneNumberUtils.displayOrUnknown(entry.contactName, entry.number)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarCircle(label = title, color = CallMemoBlue)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(entry.number, color = CallMemoMuted, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${TimeFormat.full(entry.startedAt)} · ${TimeFormat.duration(entry.durationSeconds)}",
            color = CallMemoMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction("Звонок", Icons.Outlined.Call) { onCall(entry.contactName, entry.number) }
        QuickAction("СМС", Icons.AutoMirrored.Outlined.Message) {
            openSms(context, entry.number)
        }
        QuickAction("Видео", Icons.Outlined.Videocam) {
            openVideoCall(context, entry.number)
        }
        Box {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onMoreExpandedChange(true) },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CallMemoSky),
                ) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё", tint = CallMemoBlue)
                }
                Text("Ещё", style = MaterialTheme.typography.bodyMedium, color = CallMemoMuted)
            }
            DropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = { onMoreExpandedChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("Копировать номер") },
                    onClick = {
                        clipboard.setText(AnnotatedString(entry.number))
                        onMoreExpandedChange(false)
                    },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Добавить в контакты") },
                    onClick = {
                        openAddContact(context, entry.contactName, entry.number)
                        onMoreExpandedChange(false)
                    },
                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Поделиться") },
                    onClick = {
                        shareContact(context, entry)
                        onMoreExpandedChange(false)
                    },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    val history = detail?.history.orEmpty()
    SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Предыдущие звонки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Всего с этим абонентом: ${history.size}",
            color = CallMemoMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        val visible = if (historyExpanded) history else history.take(3)
        visible.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            HistoryRow(
                item = item,
                selected = item.id == entry.id,
                onClick = {
                    if (item.id != entry.id) onOpenCall(item.id)
                },
            )
        }
        if (history.size > 3) {
            Spacer(Modifier.height(8.dp))
            IconButton(
                onClick = { onHistoryExpandedChange(!historyExpanded) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = if (historyExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (historyExpanded) "Свернуть" else "Показать все",
                    tint = CallMemoBlue,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Расшифровка",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    detail?.transcript?.let { clipboard.setText(AnnotatedString(it)) }
                },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать")
            }
        }
        Text(
            text = detail?.transcript ?: "Расшифровка ещё не готова",
            style = MaterialTheme.typography.bodyLarge,
            color = if (detail?.transcript == null) CallMemoMuted else CallMemoInk,
        )
        Spacer(Modifier.height(16.dp))

        val isThisPlaying = playback.visible && playback.callId == entry.id
        val durationMs = if (isThisPlaying) {
            playback.durationMs.coerceAtLeast(1L)
        } else {
            entry.durationSeconds.coerceAtLeast(1) * 1000L
        }
        val positionMs = if (isThisPlaying) playback.positionMs else 0L
        val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (isThisPlaying) {
                        onTogglePlayback()
                    } else {
                        onPlay(
                            entry.id,
                            entry.contactName.orEmpty().ifBlank { entry.number },
                            entry.number,
                            entry.recordingUri,
                            durationMs,
                            entry.startedAt.toEpochMilli(),
                        )
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CallMemoBlue),
            ) {
                Icon(
                    imageVector = if (isThisPlaying && playback.playing) {
                        Icons.Outlined.Pause
                    } else {
                        Icons.Outlined.PlayArrow
                    },
                    contentDescription = "Слушать",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                WaveformSeek(
                    progress = progress,
                    amplitudes = if (isThisPlaying) playback.amplitudes else emptyList(),
                    filledColor = CallMemoBlue,
                    emptyColor = CallMemoBlue.copy(alpha = 0.22f),
                    onSeekFraction = { fraction ->
                        if (isThisPlaying) {
                            onSeekPlayback((fraction * durationMs).toLong())
                        } else {
                            onPlay(
                                entry.id,
                                entry.contactName.orEmpty().ifBlank { entry.number },
                                entry.number,
                                entry.recordingUri,
                                durationMs,
                                entry.startedAt.toEpochMilli(),
                            )
                            onSeekPlayback((fraction * durationMs).toLong())
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        TimeFormat.duration(positionMs / 1000),
                        color = CallMemoMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        TimeFormat.duration(durationMs / 1000),
                        color = CallMemoMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: CallLogEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (selected) CallMemoSky.copy(alpha = 0.55f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (item.direction) {
                    CallDirection.OUTGOING -> "Исходящий"
                    CallDirection.INCOMING -> "Входящий"
                    CallDirection.MISSED -> "Пропущенный"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = item.transcriptPreview ?: "Без расшифровки",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TimeFormat.callWhen(item.startedAt), color = CallMemoMuted, style = MaterialTheme.typography.bodyMedium)
            Text(TimeFormat.duration(item.durationSeconds), color = CallMemoMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CallMemoSky),
        ) {
            Icon(icon, contentDescription = label, tint = CallMemoBlue)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CallMemoMuted)
    }
}

private fun openSms(context: android.content.Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Нет приложения для СМС", Toast.LENGTH_SHORT).show()
        }
}

private fun openVideoCall(context: android.content.Context, number: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:$number")).apply {
        putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", 3)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Видеозвонок недоступен", Toast.LENGTH_SHORT).show()
        }
}

private fun openAddContact(context: android.content.Context, name: String?, number: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
        putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name.orEmpty())
        putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Не удалось открыть контакты", Toast.LENGTH_SHORT).show()
        }
}

private fun shareContact(context: android.content.Context, entry: CallLogEntry) {
    val text = buildString {
        entry.contactName?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
        append(entry.number)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}
