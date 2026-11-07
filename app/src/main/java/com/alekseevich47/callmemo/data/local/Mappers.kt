package com.alekseevich47.callmemo.data.local

import com.alekseevich47.callmemo.domain.model.CalendarItem
import com.alekseevich47.callmemo.domain.model.CalendarItemType
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import com.alekseevich47.callmemo.domain.model.ContactItem
import com.alekseevich47.callmemo.domain.model.NoteItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun CallLogEntity.toDomain(): CallLogEntry = CallLogEntry(
    id = id,
    contactName = contactName,
    number = number,
    direction = runCatching { CallDirection.valueOf(direction) }.getOrDefault(CallDirection.INCOMING),
    startedAt = Instant.ofEpochMilli(startedAtEpochMs),
    durationSeconds = durationSeconds,
    transcriptPreview = transcript?.take(120),
    recordingUri = recordingUri,
)

fun ContactEntity.toDomain(): ContactItem = ContactItem(
    id = id,
    name = name,
    number = number,
)

fun CalendarItemEntity.toDomain(zoneId: ZoneId = ZoneId.systemDefault()): CalendarItem = CalendarItem(
    id = id,
    title = title,
    notes = notes,
    startsAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startsAtEpochMs), zoneId),
    type = runCatching { CalendarItemType.valueOf(type) }.getOrDefault(CalendarItemType.EVENT),
    remindMinutesBefore = remindMinutesBefore,
)

fun NoteEntity.toDomain(): NoteItem = NoteItem(
    id = id,
    title = title,
    body = body,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    relatedNumber = relatedNumber,
)
