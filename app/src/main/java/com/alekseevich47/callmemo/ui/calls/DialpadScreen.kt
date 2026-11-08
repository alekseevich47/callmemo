package com.alekseevich47.callmemo.ui.calls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val DismissThreshold = 0.30f
private val KeyRowHeight = 52.dp
private val KeyHitSize = 56.dp

@Composable
fun DialpadSheet(
    expanded: Boolean,
    bottomContentPadding: PaddingValues,
    initialNumber: String = "",
    onDismissFinished: () -> Unit,
    onCall: (String) -> Unit,
    sheetModifier: Modifier = Modifier,
) {
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotEmpty()) number = initialNumber
    }

    val density = LocalDensity.current
    val offsetY = remember { Animatable(10_000f) }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = sheetModifier.fillMaxWidth(),
    ) {
        // Tall enough that white fills from bottom under the tabs (no call-list peek).
        val minSheetPx = with(density) { 480.dp.toPx() }
        val parentH = constraints.maxHeight
            .takeIf { it > 0 && it < Int.MAX_VALUE / 4 }
            ?.toFloat()
            ?: with(density) { 720.dp.toPx() }
        val maxAllowedPx = parentH * 0.78f
        val targetPx = parentH * 0.64f
        val sheetHeightPx = max(minSheetPx, targetPx).coerceAtMost(maxAllowedPx)
        val sheetHeight = with(density) { sheetHeightPx.toDp() }

        LaunchedEffect(sheetHeightPx) {
            if (dismissing || entered) return@LaunchedEffect
            offsetY.snapTo(sheetHeightPx)
            offsetY.animateTo(
                0f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            )
            entered = true
        }

        // Collapse on external signal even mid-enter (tab return + scroll race).
        LaunchedEffect(expanded) {
            if (expanded || dismissing) return@LaunchedEffect
            dismissing = true
            offsetY.animateTo(sheetHeightPx, tween(durationMillis = 220))
            onDismissFinished()
        }

        fun dismissSheet() {
            if (dismissing) return
            dismissing = true
            scope.launch {
                offsetY.animateTo(sheetHeightPx, tween(durationMillis = 220))
                onDismissFinished()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                // White sheet reaches the physical bottom; insets only shift controls.
                .navigationBarsPadding()
                .padding(bottomContentPadding)
                .padding(horizontal = 12.dp)
                .pointerInput(sheetHeightPx, dismissing) {
                    if (dismissing) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val hidden = offsetY.value / sheetHeightPx
                                if (hidden >= DismissThreshold) {
                                    dismissing = true
                                    offsetY.animateTo(sheetHeightPx, tween(durationMillis = 200))
                                    onDismissFinished()
                                } else {
                                    offsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            }
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetY.snapTo((offsetY.value + amount).coerceAtLeast(0f))
                            }
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
                    .clickable(enabled = !dismissing, onClick = { dismissSheet() }),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CallMemoMuted.copy(alpha = 0.45f)),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                    textAlign = TextAlign.Center,
                    color = CallMemoInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (number.isNotEmpty()) {
                    IconButton(
                        onClick = { number = number.dropLast(1) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "Стереть",
                            tint = CallMemoMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#"),
            )
            // Fixed row heights (not weight) so the call FAB sits above the glass tabs.
            keys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KeyRowHeight),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(KeyHitSize)
                                .clip(CircleShape)
                                .clickable(enabled = !dismissing) { number += key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = key,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                                color = CallMemoInk,
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                FloatingActionButton(
                    onClick = { if (number.isNotBlank()) onCall(number) },
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.28f),
                            spotColor = Color.Black.copy(alpha = 0.36f),
                        ),
                    containerColor = CallMemoBlue,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = "Позвонить")
                }
            }
            Spacer(Modifier.weight(1f, fill = true))
        }
    }
}
