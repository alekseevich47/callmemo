package com.alekseevich47.callmemo.ui.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoInk
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted

@Composable
fun DialpadRoute(
    onBack: () -> Unit,
    onCall: (String) -> Unit,
) {
    var number by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = number.ifBlank { " " },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
            textAlign = TextAlign.Center,
            color = CallMemoInk,
            maxLines = 1,
        )
        Spacer(Modifier.height(24.dp))
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#"),
        )
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Text(
                        text = key,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { number += key }
                            .padding(top = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        color = CallMemoInk,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(56.dp))
            FloatingActionButton(
                onClick = { if (number.isNotBlank()) onCall(number) },
                containerColor = CallMemoBlue,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Outlined.Call, contentDescription = "Позвонить")
            }
            IconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Стереть",
                    tint = CallMemoMuted,
                )
            }
        }
    }
}
