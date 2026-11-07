package com.alekseevich47.callmemo.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alekseevich47.callmemo.ui.components.AvatarCircle
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoDanger
import com.alekseevich47.callmemo.ui.theme.CallMemoSuccess

@Composable
fun InCallRoute(
    name: String,
    number: String,
    onEnd: () -> Unit,
) {
    var streamOn by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1A2B), Color(0xFF1A2740), Color(0xFF0B1220)),
                ),
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AvatarCircle(label = name, color = CallMemoBlue)
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(number, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
            Text("Идёт вызов…", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InCallAction("Без звука", Icons.Outlined.MicOff)
                InCallAction("Клавиатура", Icons.Outlined.Dialpad)
                InCallAction("Динамик", Icons.Outlined.VolumeUp)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InCallAction("Удержание", Icons.Outlined.Pause)
                InCallAction("Добавить", Icons.Outlined.PersonAdd)
                InCallAction("Bluetooth", Icons.Outlined.Bluetooth)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { streamOn = !streamOn },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (streamOn) CallMemoSuccess else CallMemoBlue,
                ),
            ) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (streamOn) "Стрим включён" else "Транскрипция / Стрим")
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onEnd,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(CallMemoDanger),
            ) {
                Icon(Icons.Outlined.CallEnd, contentDescription = "Завершить", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun IncomingRoute(
    name: String,
    number: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101826), Color(0xFF18233A)),
                ),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Входящий вызов", color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp))
        AvatarCircle(label = name, color = CallMemoBlue)
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(number, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CallMemoDanger),
                ) {
                    Icon(Icons.Outlined.CallEnd, contentDescription = "Отклонить", tint = Color.White)
                }
                Text("Отклонить", color = Color.White.copy(alpha = 0.8f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onAccept,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CallMemoSuccess),
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = "Ответить", tint = Color.White)
                }
                Text("Ответить", color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onDecline) {
            Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            Text("Ответить сообщением", color = Color.White)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InCallAction(label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {},
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
    }
}
