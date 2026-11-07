package com.alekseevich47.callmemo.data.recording

import android.Manifest
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
 */
@Singleton
class RecordingLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun findRecording(number: String, startedAtEpochMs: Long, durationSeconds: Long): String? {
        val windowStart = startedAtEpochMs - 120_000L
        val windowEnd = startedAtEpochMs + (durationSeconds.coerceAtLeast(0) * 1000L) + 300_000L
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
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
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
            var bestUri: Uri? = null
            var bestScore = Int.MIN_VALUE
            while (it.moveToNext()) {
                val name = it.getString(nameIdx).orEmpty()
                val score = scoreName(name, digits)
                if (score > bestScore) {
                    bestScore = score
                    bestUri = Uri.withAppendedPath(collection, it.getLong(idIdx).toString())
                }
            }
            if (bestScore >= 0) bestUri?.toString() else null
        }
    }

    private fun findInOemFolders(digits: String, windowStart: Long, windowEnd: Long): String? {
        if (!hasAudioPermission()) return null
        val root = Environment.getExternalStorageDirectory() ?: return null
        val candidates = mutableListOf<File>()
        OemRecordingPaths.pathsForDevice().forEach { relative ->
            val dir = File(root, relative)
            if (!dir.isDirectory) return@forEach
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
                ?.filter { it.lastModified() in windowStart..windowEnd }
                ?.forEach { candidates += it }
        }
        return candidates
            .maxByOrNull { scoreName(it.name, digits) }
            ?.takeIf { scoreName(it.name, digits) >= 0 }
            ?.let { Uri.fromFile(it).toString() }
    }

    private fun scoreName(name: String, digits: String): Int {
        val lower = name.lowercase()
        val nameDigits = lower.filter { it.isDigit() }
        var score = 0
        if (listOf("call", "record", "rec", "phone").any { lower.contains(it) }) score += 2
        if (digits.length >= 7 && nameDigits.contains(digits.takeLast(7))) score += 10
        else if (digits.length >= 4 && nameDigits.contains(digits.takeLast(4))) score += 4
        else if (digits.isNotEmpty() && !nameDigits.contains(digits.takeLast(4))) score -= 1
        return score
    }

    companion object {
        private val AUDIO_EXT = setOf("m4a", "mp3", "wav", "amr", "aac", "3gp", "ogg", "flac")
    }
}
