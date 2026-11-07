package com.alekseevich47.callmemo.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alekseevich47.callmemo.domain.model.AppSettings
import com.alekseevich47.callmemo.domain.model.RecordingSource
import com.alekseevich47.callmemo.domain.model.SttModelId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createPrefs(context)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun createPrefs(context: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Fallback for unit tests / environments without StrongBox — still private mode.
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    private fun read(): AppSettings = AppSettings(
        deepSeekApiKey = prefs.getString(KEY_API, "").orEmpty(),
        sttModel = prefs.getString(KEY_MODEL, SttModelId.GIGA_AM.name)
            ?.let { runCatching { SttModelId.valueOf(it) }.getOrDefault(SttModelId.GIGA_AM) }
            ?: SttModelId.GIGA_AM,
        streamModeEnabled = prefs.getBoolean(KEY_STREAM, false),
        autoTranscribe = prefs.getBoolean(KEY_AUTO, true),
        recordingSource = prefs.getString(KEY_SOURCE, RecordingSource.OEM_FOLDER.name)
            ?.let { runCatching { RecordingSource.valueOf(it) }.getOrDefault(RecordingSource.OEM_FOLDER) }
            ?: RecordingSource.OEM_FOLDER,
        syncSystemCalendar = prefs.getBoolean(KEY_SYNC_CAL, false),
        customRecordingFolderUri = prefs.getString(KEY_FOLDER, null),
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_API, next.deepSeekApiKey)
            .putString(KEY_MODEL, next.sttModel.name)
            .putBoolean(KEY_STREAM, next.streamModeEnabled)
            .putBoolean(KEY_AUTO, next.autoTranscribe)
            .putString(KEY_SOURCE, next.recordingSource.name)
            .putBoolean(KEY_SYNC_CAL, next.syncSystemCalendar)
            .putString(KEY_FOLDER, next.customRecordingFolderUri)
            .apply()
        _settings.value = next
    }

    /** Never log the raw key; UI masking uses this helper. */
    fun maskedApiKey(raw: String = _settings.value.deepSeekApiKey): String {
        if (raw.isBlank()) return ""
        if (raw.length <= 4) return "••••"
        return raw.take(3) + "••••••••" + raw.takeLast(2)
    }

    companion object {
        private const val PREFS_NAME = "callmemo_secure_prefs"
        private const val PREFS_NAME_FALLBACK = "callmemo_secure_prefs_fallback"
        private const val KEY_API = "deepseek_api_key"
        private const val KEY_MODEL = "stt_model"
        private const val KEY_STREAM = "stream_mode"
        private const val KEY_AUTO = "auto_transcribe"
        private const val KEY_SOURCE = "recording_source"
        private const val KEY_SYNC_CAL = "sync_system_calendar"
        private const val KEY_FOLDER = "custom_recording_folder"
    }
}
