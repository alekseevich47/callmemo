package com.alekseevich47.callmemo.data.repository

import com.alekseevich47.callmemo.data.local.CalendarItemEntity
import com.alekseevich47.callmemo.data.local.CallMemoDatabase
import com.alekseevich47.callmemo.data.local.NoteEntity
import com.alekseevich47.callmemo.data.local.toDomain
import com.alekseevich47.callmemo.data.recording.RecordingLocator
import com.alekseevich47.callmemo.data.secure.SecureSettingsStore
import com.alekseevich47.callmemo.data.system.SystemCallLogDataSource
import com.alekseevich47.callmemo.data.system.SystemContactsDataSource
import com.alekseevich47.callmemo.domain.model.AppSettings
import com.alekseevich47.callmemo.domain.model.CalendarItem
import com.alekseevich47.callmemo.domain.model.CalendarItemType
import com.alekseevich47.callmemo.domain.model.CallDetail
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.domain.model.ContactItem
import com.alekseevich47.callmemo.domain.model.NoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallMemoRepository @Inject constructor(
    private val db: CallMemoDatabase,
    private val settingsStore: SecureSettingsStore,
    private val systemCallLog: SystemCallLogDataSource,
    private val systemContacts: SystemContactsDataSource,
    private val recordingLocator: RecordingLocator,
) {
    val settings: Flow<AppSettings> = settingsStore.settings

    fun hasCallLogPermission(): Boolean = systemCallLog.hasPermission()
    fun hasContactsPermission(): Boolean = systemContacts.hasPermission()

    fun observeCalls(
        query: String,
        fromEpochMs: Long? = null,
        toEpochMs: Long? = null,
    ): Flow<List<CallLogEntry>> = systemCallLog.observeCalls(query, fromEpochMs, toEpochMs)

    fun observeContacts(query: String): Flow<List<ContactItem>> = systemContacts.observeContacts(query)

    fun observeNotes(): Flow<List<NoteItem>> =
        db.noteDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCalendar(): Flow<List<CalendarItem>> =
        db.calendarDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCalendarDay(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<CalendarItem>> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return db.calendarDao().observeBetween(start, end).map { list -> list.map { it.toDomain(zoneId) } }
    }

    suspend fun callDetail(id: Long): CallDetail? = withContext(Dispatchers.IO) {
        // Fast path like the stock dialer: CallLog only. Recording resolve is lazy on play.
        val raw = systemCallLog.getById(id) ?: return@withContext null
        val history = systemCallLog.historyForNumber(raw.number)
        CallDetail(
            entry = raw,
            history = history,
            transcript = raw.transcriptPreview,
        )
    }

    suspend fun resolveRecording(entry: CallLogEntry): String? = withContext(Dispatchers.IO) {
        entry.recordingUri
            ?: recordingLocator.findRecording(
                number = entry.number,
                startedAtEpochMs = entry.startedAt.toEpochMilli(),
                durationSeconds = entry.durationSeconds,
            )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    fun maskedApiKey(): String = settingsStore.maskedApiKey()

    suspend fun addNote(title: String, body: String, relatedNumber: String? = null) {
        db.noteDao().upsertAll(
            listOf(
                NoteEntity(
                    title = title.trim().ifBlank { "Без названия" },
                    body = body.trim(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    relatedNumber = relatedNumber,
                ),
            ),
        )
    }

    suspend fun addCalendarItem(
        title: String,
        notes: String?,
        startsAt: LocalDateTime,
        type: CalendarItemType,
        remindMinutesBefore: Int? = 30,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        db.calendarDao().upsertAll(
            listOf(
                CalendarItemEntity(
                    title = title.trim().ifBlank { "Событие" },
                    notes = notes?.trim()?.ifBlank { null },
                    startsAtEpochMs = startsAt.atZone(zoneId).toInstant().toEpochMilli(),
                    type = type.name,
                    remindMinutesBefore = remindMinutesBefore,
                ),
            ),
        )
    }
}
