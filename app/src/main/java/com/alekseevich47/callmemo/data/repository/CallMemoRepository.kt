package com.alekseevich47.callmemo.data.repository

import com.alekseevich47.callmemo.data.SeedData
import com.alekseevich47.callmemo.data.local.CalendarItemEntity
import com.alekseevich47.callmemo.data.local.CallLogEntity
import com.alekseevich47.callmemo.data.local.CallMemoDatabase
import com.alekseevich47.callmemo.data.local.NoteEntity
import com.alekseevich47.callmemo.data.local.toDomain
import com.alekseevich47.callmemo.data.secure.SecureSettingsStore
import com.alekseevich47.callmemo.domain.model.AppSettings
import com.alekseevich47.callmemo.domain.model.CalendarItem
import com.alekseevich47.callmemo.domain.model.CalendarItemType
import com.alekseevich47.callmemo.domain.model.CallDetail
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.domain.model.ContactItem
import com.alekseevich47.callmemo.domain.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallMemoRepository @Inject constructor(
    private val db: CallMemoDatabase,
    private val settingsStore: SecureSettingsStore,
) {
    val settings: Flow<AppSettings> = settingsStore.settings

    suspend fun ensureSeeded() {
        if (db.callLogDao().count() == 0) {
            db.callLogDao().upsertAll(SeedData.calls())
        }
        if (db.contactDao().count() == 0) {
            db.contactDao().upsertAll(SeedData.contacts())
        }
        if (db.calendarDao().count() == 0) {
            db.calendarDao().upsertAll(SeedData.calendarItems())
        }
        if (db.noteDao().count() == 0) {
            db.noteDao().upsertAll(SeedData.notes())
        }
    }

    fun observeCalls(query: String): Flow<List<CallLogEntry>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) {
            db.callLogDao().observeAll()
        } else {
            db.callLogDao().search(trimmed)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeContacts(query: String): Flow<List<ContactItem>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) {
            db.contactDao().observeAll()
        } else {
            db.contactDao().search(trimmed)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeNotes(): Flow<List<NoteItem>> =
        db.noteDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCalendar(): Flow<List<CalendarItem>> =
        db.calendarDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCalendarDay(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<CalendarItem>> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return db.calendarDao().observeBetween(start, end).map { list -> list.map { it.toDomain(zoneId) } }
    }

    suspend fun callDetail(id: Long): CallDetail? {
        val entity = db.callLogDao().getById(id) ?: return null
        val history = db.callLogDao().historyForNumber(entity.number).map { it.toDomain() }
        return CallDetail(
            entry = entity.toDomain(),
            history = history,
            transcript = entity.transcript,
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

    suspend fun recordFinishedCall(
        contactName: String?,
        number: String,
        direction: CallDirection,
        durationSeconds: Long,
        transcript: String? = null,
    ) {
        db.callLogDao().upsertAll(
            listOf(
                CallLogEntity(
                    contactName = contactName,
                    number = number,
                    direction = direction.name,
                    startedAtEpochMs = Instant.now().minusSeconds(durationSeconds.coerceAtLeast(0)).toEpochMilli(),
                    durationSeconds = durationSeconds.coerceAtLeast(0),
                    transcript = transcript,
                    recordingUri = if (transcript != null) "demo://recorded" else null,
                ),
            ),
        )
    }
}
