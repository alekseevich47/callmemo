package com.alekseevich47.callmemo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
}

data class CallLogEntry(
    val id: Long,
    val contactName: String?,
    val number: String,
    val direction: CallDirection,
    val startedAt: Instant,
    val durationSeconds: Long,
    val transcriptPreview: String? = null,
    val recordingUri: String? = null,
)

data class ContactItem(
    val id: Long,
    val name: String,
    val number: String,
    val initial: String = name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
)

enum class CalendarItemType {
    EVENT,
    REMINDER,
    TASK,
}

data class CalendarItem(
    val id: Long,
    val title: String,
    val notes: String?,
    val startsAt: LocalDateTime,
    val type: CalendarItemType,
    val remindMinutesBefore: Int? = null,
)

data class NoteItem(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val relatedNumber: String? = null,
)

data class CallDetail(
    val entry: CallLogEntry,
    val history: List<CallLogEntry>,
    val transcript: String?,
)

enum class RecordingSource {
    OEM_FOLDER,
    EMBEDDED_ADB,
    SHIZUKU,
    DICTATION_ONLY,
}

enum class SttModelId(val label: String, val downloadHintMb: Int) {
    GIGA_AM("GigaAM (RU)", 180),
    VOSK_SMALL("Vosk Small RU", 50),
    WHISPER_SMALL("Whisper Small", 200),
    T_ONE_STREAM("T-one Streaming RU", 120),
}

data class AppSettings(
    val deepSeekApiKey: String = "",
    val sttModel: SttModelId = SttModelId.GIGA_AM,
    val streamModeEnabled: Boolean = false,
    val autoTranscribe: Boolean = true,
    val recordingSource: RecordingSource = RecordingSource.OEM_FOLDER,
    val syncSystemCalendar: Boolean = false,
    val customRecordingFolderUri: String? = null,
)

data class DayMarker(
    val date: LocalDate,
    val hasEvents: Boolean,
)
