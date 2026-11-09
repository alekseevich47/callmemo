package com.alekseevich47.callmemo.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Builds a cheap amplitude preview without MediaCodec PCM decode.
 * Full decode previously hung mid/low-end devices on long OEM recordings.
 */
@Singleton
class WaveformAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun amplitudes(uriString: String, barCount: Int = 48): List<Float> {
        return runCatching { sampleAmplitudes(uriString, barCount) }
            .getOrElse { fallbackPattern(barCount) }
    }

    private fun sampleAmplitudes(uriString: String, barCount: Int): List<Float> {
        val extractor = MediaExtractor()
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme.isNullOrBlank() || uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: uriString.removePrefix("file://"))
            } else {
                extractor.setDataSource(context, uri, null)
            }
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return fallbackPattern(barCount)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else {
                1L
            }

            val peaks = FloatArray(barCount)
            val counts = IntArray(barCount)
            var safety = 0
            // Compressed sample size is a coarse energy proxy — no decoder, no hang.
            while (safety++ < 8_000) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0L) break
                val size = extractor.sampleSize.coerceAtLeast(0)
                val bar = ((sampleTime.toDouble() / durationUs) * (barCount - 1))
                    .toInt()
                    .coerceIn(0, barCount - 1)
                val energy = sqrt(size.toDouble()).toFloat()
                peaks[bar] = max(peaks[bar], energy)
                counts[bar] += 1
                if (!extractor.advance()) break
            }

            var maxPeak = 0.0001f
            for (i in peaks.indices) {
                if (counts[i] == 0 && i > 0) peaks[i] = peaks[i - 1] * 0.7f
                maxPeak = max(maxPeak, peaks[i])
            }
            if (counts.all { it == 0 }) return fallbackPattern(barCount)
            return peaks.map { (it / maxPeak).coerceIn(0.08f, 1f) }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun fallbackPattern(barCount: Int): List<Float> {
        return List(barCount) { i ->
            val t = i / barCount.toFloat()
            (0.25f + 0.55f * abs(kotlin.math.sin(t * 12f)) + 0.2f * abs(kotlin.math.sin(t * 3.1f)))
                .coerceIn(0.12f, 1f)
        }
    }
}
