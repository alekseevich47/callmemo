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
import kotlin.math.abs

/**
 * Resolves OEM call recordings (Tecno/HiOS: Music/PhoneRecord/<number>/…aac).
 *
 * Prefer MediaStore content:// URIs (scoped storage). File walk is a fallback and
 * recursively scans number subfolders.
 */
@Singleton
class RecordingLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun findRecording(number: String, startedAtEpochMs: Long, durationSeconds: Long): String? {
        if (startedAtEpochMs <= 0L) return null
        val digits = PhoneNumberUtils.digitsOnly(number)
        val windowStart = startedAtEpochMs - 300_000L
        val windowEnd = startedAtEpochMs + (durationSeconds.coerceAtLeast(0) * 1000L) + 900_000L

        return findInMediaStore(digits, startedAtEpochMs, windowStart, windowEnd)
            ?: findInOemFolders(digits, startedAtEpochMs, windowStart, windowEnd)
            ?: findNearestPhoneRecord(digits, startedAtEpochMs)
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

    private fun findInMediaStore(
        digits: String,
        targetMs: Long,
        windowStart: Long,
        windowEnd: Long,
    ): String? {
        if (!hasAudioPermission()) return null
        val fromAudio = queryAudioCollection(digits, targetMs, windowStart, windowEnd)
        if (fromAudio != null) return fromAudio
        return queryFilesCollection(digits, targetMs, windowStart, windowEnd)
    }

    private fun queryAudioCollection(
        digits: String,
        targetMs: Long,
        windowStart: Long,
        windowEnd: Long,
    ): String? {
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
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH)
        }.toTypedArray()
        return queryCandidates(
            collection = collection,
            projection = projection,
            idColumn = MediaStore.Audio.Media._ID,
            nameColumn = MediaStore.Audio.Media.DISPLAY_NAME,
            addedColumn = MediaStore.Audio.Media.DATE_ADDED,
            modifiedColumn = MediaStore.Audio.Media.DATE_MODIFIED,
            pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else null,
            digits = digits,
            targetMs = targetMs,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
    }

    private fun queryFilesCollection(
        digits: String,
        targetMs: Long,
        windowStart: Long,
        windowEnd: Long,
    ): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        return queryCandidates(
            collection = collection,
            projection = projection,
            idColumn = MediaStore.Files.FileColumns._ID,
            nameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME,
            addedColumn = MediaStore.Files.FileColumns.DATE_ADDED,
            modifiedColumn = MediaStore.Files.FileColumns.DATE_MODIFIED,
            pathColumn = MediaStore.Files.FileColumns.RELATIVE_PATH,
            digits = digits,
            targetMs = targetMs,
            windowStart = windowStart,
            windowEnd = windowEnd,
            mimeColumn = MediaStore.Files.FileColumns.MIME_TYPE,
        )
    }

    private fun queryCandidates(
        collection: Uri,
        projection: Array<String>,
        idColumn: String,
        nameColumn: String,
        addedColumn: String,
        modifiedColumn: String,
        pathColumn: String?,
        digits: String,
        targetMs: Long,
        windowStart: Long,
        windowEnd: Long,
        mimeColumn: String? = null,
    ): String? {
        val selection = buildString {
            append("(($addedColumn BETWEEN ? AND ?) OR ($modifiedColumn BETWEEN ? AND ?))")
            if (pathColumn != null) {
                append(" AND (")
                PHONE_RECORD_PATH_LIKE.forEachIndexed { index, _ ->
                    if (index > 0) append(" OR ")
                    append("LOWER($pathColumn) LIKE ?")
                }
                append(")")
            }
        }
        val startSec = (windowStart / 1000L).toString()
        val endSec = (windowEnd / 1000L).toString()
        val args = buildList {
            add(startSec); add(endSec); add(startSec); add(endSec)
            if (pathColumn != null) addAll(PHONE_RECORD_PATH_LIKE)
        }.toTypedArray()

        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "$modifiedColumn DESC",
            )
        }.getOrNull() ?: return null

        return cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(idColumn)
            val nameIdx = c.getColumnIndexOrThrow(nameColumn)
            val addedIdx = c.getColumnIndexOrThrow(addedColumn)
            val modifiedIdx = c.getColumnIndexOrThrow(modifiedColumn)
            val pathIdx = pathColumn?.let { c.getColumnIndex(it) } ?: -1
            val mimeIdx = mimeColumn?.let { c.getColumnIndex(it) } ?: -1

            var bestUri: Uri? = null
            var bestRank = Long.MIN_VALUE
            while (c.moveToNext()) {
                val name = c.getString(nameIdx).orEmpty()
                val relativePath = if (pathIdx >= 0) c.getString(pathIdx).orEmpty() else ""
                if (mimeIdx >= 0) {
                    val mime = c.getString(mimeIdx).orEmpty().lowercase()
                    if (mime.isNotBlank() && !mime.startsWith("audio/") && mime != "application/octet-stream") {
                        continue
                    }
                }
                if (!looksLikeAudioFile(name)) continue
                val score = scoreCandidate(name, relativePath, digits)
                if (score < MIN_ACCEPT_SCORE) continue
                val modifiedMs = c.getLong(modifiedIdx) * 1000L
                val addedMs = c.getLong(addedIdx) * 1000L
                val closest = listOf(modifiedMs, addedMs).minBy { abs(it - targetMs) }
                val delta = abs(closest - targetMs)
                // Higher score wins; among equals prefer closer timestamp.
                val rank = score * 1_000_000_000L - delta
                if (rank > bestRank) {
                    bestRank = rank
                    bestUri = ContentUris.withAppendedId(collection, c.getLong(idIdx))
                }
            }
            bestUri?.toString()
        }
    }

    /**
     * Soft fallback: any PhoneRecord file whose folder/name matches the number,
     * nearest to the call time (up to 48h). Covers OEM DATE_* quirks.
     */
    private fun findNearestPhoneRecord(digits: String, targetMs: Long): String? {
        if (!hasAudioPermission() || digits.length < 4) return null
        if (Build.VERSION.SDK_INT < 29) {
            return findInOemFolders(
                digits = digits,
                targetMs = targetMs,
                windowStart = targetMs - 48L * 3600_000L,
                windowEnd = targetMs + 48L * 3600_000L,
            )
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val selection = buildString {
            append("(")
            PHONE_RECORD_PATH_LIKE.forEachIndexed { index, _ ->
                if (index > 0) append(" OR ")
                append("LOWER(${MediaStore.Audio.Media.RELATIVE_PATH}) LIKE ?")
            }
            append(")")
        }
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                PHONE_RECORD_PATH_LIKE.toTypedArray(),
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return null

        return cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val addedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val pathIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            var bestUri: Uri? = null
            var bestDelta = Long.MAX_VALUE
            val maxDelta = 48L * 3600_000L
            while (c.moveToNext()) {
                val name = c.getString(nameIdx).orEmpty()
                val relativePath = c.getString(pathIdx).orEmpty()
                if (!looksLikeAudioFile(name)) continue
                val score = scoreCandidate(name, relativePath, digits)
                if (score < MIN_NUMBER_MATCH_SCORE) continue
                val modifiedMs = c.getLong(modifiedIdx) * 1000L
                val addedMs = c.getLong(addedIdx) * 1000L
                val delta = listOf(modifiedMs, addedMs).minOf { abs(it - targetMs) }
                if (delta <= maxDelta && delta < bestDelta) {
                    bestDelta = delta
                    bestUri = ContentUris.withAppendedId(collection, c.getLong(idIdx))
                }
            }
            bestUri?.toString()
        }
    }

    private fun findInOemFolders(
        digits: String,
        targetMs: Long,
        windowStart: Long,
        windowEnd: Long,
    ): String? {
        if (!hasAudioPermission()) return null
        val root = Environment.getExternalStorageDirectory() ?: return null
        var best: Pair<File, Long>? = null
        OemRecordingPaths.pathsForDevice().forEach { relative ->
            val dir = File(root, relative)
            if (!dir.isDirectory) return@forEach
            walkAudioFiles(dir, depth = 0).forEach { file ->
                val modified = file.lastModified()
                if (modified !in windowStart..windowEnd) return@forEach
                val relativePath = relative + "/" +
                    (file.parentFile?.relativeToOrNull(dir)?.path?.replace('\\', '/') ?: "")
                val score = scoreCandidate(file.name, relativePath, digits)
                if (score < MIN_ACCEPT_SCORE) return@forEach
                val rank = score * 1_000_000_000L - abs(modified - targetMs)
                val currentBest = best
                if (currentBest == null || rank > currentBest.second) {
                    best = file to rank
                }
            }
        }
        return best?.first?.let { fileToPlayableUri(it) }
    }

    private fun walkAudioFiles(dir: File, depth: Int): Sequence<File> = sequence {
        if (depth > 4) return@sequence
        val children = dir.listFiles() ?: return@sequence
        for (child in children) {
            when {
                child.isFile && looksLikeAudioFile(child.name) -> yield(child)
                child.isDirectory -> yieldAll(walkAudioFiles(child, depth + 1))
            }
        }
    }

    private fun fileToPlayableUri(file: File): String {
        // Prefer MediaStore content URI — file:// often fails under scoped storage.
        val content = mediaStoreUriForFile(file)
        if (content != null) return content
        return Uri.fromFile(file).toString()
    }

    private fun mediaStoreUriForFile(file: File): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val name = file.name
        val parent = file.parentFile?.name.orEmpty()
        val selection =
            "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND LOWER(${MediaStore.Audio.Media.RELATIVE_PATH}) LIKE ?"
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                selection,
                arrayOf(name, "%phonerecord%$parent%"),
                null,
            )
        }.getOrNull() ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val id = it.getLong(0)
            ContentUris.withAppendedId(collection, id).toString()
        }
    }

    companion object {
        private val AUDIO_EXT = setOf("m4a", "mp3", "wav", "amr", "aac", "3gp", "ogg", "flac", "awb")

        /** Call-like path/name without requiring digits (OEM generic filenames). */
        const val MIN_ACCEPT_SCORE = 2

        /** Soft nearest-match requires number digits in path/name. */
        const val MIN_NUMBER_MATCH_SCORE = 6

        private val PHONE_RECORD_PATH_LIKE = listOf(
            "%phonerecord%",
            "%phonerecorod%",
            "%call_rec%",
            "%callrecord%",
            "%recordings/call%",
            "%/call/%",
        )

        private val CALL_NAME_HINTS = listOf(
            "call", "record", "rec", "phone", "звук", "вызов", "звон", "телефон",
        )
        private val CALL_PATH_HINTS = listOf(
            "call", "phonerecord", "phonerecorod", "recordings", "call_rec", "callrecord",
            "sound_recorder", "voicerecorder",
        )

        fun looksLikeAudioFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            // Tecno sometimes writes extension-less or uppercase AAC.
            return ext in AUDIO_EXT || ext.isEmpty() || ext == "aac" || name.lowercase().endsWith(".aac")
        }

        fun scoreCandidate(name: String, relativePath: String, digits: String): Int {
            val lowerName = name.lowercase()
            val lowerPath = relativePath.lowercase().replace('\\', '/')
            val blobDigits = (lowerName + lowerPath).filter { it.isDigit() }
            var score = 0

            if (CALL_NAME_HINTS.any { lowerName.contains(it) }) score += 2
            if (CALL_PATH_HINTS.any { lowerPath.contains(it) }) score += 3

            // Tecno: Music/PhoneRecord/<number>/file.aac — digits live in the folder path.
            when {
                digits.length >= 7 && blobDigits.contains(digits.takeLast(7)) -> score += 10
                digits.length >= 4 && blobDigits.contains(digits.takeLast(4)) -> score += 4
                digits.length >= 4 && blobDigits.isNotEmpty() &&
                    !CALL_PATH_HINTS.any { lowerPath.contains(it) } -> score -= 2
            }

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
