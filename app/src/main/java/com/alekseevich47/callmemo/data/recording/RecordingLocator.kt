package com.alekseevich47.callmemo.data.recording

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a call-recording file near a given number + timestamp from MediaStore
 * and known OEM folders.
 *
 * Matching is strict: random music / WhatsApp voice notes in the same time window
 * must NOT be treated as call recordings (score threshold + path/name heuristics).
 */
@Singleton
class RecordingLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun findRecording(number: String, startedAtEpochMs: Long, durationSeconds: Long): String? {
        if (startedAtEpochMs <= 0L) return null
        val windowStart = startedAtEpochMs - 180_000L
        val windowEnd = startedAtEpochMs + (durationSeconds.coerceAtLeast(0) * 1000L) + 420_000L
        val digits = PhoneNumberUtils.digitsOnly(number)
        return findInMediaStore(digits, windowStart, windowEnd)
            ?: findInOemFolders(digits, windowStart, windowEnd)
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun findInMediaStore(digits: String, windowStart: Long, windowEnd: Long): String? {
        if (!hasAudioPermission()) return null
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
        val selection = "(${MediaStore.Audio.Media.DATE_ADDED} BETWEEN ? AND ?) OR " +
            "(${MediaStore.Audio.Media.DATE_MODIFIED} BETWEEN ? AND ?)"
        val startSec = (windowStart / 1000L).toString()
        val endSec = (windowEnd / 1000L).toString()
        val args = arrayOf(startSec, endSec, startSec, endSec)
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )
        }.getOrNull() ?: return null

        return cursor.use {
            val idIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathIdx = if (Build.VERSION.SDK_INT >= 29) {
                it.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }
            var bestUri: Uri? = null
            var bestScore = Int.MIN_VALUE
            while (it.moveToNext()) {
                val name = it.getString(nameIdx).orEmpty()
                val relativePath = if (pathIdx >= 0) it.getString(pathIdx).orEmpty() else ""
                val score = scoreCandidate(name, relativePath, digits)
                if (score > bestScore) {
                    bestScore = score
                    bestUri = ContentUris.withAppendedId(collection, it.getLong(idIdx))
                }
            }
            if (bestScore >= MIN_ACCEPT_SCORE && bestUri != null) bestUri.toString() else null
        }
    }

    private fun findInOemFolders(digits: String, windowStart: Long, windowEnd: Long): String? {
        if (!hasAudioPermission()) return null
        val root = Environment.getExternalStorageDirectory() ?: return null
        val candidates = mutableListOf<Pair<File, Int>>()
        OemRecordingPaths.pathsForDevice().forEach { relative ->
            val dir = File(root, relative)
            if (!dir.isDirectory) return@forEach
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
                ?.filter { it.lastModified() in windowStart..windowEnd }
                ?.forEach { file ->
                    val score = scoreCandidate(file.name, relative, digits)
                    if (score >= MIN_ACCEPT_SCORE) {
                        candidates += file to score
                    }
                }
        }
        return candidates
            .maxByOrNull { it.second }
            ?.first
            ?.let { Uri.fromFile(it).toString() }
    }

    companion object {
        private val AUDIO_EXT = setOf("m4a", "mp3", "wav", "amr", "aac", "3gp", "ogg", "flac")

        /** Require call-like name/path and/or phone digits — never accept random music (score -1/0). */
        const val MIN_ACCEPT_SCORE = 2

        private val CALL_NAME_HINTS = listOf(
            "call", "record", "rec", "phone", "звук", "вызов", "звон", "телефон",
        )
        private val CALL_PATH_HINTS = listOf(
            "call", "phonerecord", "phonerecorod", "recordings", "call_rec", "callrecord",
            "sound_recorder", "voicerecorder",
        )

        fun scoreCandidate(name: String, relativePath: String, digits: String): Int {
            val lowerName = name.lowercase()
            val lowerPath = relativePath.lowercase().replace('\\', '/')
            val nameDigits = lowerName.filter { it.isDigit() }
            var score = 0

            if (CALL_NAME_HINTS.any { lowerName.contains(it) }) score += 2
            if (CALL_PATH_HINTS.any { lowerPath.contains(it) }) score += 3

            when {
                digits.length >= 7 && nameDigits.contains(digits.takeLast(7)) -> score += 10
                digits.length >= 4 && nameDigits.contains(digits.takeLast(4)) -> score += 4
                digits.length >= 4 && nameDigits.isNotEmpty() -> score -= 2
            }

            // Reject chat/voice apps & ringtones. Do NOT blanket-penalize Music/ —
            // Tecno/Transsion stores call recordings under Music/PhoneRecord(o).
            val callPath = CALL_PATH_HINTS.any { lowerPath.contains(it) }
            if (!callPath &&
                listOf("whatsapp", "telegram", "ringtone", "notification", "alarm", "music/")
                    .any { lowerPath.contains(it) || lowerName.contains(it) }
            ) {
                score -= 8
            }
            return score
        }
    }
}
