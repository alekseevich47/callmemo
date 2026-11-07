package com.alekseevich47.callmemo.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.components.SectionCard
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
    onPlay: (Long, String, String, String?, Long) -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val entry = detail?.entry
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CallMemoMist)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
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
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = null)
            }
        }

        if (entry == null) {
            Text("Загрузка…", modifier = Modifier.padding(20.dp), color = CallMemoMuted)
            return
        }

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
            QuickAction("СМС", Icons.AutoMirrored.Outlined.Message) {}
            QuickAction("Видео", Icons.Outlined.Videocam) {}
            QuickAction("Ещё", Icons.Outlined.MoreHoriz) {}
        }

        Spacer(Modifier.height(18.dp))
        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Предыдущие звонки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Всего с этим абонентом: ${detail?.history?.size ?: 0}",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            detail?.history.orEmpty().forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (item.direction) {
                                com.alekseevich47.callmemo.domain.model.CallDirection.OUTGOING -> "Исходящий"
                                com.alekseevich47.callmemo.domain.model.CallDirection.INCOMING -> "Входящий"
                                com.alekseevich47.callmemo.domain.model.CallDirection.MISSED -> "Пропущенный"
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
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Расшифровка",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val clipboard = LocalClipboardManager.current
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
            WaveformPlaceholder()
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.18f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = CallMemoBlue,
                trackColor = CallMemoSky,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0:00", color = CallMemoMuted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    TimeFormat.duration(entry.durationSeconds),
                    color = CallMemoMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onPlay(
                            entry.id,
                            entry.contactName.orEmpty().ifBlank { entry.number },
                            entry.number,
                            entry.recordingUri,
                            entry.durationSeconds.coerceAtLeast(1) * 1000L,
                        )
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CallMemoBlue),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Слушать", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "Громкость", tint = CallMemoMuted)
                }
            }
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

@Composable
private fun WaveformPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val heights = listOf(12, 22, 18, 28, 14, 24, 30, 16, 26, 20, 12, 28, 18, 22, 14, 26, 20, 30, 16, 22)
        heights.forEach { h ->
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CallMemoBlue.copy(alpha = 0.35f)),
            )
        }
    }
}
