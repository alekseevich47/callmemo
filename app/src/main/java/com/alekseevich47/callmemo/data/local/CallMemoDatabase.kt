package com.alekseevich47.callmemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CallLogEntity::class,
        ContactEntity::class,
        CalendarItemEntity::class,
        NoteEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CallMemoDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
    abstract fun contactDao(): ContactDao
    abstract fun calendarDao(): CalendarDao
    abstract fun noteDao(): NoteDao
}
