package com.alekseevich47.callmemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alekseevich47.callmemo.ui.navigation.TopLevelDestination
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoGlass
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.ui.theme.CallMemoSky

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
fun AvatarCircle(label: String, color: Color = CallMemoBlue) {
    Box(
        modifier = Modifier
            .size(48.dp)
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
    Row(
        modifier = barModifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(CallMemoGlass, Color.White.copy(alpha = 0.92f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { dest ->
            val selected = dest == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(dest) }
                    .background(if (selected) CallMemoSky else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = tabIcon(dest),
                    contentDescription = dest.label,
                    tint = if (selected) CallMemoBlue else CallMemoMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) CallMemoBlue else CallMemoMuted,
                )
            }
        }
    }
}

@Composable
fun StickyPlayerBar(
    title: String,
    subtitle: String,
    playing: Boolean,
    onToggle: () -> Unit,
    barModifier: Modifier = Modifier,
) {
    Row(
        modifier = barModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CallMemoInk)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Играть",
                tint = Color.White,
            )
        }
    }
}

@Composable
fun SectionCard(
    cardModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = cardModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        content()
    }
}
