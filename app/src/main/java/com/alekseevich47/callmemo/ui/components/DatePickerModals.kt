package com.alekseevich47.callmemo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Tennis-style date / range picker modal (membership DatePickerModal / DateRangeModal).
 * Days → months → years drill-down, swipe between periods, confirm CTA.
 */
enum class CalendarPickerMode { Single, Range }

private enum class CalendarView { Days, Months, Years }

data class DateRangeSelection(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

@Composable
fun CallMemoDatePickerModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    initialDate: LocalDate? = null,
    title: String = "Выбрать дату",
) {
    if (!visible) return
    var selected by remember(visible) { mutableStateOf(initialDate) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CalendarPickerCard(
            title = title,
            mode = CalendarPickerMode.Single,
            selectedStart = selected,
            selectedEnd = selected,
            onSelectSingle = { selected = it },
            onSelectRange = { _, _ -> },
            onDismiss = onDismiss,
            onConfirm = {
                val day = selected
                if (day != null) {
                    onConfirm(day)
                    onDismiss()
                }
            },
            confirmEnabled = selected != null,
            hint = selected?.let { formatLongRu(it) } ?: "Выберите дату",
        )
    }
}

@Composable
fun CallMemoDateRangePickerModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    initialStart: LocalDate? = null,
    initialEnd: LocalDate? = null,
    title: String = "Выбрать период",
) {
    if (!visible) return
    var start by remember(visible) { mutableStateOf(initialStart) }
    var end by remember(visible) { mutableStateOf(initialEnd ?: initialStart) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CalendarPickerCard(
            title = title,
            mode = CalendarPickerMode.Range,
            selectedStart = start,
            selectedEnd = end,
            onSelectSingle = {},
            onSelectRange = { s, e ->
                start = s
                end = e
            },
            onDismiss = onDismiss,
            onConfirm = {
                val s = start
                if (s != null) {
                    onConfirm(s, end ?: s)
                    onDismiss()
                }
            },
            confirmEnabled = start != null,
            hint = formatRangeHint(start, end),
        )
    }
}

@Composable
private fun CalendarPickerCard(
    title: String,
    mode: CalendarPickerMode,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onSelectSingle: (LocalDate) -> Unit,
    onSelectRange: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    hint: String,
) {
    var view by remember { mutableStateOf(CalendarView.Days) }
    var displayMonth by remember {
        mutableStateOf(YearMonth.from(selectedStart ?: LocalDate.now()))
    }
    LaunchedEffect(selectedStart) {
        selectedStart?.let { displayMonth = YearMonth.from(it) }
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CallMemoInk,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = CallMemoMuted)
                }
            }
            Spacer(Modifier.height(4.dp))
            TennisCalendarPicker(
                view = view,
                onViewChange = { view = it },
                displayMonth = displayMonth,
                onDisplayMonthChange = { displayMonth = it },
                mode = mode,
                selectedStart = selectedStart,
                selectedEnd = selectedEnd,
                onSelectSingle = onSelectSingle,
                onSelectRange = onSelectRange,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CallMemoBlue),
            ) {
                Text("Подтвердить", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TennisCalendarPicker(
    view: CalendarView,
    onViewChange: (CalendarView) -> Unit,
    displayMonth: YearMonth,
    onDisplayMonthChange: (YearMonth) -> Unit,
    mode: CalendarPickerMode,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onSelectSingle: (LocalDate) -> Unit,
    onSelectRange: (LocalDate?, LocalDate?) -> Unit,
) {
    val locale = Locale("ru")
    var dragAccum by remember { mutableFloatStateOf(0f) }

    fun goPrev() {
        onDisplayMonthChange(
            when (view) {
                CalendarView.Days -> displayMonth.minusMonths(1)
                CalendarView.Months -> displayMonth.minusYears(1)
                CalendarView.Years -> displayMonth.minusYears(12)
            },
        )
    }

    fun goNext() {
        onDisplayMonthChange(
            when (view) {
                CalendarView.Days -> displayMonth.plusMonths(1)
                CalendarView.Months -> displayMonth.plusYears(1)
                CalendarView.Years -> displayMonth.plusYears(12)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(view, displayMonth) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccum > 50f -> goPrev()
                            dragAccum < -50f -> goNext()
                        }
                        dragAccum = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragAccum += amount
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { goPrev() }) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Назад",
                    tint = CallMemoBlue,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (view) {
                    CalendarView.Days -> {
                        TextButton(onClick = { onViewChange(CalendarView.Months) }) {
                            Text(
                                text = displayMonth.month
                                    .getDisplayName(TextStyle.FULL_STANDALONE, locale)
                                    .replaceFirstChar { it.titlecase(locale) } +
                                    " ${displayMonth.year}",
                                color = CallMemoInk,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    CalendarView.Months -> {
                        TextButton(onClick = { onViewChange(CalendarView.Years) }) {
                            Text(
                                text = displayMonth.year.toString(),
                                color = CallMemoInk,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    CalendarView.Years -> {
                        val start = (displayMonth.year / 12) * 12
                        Text(
                            text = "$start–${start + 11}",
                            color = CallMemoInk,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            IconButton(onClick = { goNext() }) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Вперёд",
                    tint = CallMemoBlue,
                )
            }
        }

        AnimatedContent(
            targetState = view,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "calendar-view",
        ) { current ->
            when (current) {
                CalendarView.Days -> DaysGrid(
                    displayMonth = displayMonth,
                    selectedStart = selectedStart,
                    selectedEnd = selectedEnd ?: selectedStart,
                    onPick = { date ->
                        when (mode) {
                            CalendarPickerMode.Single -> onSelectSingle(date)
                            CalendarPickerMode.Range -> {
                                val s = selectedStart
                                val e = selectedEnd
                                if (s == null || e != null) {
                                    onSelectRange(date, null)
                                } else if (date.isBefore(s)) {
                                    onSelectRange(date, s)
                                } else {
                                    onSelectRange(s, date)
                                }
                            }
                        }
                    },
                )
                CalendarView.Months -> MonthsGrid(
                    year = displayMonth.year,
                    selected = selectedStart,
                    onPick = { month ->
                        onDisplayMonthChange(YearMonth.of(displayMonth.year, month))
                        onViewChange(CalendarView.Days)
                    },
                )
                CalendarView.Years -> YearsGrid(
                    anchorYear = displayMonth.year,
                    selected = selectedStart,
                    onPick = { year ->
                        onDisplayMonthChange(YearMonth.of(year, displayMonth.month))
                        onViewChange(CalendarView.Months)
                    },
                )
            }
        }
    }
}

@Composable
private fun DaysGrid(
    displayMonth: YearMonth,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onPick: (LocalDate) -> Unit,
) {
    val locale = Locale("ru")
    val today = LocalDate.now()
    val cells = remember(displayMonth) { buildMonthCells(displayMonth) }
    val rangeStart = selectedStart
    val rangeEnd = selectedEnd ?: selectedStart

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = CallMemoMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = date?.month == displayMonth.month
                    val isSelected = date != null && rangeStart != null && rangeEnd != null &&
                        !date.isBefore(rangeStart) && !date.isAfter(rangeEnd)
                    val isEndpoint = date != null && (date == rangeStart || date == rangeEnd)
                    val isToday = date == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isEndpoint -> CallMemoBlue
                                    isSelected -> CallMemoBlue.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                },
                            )
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.border(1.dp, CallMemoBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = date != null) { date?.let(onPick) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date?.dayOfMonth?.toString().orEmpty(),
                            color = when {
                                !inMonth -> CallMemoMuted.copy(alpha = 0.35f)
                                isEndpoint -> Color.White
                                isToday -> CallMemoBlue
                                else -> CallMemoInk
                            },
                            fontWeight = when {
                                isEndpoint || isToday -> FontWeight.Bold
                                else -> FontWeight.Medium
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MonthsGrid(
    year: Int,
    selected: LocalDate?,
    onPick: (Int) -> Unit,
) {
    val locale = Locale("ru")
    val now = LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0 until 4).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0 until 3).forEach { col ->
                    val month = row * 3 + col + 1
                    val isSelected = selected?.year == year && selected.monthValue == month
                    val isCurrent = now.year == year && now.monthValue == month
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CallMemoBlue else Color.Transparent)
                            .clickable { onPick(month) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = java.time.Month.of(month)
                                .getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                                .replaceFirstChar { it.titlecase(locale) },
                            color = when {
                                isSelected -> Color.White
                                isCurrent -> CallMemoBlue
                                else -> CallMemoInk
                            },
                            fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearsGrid(
    anchorYear: Int,
    selected: LocalDate?,
    onPick: (Int) -> Unit,
) {
    val start = (anchorYear / 12) * 12
    val years = (start until start + 12).toList()
    val nowYear = LocalDate.now().year
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        years.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { year ->
                    val isSelected = selected?.year == year
                    val isCurrent = year == nowYear
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CallMemoBlue else Color.Transparent)
                            .clickable { onPick(year) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = year.toString(),
                            color = when {
                                isSelected -> Color.White
                                isCurrent -> CallMemoBlue
                                else -> CallMemoInk
                            },
                            fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun buildMonthCells(yearMonth: YearMonth): List<LocalDate?> {
    val first = yearMonth.atDay(1)
    val shift = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val cells = MutableList<LocalDate?>(shift) { null }
    for (day in 1..daysInMonth) {
        cells += yearMonth.atDay(day)
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun formatLongRu(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))

private fun formatRangeHint(start: LocalDate?, end: LocalDate?): String {
    if (start == null) return "Выберите дату или период"
    if (end == null || end == start) return formatLongRu(start)
    return "${formatLongRu(start)} — ${formatLongRu(end)}"
}
