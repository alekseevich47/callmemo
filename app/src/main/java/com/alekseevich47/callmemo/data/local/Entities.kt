package com.alekseevich47.callmemo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactName: String?,
    val number: String,
    val direction: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Long,
    val transcript: String?,
    val recordingUri: String?,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String,
)

@Entity(tableName = "calendar_items")
data class CalendarItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String?,
    val startsAtEpochMs: Long,
    val type: String,
    val remindMinutesBefore: Int?,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAtEpochMs: Long,
    val relatedNumber: String?,
)
