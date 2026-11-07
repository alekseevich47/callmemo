package com.alekseevich47.callmemo.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCallLogDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun observeCalls(query: String = ""): Flow<List<CallLogEntry>> = callbackFlow {
        val emitNow = {
            trySend(if (hasPermission()) readCalls(query) else emptyList())
        }
        emitNow()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emitNow()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer,
            )
        }
        val ticker = launch {
            var hadPermission = hasPermission()
            while (isActive) {
                delay(1_500)
                val now = hasPermission()
                if (now != hadPermission) {
                    hadPermission = now
                    emitNow()
                } else if (now) {
                    emitNow()
                    break
                }
            }
        }
        awaitClose {
            ticker.cancel()
            runCatching {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
    }

    fun historyForNumber(number: String): List<CallLogEntry> {
        if (!hasPermission()) return emptyList()
        return readCalls(numberFilter = number)
    }

    fun getById(id: Long): CallLogEntry? {
        if (!hasPermission()) return null
        return readCalls(idFilter = id).firstOrNull()
    }

    private fun readCalls(
        query: String = "",
        numberFilter: String? = null,
        idFilter: Long? = null,
    ): List<CallLogEntry> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val selectionParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (idFilter != null) {
            selectionParts += "${CallLog.Calls._ID}=?"
            args += idFilter.toString()
        }
        if (!numberFilter.isNullOrBlank()) {
            selectionParts += "${CallLog.Calls.NUMBER}=?"
            args += numberFilter
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty() && numberFilter == null && idFilter == null) {
            selectionParts += "(${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?)"
            args += "%$trimmed%"
            args += "%$trimmed%"
        }
        val selection = selectionParts.joinToString(" AND ").ifBlank { null }
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            args.toTypedArray().ifEmpty { null },
            "${CallLog.Calls.DATE} DESC",
        ) ?: return emptyList()

        return cursor.use {
            val idIdx = it.getColumnIndexOrThrow(CallLog.Calls._ID)
            val nameIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            buildList {
                while (it.moveToNext() && size < 300) {
                    val number = it.getString(numberIdx).orEmpty()
                    if (number.isBlank()) continue
                    add(
                        CallLogEntry(
                            id = it.getLong(idIdx),
                            contactName = it.getString(nameIdx),
                            number = number,
                            direction = when (it.getInt(typeIdx)) {
                                CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                                CallLog.Calls.MISSED_TYPE,
                                CallLog.Calls.REJECTED_TYPE,
                                CallLog.Calls.BLOCKED_TYPE,
                                -> CallDirection.MISSED
                                else -> CallDirection.INCOMING
                            },
                            startedAt = Instant.ofEpochMilli(it.getLong(dateIdx)),
                            durationSeconds = it.getLong(durationIdx).coerceAtLeast(0),
                            transcriptPreview = null,
                            recordingUri = null,
                        ),
                    )
                }
            }
        }
    }
}
