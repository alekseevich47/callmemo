package com.alekseevich47.callmemo.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.components.CallMemoDateRangePickerModal
import com.alekseevich47.callmemo.ui.components.InfoCircleButton
import com.alekseevich47.callmemo.ui.components.ScreenHeader
import com.alekseevich47.callmemo.ui.components.SearchField
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoDanger
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.ui.theme.CallMemoSuccess
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.alekseevich47.callmemo.util.TimeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CallsRoute(
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCallNumber: (String?, String) -> Unit,
    listBottomPadding: Dp = 140.dp,
    hasCallLogPermission: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    onScrollCalls: (scrollingDown: Boolean) -> Unit = {},
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    CallsScreen(
        calls = calls,
        query = query,
        filter = filter,
        onQueryChange = viewModel::onQueryChange,
        onDateFilter = viewModel::setDateFilter,
        onClearDateFilter = viewModel::clearDateFilter,
        onOpenSettings = onOpenSettings,
        onOpenDetail = onOpenDetail,
        onCallNumber = onCallNumber,
        listBottomPadding = listBottomPadding,
        hasCallLogPermission = hasCallLogPermission,
        onRequestPermissions = onRequestPermissions,
        onScrollCalls = onScrollCalls,
    )
}

@Composable
fun CallsScreen(
    calls: List<CallLogEntry>,
    query: String,
    filter: CallDateFilter,
    onQueryChange: (String) -> Unit,
    onDateFilter: (LocalDate?, LocalDate?) -> Unit,
    onClearDateFilter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCallNumber: (String?, String) -> Unit,
    listBottomPadding: Dp = 140.dp,
    hasCallLogPermission: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    onScrollCalls: (scrollingDown: Boolean) -> Unit = {},
) {
    var showRangePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset + 4)
            val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset - 4)
            when {
                scrollingDown && (index > 0 || offset > 16) -> onScrollCalls(true)
                scrollingUp -> onScrollCalls(false)
            }
            lastIndex = index
            lastOffset = offset
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Звонки", onSettings = onOpenSettings)
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Поиск по звонкам...",
            trailingIcon = {
                IconButton(onClick = { showRangePicker = true }) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = "Фильтр по дате",
                        tint = if (filter.active) CallMemoBlue else CallMemoMuted,
                    )
                }
            },
        )
        if (filter.active) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatFilter(filter),
                    color = CallMemoBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClearDateFilter) {
                    Icon(Icons.Outlined.Close, contentDescription = "Сбросить фильтр", tint = CallMemoMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            !hasCallLogPermission -> {
                PermissionEmpty(
                    text = "Нужен доступ к журналу звонков и контактам",
                    onRequest = onRequestPermissions,
                )
            }
            calls.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = listBottomPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filter.active) "Нет звонков за выбранный период" else "Пока нет звонков",
                        color = CallMemoMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(calls, key = { it.id }) { entry ->
                        CallRow(
                            entry = entry,
                            onCall = { onCallNumber(entry.contactName, entry.number) },
                            onOpenDetail = { onOpenDetail(entry.id) },
                        )
                    }
                }
            }
        }
    }

    CallMemoDateRangePickerModal(
        visible = showRangePicker,
        onDismiss = { showRangePicker = false },
        initialStart = filter.start,
        initialEnd = filter.end,
        onConfirm = { start, end -> onDateFilter(start, end) },
    )
}

private fun formatFilter(filter: CallDateFilter): String {
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    val start = filter.start ?: return ""
    val end = filter.end ?: start
    return if (start == end) {
        "Звонки за ${start.format(fmt)}"
    } else {
        "Звонки: ${start.format(fmt)} – ${end.format(fmt)}"
    }
}

@Composable
private fun PermissionEmpty(text: String, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = CallMemoMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text("Разрешить")
        }
    }
}

@Composable
private fun CallRow(
    entry: CallLogEntry,
    onCall: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val title = PhoneNumberUtils.displayOrUnknown(entry.contactName, entry.number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable(onClick = onCall)) {
            AvatarCircle(label = title)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clickable(onClick = onCall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectionIcon(entry.direction)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CallMemoMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = TimeFormat.callWhen(entry.startedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = CallMemoMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = 2.dp),
        )
        InfoCircleButton(onClick = onOpenDetail)
    }
}

@Composable
private fun DirectionIcon(direction: CallDirection) {
    val (icon, tint) = when (direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Outlined.CallMade to CallMemoBlue
        CallDirection.INCOMING -> Icons.AutoMirrored.Outlined.CallReceived to CallMemoSuccess
        CallDirection.MISSED -> Icons.AutoMirrored.Outlined.CallMissed to CallMemoDanger
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
}
