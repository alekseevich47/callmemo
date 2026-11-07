package com.alekseevich47.callmemo.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alekseevich47.callmemo.data.repository.CallMemoRepository
import com.alekseevich47.callmemo.domain.model.CalendarItem
import com.alekseevich47.callmemo.domain.model.CalendarItemType
import com.alekseevich47.callmemo.ui.components.ScreenHeader
import com.alekseevich47.callmemo.ui.components.SectionCard
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CallMemoRepository,
) : ViewModel() {
    val items: StateFlow<List<CalendarItem>> = repository.observeCalendar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val markedDates: StateFlow<Set<LocalDate>> = items
        .map { list -> list.map { it.startsAt.toLocalDate() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addEvent(title: String, notes: String, day: LocalDate) {
        viewModelScope.launch {
            repository.addCalendarItem(
                title = title,
                notes = notes.ifBlank { null },
                startsAt = LocalDateTime.of(day, LocalTime.of(12, 0)),
                type = CalendarItemType.EVENT,
                remindMinutesBefore = 30,
            )
        }
    }
}

@Composable
fun CalendarRoute(
    onOpenSettings: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val marked by viewModel.markedDates.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val yearMonth = YearMonth.parse(month)
    val selected = LocalDate.parse(selectedDay)
    val dayItems = items.filter { it.startsAt.toLocalDate() == selected }
    var showDialog by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventNotes by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Календарь", onSettings = onOpenSettings)
            MonthHeader(
                yearMonth = yearMonth,
                onPrev = { month = yearMonth.minusMonths(1).toString() },
                onNext = { month = yearMonth.plusMonths(1).toString() },
            )
            MonthGrid(
                yearMonth = yearMonth,
                selected = selected,
                marked = marked,
                onSelect = { selectedDay = it.toString() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = dayTitle(selected),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = CallMemoInk,
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dayItems.isEmpty()) {
                    item {
                        SectionCard {
                            Text("На этот день пока ничего нет", color = CallMemoMuted)
                        }
                    }
                } else {
                    items(dayItems, key = { it.id }) { item ->
                        DayEventCard(item)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                eventTitle = ""
                eventNotes = ""
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 96.dp),
            shape = CircleShape,
            containerColor = CallMemoBlue,
            contentColor = Color.White,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Добавить")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Событие на ${dayTitle(selected)}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = eventNotes,
                        onValueChange = { eventNotes = it },
                        label = { Text("Заметка") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addEvent(eventTitle, eventNotes, selected)
                        showDialog = false
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Пред. месяц")
        }
        Text(
            text = TimeFormat.monthTitle(yearMonth),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "След. месяц")
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selected: LocalDate,
    marked: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = Locale("ru")
    val days = buildMonthCells(yearMonth)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
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
        Spacer(Modifier.height(8.dp))
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = date?.month == yearMonth.month
                    val isSelected = date == selected
                    val hasMark = date != null && date in marked
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) CallMemoBlue else Color.Transparent)
                            .then(
                                if (date != null && date == LocalDate.now() && !isSelected) {
                                    Modifier.border(1.dp, CallMemoBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = date != null) { date?.let(onSelect) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date?.dayOfMonth?.toString().orEmpty(),
                                color = when {
                                    !inMonth -> CallMemoMuted.copy(alpha = 0.35f)
                                    isSelected -> Color.White
                                    else -> CallMemoInk
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (hasMark) {
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else CallMemoBlue),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventCard(item: CalendarItem) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.startsAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = CallMemoBlue,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                item.notes?.let {
                    Text(it, color = CallMemoMuted, style = MaterialTheme.typography.bodyMedium)
                }
                if (item.remindMinutesBefore != null) {
                    Text(
                        "Напомнить за ${item.remindMinutesBefore} мин",
                        color = CallMemoMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (item.type == CalendarItemType.REMINDER || item.remindMinutesBefore != null) {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = CallMemoBlue)
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

private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
    }
}
