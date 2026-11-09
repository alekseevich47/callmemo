package com.alekseevich47.callmemo.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.alekseevich47.callmemo.domain.model.ContactItem
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemContactsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Resolve a display name for [rawNumber] via PhoneLookup.
     * Returns null when permission is missing, number is blank, or no contact matches.
     */
    fun lookupDisplayName(rawNumber: String): String? {
        if (!hasPermission()) return null
        val number = rawNumber.trim()
        if (number.isEmpty()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
            ?: fallbackLookupByDigits(number)
    }

    /** Slow path when PhoneLookup encoding fails on odd OEM formats. */
    private fun fallbackLookupByDigits(number: String): String? {
        val target = PhoneNumberUtils.digitsOnly(number)
        if (target.length < 7) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var scanned = 0
                while (cursor.moveToNext() && scanned < 2_000) {
                    scanned++
                    val candidate = cursor.getString(numberIdx).orEmpty()
                    if (PhoneNumberUtils.sameSubscriber(candidate, number)) {
                        return@use cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                    }
                }
                null
            }
        }.getOrNull()
    }

    fun observeContacts(query: String = ""): Flow<List<ContactItem>> = callbackFlow {
        val emitNow = {
            trySend(if (hasPermission()) readContacts(query) else emptyList())
        }
        emitNow()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emitNow()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
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

    private fun readContacts(query: String): List<ContactItem> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val trimmed = query.trim()
        val selection = if (trimmed.isEmpty()) {
            null
        } else {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        }
        val args = if (trimmed.isEmpty()) null else arrayOf("%$trimmed%", "%$trimmed%")
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
        ) ?: return emptyList()

        return cursor.use {
            val idIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)
            val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            buildList {
                while (it.moveToNext() && size < 500) {
                    val name = it.getString(nameIdx).orEmpty().ifBlank { "Без имени" }
                    val number = it.getString(numberIdx).orEmpty()
                    if (number.isBlank()) continue
                    add(
                        ContactItem(
                            id = it.getLong(idIdx),
                            name = name,
                            number = number,
                        ),
                    )
                }
            }
        }
    }
}
