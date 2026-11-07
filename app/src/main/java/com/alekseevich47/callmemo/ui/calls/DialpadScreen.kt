package com.alekseevich47.callmemo.ui.calls

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import kotlin.math.max

/**
 * Opaque dialpad sheet (~58% screen). Bottom padding reserves chrome so keys sit above
 * floating tabs/player while the sheet fills behind them.
 */
@Composable
fun DialpadSheet(
    visible: Boolean,
    bottomContentPadding: PaddingValues,
    initialNumber: String = "",
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    sheetModifier: Modifier = Modifier,
) {
    if (!visible) return

    var number by rememberSaveable { mutableStateOf(initialNumber) }
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotEmpty()) number = initialNumber
    }

    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = sheetModifier.fillMaxWidth()) {
        val minSheetPx = with(density) { 420.dp.toPx() }
        val maxAllowedPx = constraints.maxHeight * 0.72f
        val targetPx = constraints.maxHeight * 0.58f
        val sheetHeightPx = max(minSheetPx, targetPx).coerceAtMost(maxAllowedPx)
        val sheetHeight = with(density) { sheetHeightPx.toDp() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .padding(bottomContentPadding)
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > with(density) { 72.dp.toPx() }) onDismiss()
                            dragY = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            if (amount > 0f) dragY += amount
                            else dragY = (dragY + amount).coerceAtLeast(0f)
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp)
                    .clickable(onClick = onDismiss),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = number.ifBlank { " " },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                    textAlign = TextAlign.Center,
                    color = CallMemoInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (number.isNotEmpty()) {
                    IconButton(
                        onClick = { number = number.dropLast(1) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "Стереть",
                            tint = CallMemoMuted,
                        )
                    }
                } else {
                    Spacer(Modifier.size(44.dp))
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
                                .clickable { number += key },
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
                    containerColor = CallMemoBlue,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = "Позвонить")
                }
            }
        }
    }
}
