package com.alekseevich47.callmemo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekseevich47.callmemo.player.PlaybackUiState
import com.alekseevich47.callmemo.ui.navigation.TopLevelDestination
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.util.TimeFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ScreenHeader(
    title: String,
    onSettings: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = CallMemoInk,
            modifier = Modifier.weight(1f),
        )
        trailing()
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = CallMemoInk)
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldModifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp)),
        placeholder = { Text(placeholder, color = CallMemoMuted) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = CallMemoMuted) },
        trailingIcon = trailingIcon,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = CallMemoBlue,
        ),
    )
}

@Composable
fun AvatarCircle(label: String, color: Color = CallMemoBlue, size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun tabIcon(destination: TopLevelDestination): ImageVector = when (destination) {
    TopLevelDestination.CALLS -> Icons.Outlined.Call
    TopLevelDestination.CONTACTS -> Icons.Outlined.Contacts
    TopLevelDestination.CALENDAR -> Icons.Outlined.CalendarMonth
    TopLevelDestination.NOTES -> Icons.Outlined.NoteAlt
}

@Composable
fun GlassBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    barModifier: Modifier = Modifier,
) {
    val plateShape = RoundedCornerShape(LiquidGlassTokens.DefaultCorner)
    Box(
        modifier = barModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Single tight glass plate wrapping chips only (no full-width underlay).
        LiquidGlassPlate(shape = plateShape) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { dest ->
                    GlassTabChip(
                        label = dest.label,
                        icon = tabIcon(dest),
                        selected = dest == current,
                        onClick = { onSelect(dest) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val tint = if (selected) CallMemoBlue else CallMemoMuted
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 64.dp, max = 74.dp)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = CallMemoBlue.copy(alpha = 0.22f)),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun InfoCircleButton(
    onClick: () -> Unit,
    contentDescription: String = "Подробнее",
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(1.dp, CallMemoMuted.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = contentDescription,
                tint = CallMemoMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Compact sticky player (~half previous height). */
@Composable
fun StickyPlayerBar(
    state: PlaybackUiState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit,
    barModifier: Modifier = Modifier,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(18.dp)
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = barModifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val hidden = abs(offsetX.value) / widthPx
                                if (hidden >= 0.5f) {
                                    val target = if (offsetX.value >= 0f) widthPx else -widthPx
                                    offsetX.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                                    onDismiss()
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                    )
                }
                .background(CallMemoInk, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (state.playing) "Пауза" else "Играть",
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDetail),
            ) {
                Text(
                    text = state.contactName.ifBlank { state.number },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WaveformSeek(
                    progress = progress,
                    amplitudes = state.amplitudes,
                    filledColor = CallMemoBlue,
                    emptyColor = Color.White.copy(alpha = 0.25f),
                    barHeight = 22.dp,
                    onSeekFraction = { fraction -> onSeek((fraction * duration).toLong()) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = TimeFormat.duration(state.positionMs / 1000),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = TimeFormat.duration(state.durationMs / 1000),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            TextButton(onClick = onCycleSpeed, modifier = Modifier.padding(start = 2.dp)) {
                Text(
                    text = speedLabel(state.speed),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun WaveformSeek(
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    amplitudes: List<Float> = emptyList(),
    filledColor: Color = CallMemoBlue,
    emptyColor: Color = CallMemoBlue.copy(alpha = 0.25f),
    barHeight: androidx.compose.ui.unit.Dp = 36.dp,
    barModifier: Modifier = Modifier,
) {
    val fallbackBars = remember {
        listOf(
            0.35f, 0.55f, 0.4f, 0.75f, 0.5f, 0.9f, 0.45f, 0.7f, 0.6f, 0.85f,
            0.4f, 0.65f, 0.95f, 0.5f, 0.72f, 0.38f, 0.8f, 0.55f, 0.68f, 0.42f,
            0.77f, 0.5f, 0.62f, 0.88f, 0.47f, 0.7f, 0.58f, 0.82f, 0.44f, 0.66f,
        )
    }
    val bars = amplitudes.ifEmpty { fallbackBars }
    Box(
        modifier = barModifier
            .fillMaxWidth()
            .height(barHeight)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onSeekFraction((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEachIndexed { index, amp ->
                val filled = index <= (progress * (bars.lastIndex.coerceAtLeast(1))).roundToInt()
                val h = (barHeight.value * (0.25f + amp * 0.75f)).dp
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(h)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (filled) filledColor else emptyColor),
                )
            }
        }
    }
}

private fun speedLabel(speed: Float): String = when (speed) {
    1.3f -> "x1.3"
    1.5f -> "x1.5"
    2f -> "x2"
    else -> "x1"
}

@Composable
fun SectionCard(
    cardModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = cardModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content,
    )
}

/**
 * Shared FAB bottom inset above glass tabs (aligned across tabs).
 * Slightly below the original ~96.dp floating position — not down next to the menu.
 */
val ChromeFabBottomPadding = 90.dp
