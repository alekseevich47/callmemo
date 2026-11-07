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
    val ended: Boolean = false,
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
    private var waveformJob: Job? = null

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
        waveformJob?.cancel()
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
            ended = false,
        )
        playJob = scope.launch {
            // Resolve URI quickly; start ExoPlayer ASAP — waveform loads in background.
            val resolved = withContext(Dispatchers.IO) {
                resolvePlayableUri(uri, number, startedAtEpochMs, hint)
            }
            if (resolved.isNullOrBlank()) {
                // No recording found: keep bar visible but do not play synthetic beeps.
                _state.update { it.copy(playing = false, uri = null) }
                startNotificationService()
                return@launch
            }
            val exo = player ?: return@launch
            _state.update { it.copy(uri = resolved, ended = false) }
            applySpeakerRoute()
            runCatching {
                exo.stop()
                exo.clearMediaItems()
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(resolved)))
                exo.setPlaybackSpeed(_state.value.speed)
                exo.prepare()
                exo.playWhenReady = true
                exo.volume = 1f
            }.onFailure {
                _state.update { state -> state.copy(playing = false) }
            }
            startTicker()
            startNotificationService()
            waveformJob = launch(Dispatchers.IO) {
                val amplitudes = waveformAnalyzer.amplitudes(resolved)
                _state.update { it.copy(amplitudes = amplitudes) }
            }
        }
    }

    fun toggle() {
        val exo = player
        val current = _state.value
        if (!current.visible) return
        if (exo != null && !current.uri.isNullOrBlank()) {
            val atEnd = current.ended ||
                exo.playbackState == Player.STATE_ENDED ||
                (current.durationMs > 0L && current.positionMs >= current.durationMs - 250L)
            when {
                atEnd -> {
                    exo.seekTo(0L)
                    exo.playWhenReady = true
                    exo.play()
                    _state.update { it.copy(playing = true, positionMs = 0L, ended = false) }
                }
                exo.isPlaying -> {
                    exo.pause()
                    _state.update { it.copy(playing = false) }
                }
                else -> {
                    exo.play()
                    _state.update { it.copy(playing = true, ended = false) }
                }
            }
        } else if (current.uri.isNullOrBlank()) {
            // Retry resolve is left to a fresh play() call from UI.
            _state.update { it.copy(playing = false) }
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
            if (_state.value.ended || exo.playbackState == Player.STATE_ENDED) {
                exo.playWhenReady = true
            }
        }
        _state.update { it.copy(positionMs = clamped, ended = false) }
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
        waveformJob?.cancel()
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

    private fun ensurePlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(appContext).build().also { exo ->
            applySpeakerRoute(exo)
            exo.volume = 1f
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying, ended = if (isPlaying) false else it.ended) }
                    startNotificationService()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val mediaDuration = exo.duration
                        _state.update {
                            it.copy(
                                durationMs = if (mediaDuration > 0) mediaDuration else it.durationMs,
                                playing = exo.playWhenReady && exo.isPlaying,
                                ended = false,
                            )
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        _state.update {
                            it.copy(
                                playing = false,
                                positionMs = it.durationMs,
                                ended = true,
                            )
                        }
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
            var ticks = 0
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
                    // Progress: update notification in-place. Never restart FGS every tick —
                    // that previously froze the app (startForegroundService ×2/sec).
                    ticks++
                    if (current.playing && ticks % 4 == 0) {
                        refreshNotification()
                    }
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

    private fun refreshNotification() {
        if (!_state.value.visible) return
        val intent = Intent(appContext, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_REFRESH
        }
        runCatching { appContext.startService(intent) }
    }

    private fun stopNotificationService() {
        runCatching {
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
        }
    }
}
