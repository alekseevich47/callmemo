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
    // Start off-screen; enter animation snaps to measured height then slides up.
    val offsetY = remember { Animatable(10_000f) }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = sheetModifier.fillMaxWidth()) {
        // Slightly taller sheet so the call FAB clears the glass tabs without
        // lifting the whole sheet above the menu.
        val minSheetPx = with(density) { 460.dp.toPx() }
        val maxAllowedPx = constraints.maxHeight * 0.76f
        val targetPx = constraints.maxHeight * 0.62f
        val sheetHeightPx = max(minSheetPx, targetPx).coerceAtMost(maxAllowedPx)
        val sheetHeight = with(density) { sheetHeightPx.toDp() }

        // Enter from below once; never snap back to 0 after a dismiss animation.
        LaunchedEffect(sheetHeightPx) {
            if (dismissing || entered) return@LaunchedEffect
            offsetY.snapTo(sheetHeightPx)
            offsetY.animateTo(
                0f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
            )
            entered = true
        }

        // External collapse (tab scroll / section change) — animate out then notify.
        LaunchedEffect(expanded) {
            if (!expanded && entered && !dismissing) {
                dismissing = true
                offsetY.animateTo(sheetHeightPx, tween(durationMillis = 240))
                onDismissFinished()
            }
        }

        fun dismissSheet() {
            if (dismissing) return
            dismissing = true
            scope.launch {
                offsetY.animateTo(
                    sheetHeightPx,
                    tween(durationMillis = 220),
                )
                onDismissFinished()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
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
                                // Only drag down; pulling back up reduces offset (can cancel dismiss).
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
                    .padding(top = 10.dp, bottom = 6.dp)
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

            // Number centered over the keypad; backspace overlays on the right.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
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

            Spacer(Modifier.height(4.dp))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#"),
            )
            keys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !dismissing) { number += key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = key,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineMedium,
                                color = CallMemoInk,
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FloatingActionButton(
                    onClick = { if (number.isNotBlank()) onCall(number) },
                    modifier = Modifier.shadow(
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
        }
    }
}
