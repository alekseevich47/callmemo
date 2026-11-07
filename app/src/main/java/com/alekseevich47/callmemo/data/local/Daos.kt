package com.alekseevich47.callmemo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startedAtEpochMs DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query(
        """
        SELECT * FROM call_logs
        WHERE contactName LIKE '%' || :query || '%' OR number LIKE '%' || :query || '%'
        ORDER BY startedAtEpochMs DESC
        """,
    )
    fun search(query: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE number = :number ORDER BY startedAtEpochMs DESC")
    suspend fun historyForNumber(number: String): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CallLogEntity>)

    @Query("SELECT COUNT(*) FROM call_logs")
    suspend fun count(): Int
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query(
        """
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%' OR number LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun search(query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContactEntity>)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_items ORDER BY startsAtEpochMs ASC")
    fun observeAll(): Flow<List<CalendarItemEntity>>

    @Query(
        """
        SELECT * FROM calendar_items
        WHERE startsAtEpochMs >= :fromInclusive AND startsAtEpochMs < :toExclusive
        ORDER BY startsAtEpochMs ASC
        """,
    )
    fun observeBetween(fromInclusive: Long, toExclusive: Long): Flow<List<CalendarItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CalendarItemEntity>)

    @Query("SELECT COUNT(*) FROM calendar_items")
    suspend fun count(): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NoteEntity>)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
