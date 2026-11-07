package com.alekseevich47.callmemo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alekseevich47.callmemo.data.recording.OemRecordingPaths
import com.alekseevich47.callmemo.data.repository.CallMemoRepository
import com.alekseevich47.callmemo.domain.model.AppSettings
import com.alekseevich47.callmemo.domain.model.RecordingSource
import com.alekseevich47.callmemo.domain.model.SttModelId
import com.alekseevich47.callmemo.ui.components.SectionCard
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: CallMemoRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onTestCall: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var revealKey by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }

        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Демо", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Проверь входящий → активный вызов, стрим и запись в журнал без реальной телефонии.",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onTestCall,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Тестовый звонок")
            }
        }

        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("DeepSeek API", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.deepSeekApiKey,
                onValueChange = { value ->
                    // Avoid logging; store only via encrypted prefs.
                    viewModel.update { it.copy(deepSeekApiKey = value.trim()) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API ключ") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            if (revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )
            Text(
                "Ключ хранится в EncryptedSharedPreferences и не попадает в бэкап.",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Модель расшифровки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it },
            ) {
                OutlinedTextField(
                    value = settings.sttModel.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    shape = RoundedCornerShape(14.dp),
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false },
                ) {
                    SttModelId.entries.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text("${model.label} · ~${model.downloadHintMb} МБ")
                            },
                            onClick = {
                                viewModel.update { it.copy(sttModel = model) }
                                modelExpanded = false
                            },
                        )
                    }
                }
            }
            Text(
                "Скачивание модели в фоне появится в следующем шаге.",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitch(
                title = "Стрим-режим",
                checked = settings.streamModeEnabled,
                onCheckedChange = { checked -> viewModel.update { it.copy(streamModeEnabled = checked) } },
            )
            SettingSwitch(
                title = "Авто-расшифровка",
                checked = settings.autoTranscribe,
                onCheckedChange = { checked -> viewModel.update { it.copy(autoTranscribe = checked) } },
            )
        }

        SectionCard(cardModifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Источник записи", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = sourceExpanded,
                onExpandedChange = { sourceExpanded = it },
            ) {
                OutlinedTextField(
                    value = sourceLabel(settings.recordingSource),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                    shape = RoundedCornerShape(14.dp),
                )
                ExposedDropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = { sourceExpanded = false },
                ) {
                    RecordingSource.entries.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(sourceLabel(source)) },
                            onClick = {
                                viewModel.update { it.copy(recordingSource = source) }
                                sourceExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                OemRecordingPaths.hintForDevice(),
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Папки: " + OemRecordingPaths.pathsForDevice().take(3).joinToString(),
                color = CallMemoMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitch(
                title = "Системный календарь",
                checked = settings.syncSystemCalendar,
                onCheckedChange = { checked -> viewModel.update { it.copy(syncSystemCalendar = checked) } },
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun sourceLabel(source: RecordingSource): String = when (source) {
    RecordingSource.OEM_FOLDER -> "Штатная запись (OEM)"
    RecordingSource.EMBEDDED_ADB -> "Встроенный ADB"
    RecordingSource.SHIZUKU -> "Shizuku"
    RecordingSource.DICTATION_ONLY -> "Только диктовка / импорт"
}
