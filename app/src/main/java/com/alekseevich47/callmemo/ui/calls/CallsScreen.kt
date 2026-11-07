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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.components.InfoCircleButton
import com.alekseevich47.callmemo.ui.components.ScreenHeader
import com.alekseevich47.callmemo.ui.components.SearchField
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoDanger
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.ui.theme.CallMemoSuccess
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.alekseevich47.callmemo.util.TimeFormat

@Composable
fun CallsRoute(
    onOpenSettings: () -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCallNumber: (String?, String) -> Unit,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    CallsScreen(
        calls = calls,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onOpenSettings = onOpenSettings,
        onOpenDialpad = onOpenDialpad,
        onOpenDetail = onOpenDetail,
        onCallNumber = onCallNumber,
    )
}

@Composable
fun CallsScreen(
    calls: List<CallLogEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCallNumber: (String?, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Звонки", onSettings = onOpenSettings)
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Поиск по звонкам...",
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
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
        FloatingActionButton(
            onClick = onOpenDialpad,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 96.dp),
            shape = CircleShape,
            containerColor = CallMemoBlue,
            contentColor = Color.White,
        ) {
            Icon(Icons.Outlined.Dialpad, contentDescription = "Набор номера")
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
