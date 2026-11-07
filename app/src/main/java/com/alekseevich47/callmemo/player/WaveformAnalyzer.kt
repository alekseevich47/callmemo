package com.alekseevich47.callmemo.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Singleton
class WaveformAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun amplitudes(uriString: String, barCount: Int = 48): List<Float> {
        return runCatching { decodeAmplitudes(uriString, barCount) }
            .getOrElse { fallbackPattern(barCount) }
    }

    private fun decodeAmplitudes(uriString: String, barCount: Int): List<Float> {
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
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return fallbackPattern(barCount)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val peaks = FloatArray(barCount)
            val counts = IntArray(barCount)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else {
                1L
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var safety = 0
            while (!outputDone && safety++ < 20_000) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val sample = extractor.readSampleData(buffer, 0)
                        if (sample < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sample, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        val pcm = ByteArray(info.size)
                        out.position(info.offset)
                        out.get(pcm)
                        val shorts = ShortArray(pcm.size / 2)
                        ByteBuffer.wrap(pcm).order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                        val bar = ((info.presentationTimeUs.toDouble() / durationUs) * (barCount - 1))
                            .toInt()
                            .coerceIn(0, barCount - 1)
                        var sum = 0.0
                        for (s in shorts) sum += (s * s).toDouble()
                        val rms = sqrt(sum / shorts.size.coerceAtLeast(1)).toFloat() / Short.MAX_VALUE
                        peaks[bar] = max(peaks[bar], rms)
                        counts[bar] += 1
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
            codec.stop()
            codec.release()

            var maxPeak = 0.0001f
            for (i in peaks.indices) {
                if (counts[i] == 0 && i > 0) peaks[i] = peaks[i - 1] * 0.7f
                maxPeak = max(maxPeak, peaks[i])
            }
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
