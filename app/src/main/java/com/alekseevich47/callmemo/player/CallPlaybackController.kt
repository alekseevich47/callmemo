package com.alekseevich47.callmemo.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alekseevich47.callmemo.data.recording.RecordingLocator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val visible: Boolean = false,
    val callId: Long? = null,
    val contactName: String = "",
    val number: String = "",
    val uri: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val amplitudes: List<Float> = emptyList(),
    val earpiece: Boolean = false,
)

@Singleton
class CallPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
    private val recordingLocator: RecordingLocator,
    private val waveformAnalyzer: WaveformAnalyzer,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private var playJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val speeds = listOf(1f, 1.3f, 1.5f, 2f)

    fun play(
        callId: Long?,
        contactName: String,
        number: String,
        uri: String?,
        durationHintMs: Long = 0L,
        startedAtEpochMs: Long = 0L,
    ) {
        playJob?.cancel()
        ensurePlayer()
        val hint = durationHintMs.coerceAtLeast(1_000L)
        _state.value = PlaybackUiState(
            visible = true,
            callId = callId,
            contactName = contactName,
            number = number,
            uri = uri,
            playing = true,
            speed = 1f,
            durationMs = hint,
            positionMs = 0L,
            amplitudes = emptyList(),
        )
        playJob = scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                resolvePlayableUri(uri, number, startedAtEpochMs, hint)
                    ?: synthesizeTone(hint)
            }
            val amplitudes = withContext(Dispatchers.IO) {
                waveformAnalyzer.amplitudes(resolved)
            }
            val exo = player ?: return@launch
            _state.update { it.copy(uri = resolved, amplitudes = amplitudes) }
            applySpeakerRoute()
            runCatching {
                exo.stop()
                exo.clearMediaItems()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(resolved)))
                exo.setPlaybackSpeed(1f)
                exo.prepare()
                exo.playWhenReady = true
                exo.volume = 1f
            }.onFailure {
                _state.update { state -> state.copy(playing = false) }
            }
            startTicker()
            startNotificationService()
        }
    }

    fun toggle() {
        val exo = player
        val current = _state.value
        if (!current.visible) return
        if (exo != null && !current.uri.isNullOrBlank()) {
            if (exo.isPlaying) exo.pause() else exo.play()
            _state.update { it.copy(playing = exo.isPlaying || exo.playWhenReady) }
        } else {
            _state.update { it.copy(playing = !it.playing) }
        }
        startNotificationService()
    }

    fun seekTo(positionMs: Long) {
        val exo = player
        val duration = _state.value.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, duration)
        if (exo != null && !_state.value.uri.isNullOrBlank()) {
            exo.seekTo(clamped)
        }
        _state.update { it.copy(positionMs = clamped) }
        startNotificationService()
    }

    fun cycleSpeed() {
        val current = _state.value.speed
        val idx = speeds.indexOfFirst { it == current }.let { if (it < 0) 0 else (it + 1) % speeds.size }
        val next = speeds[idx]
        player?.setPlaybackSpeed(next)
        _state.update { it.copy(speed = next) }
    }

    fun setEarpiece(enabled: Boolean) {
        if (_state.value.earpiece == enabled) return
        _state.update { it.copy(earpiece = enabled) }
        if (enabled) applyEarpieceRoute() else applySpeakerRoute()
    }

    fun hide() {
        playJob?.cancel()
        ticker?.cancel()
        player?.stop()
        player?.clearMediaItems()
        applySpeakerRoute()
        stopNotificationService()
        _state.value = PlaybackUiState()
    }

    private fun resolvePlayableUri(
        uri: String?,
        number: String,
        startedAtEpochMs: Long,
        durationHintMs: Long,
    ): String? {
        if (!uri.isNullOrBlank() && !uri.startsWith("demo://")) {
            if (uri.startsWith("content:") || uri.startsWith("file:")) return uri
            val asFile = File(uri)
            if (asFile.exists()) return Uri.fromFile(asFile).toString()
            return uri
        }
        return recordingLocator.findRecording(
            number = number,
            startedAtEpochMs = startedAtEpochMs,
            durationSeconds = (durationHintMs / 1000L).coerceAtLeast(1L),
        )
    }

    /** Audible placeholder matching call duration when no OEM file is available. */
    private fun synthesizeTone(durationHintMs: Long): String {
        val ms = durationHintMs.coerceIn(3_000L, 600_000L)
        val file = File(appContext.cacheDir, "callmemo_tone_${ms}.wav")
        if (!file.exists() || file.length() < 44L) {
            writeToneWav(file, ms)
        }
        return Uri.fromFile(file).toString()
    }

    private fun writeToneWav(file: File, durationMs: Long) {
        val sampleRate = 22_050
        val seconds = durationMs / 1000.0
        val totalSamples = (seconds * sampleRate).toInt().coerceAtLeast(sampleRate)
        val pcm = ByteArray(totalSamples * 2)
        for (s in 0 until totalSamples) {
            val t = s.toDouble() / sampleRate
            val envelope = when {
                t < 0.03 -> t / 0.03
                t > seconds - 0.05 -> ((seconds - t).coerceAtLeast(0.0) / 0.05)
                else -> 1.0
            }
            // Soft speech-like amplitude modulation so waveform analyzer has peaks.
            val mod = 0.55 + 0.45 * kotlin.math.sin(2.0 * Math.PI * 3.2 * t)
            val sample = (
                kotlin.math.sin(2.0 * Math.PI * 440.0 * t) *
                    0.22 * envelope * mod * Short.MAX_VALUE
                ).toInt().toShort()
            pcm[s * 2] = (sample.toInt() and 0xff).toByte()
            pcm[s * 2 + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
        }
        java.io.FileOutputStream(file).use { out ->
            fun writeInt(v: Int) {
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
                out.write((v shr 16) and 0xff)
                out.write((v shr 24) and 0xff)
            }
            fun writeShortLe(v: Int) {
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
            }
            out.write("RIFF".toByteArray())
            writeInt(36 + pcm.size)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            writeInt(16)
            writeShortLe(1)
            writeShortLe(1)
            writeInt(sampleRate)
            writeInt(sampleRate * 2)
            writeShortLe(2)
            writeShortLe(16)
            out.write("data".toByteArray())
            writeInt(pcm.size)
            out.write(pcm)
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(appContext).build().also { exo ->
            applySpeakerRoute(exo)
            exo.volume = 1f
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                    startNotificationService()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val mediaDuration = exo.duration
                        _state.update {
                            it.copy(
                                durationMs = if (mediaDuration > 0) mediaDuration else it.durationMs,
                                playing = exo.playWhenReady,
                            )
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        _state.update { it.copy(playing = false, positionMs = it.durationMs) }
                        startNotificationService()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.update { it.copy(playing = false) }
                }
            })
        }
    }

    private fun applySpeakerRoute(exo: ExoPlayer? = player) {
        audioManager.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        exo?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
    }

    private fun applyEarpieceRoute(exo: ExoPlayer? = player) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        exo?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = _state.value
                if (!current.visible) break
                val exo = player
                if (exo != null && !current.uri.isNullOrBlank()) {
                    val mediaDuration = exo.duration
                    _state.update {
                        it.copy(
                            positionMs = exo.currentPosition.coerceAtLeast(0L),
                            durationMs = if (mediaDuration > 0) mediaDuration else it.durationMs,
                            playing = exo.isPlaying,
                        )
                    }
                    if (current.playing) startNotificationService()
                }
                delay(500)
            }
        }
    }

    private fun startNotificationService() {
        if (!_state.value.visible) {
            stopNotificationService()
            return
        }
        val intent = Intent(appContext, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SYNC
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun stopNotificationService() {
        runCatching {
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
        }
    }
}
