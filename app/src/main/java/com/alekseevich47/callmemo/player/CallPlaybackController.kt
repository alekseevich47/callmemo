package com.alekseevich47.callmemo.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
)

@Singleton
class CallPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val speeds = listOf(1f, 1.3f, 1.5f, 2f)

    fun play(
        callId: Long?,
        contactName: String,
        number: String,
        uri: String?,
        durationHintMs: Long = 0L,
    ) {
        ensurePlayer()
        val exo = player ?: return
        val fallbackDuration = durationHintMs.coerceAtLeast(1_000L)
        _state.value = PlaybackUiState(
            visible = true,
            callId = callId,
            contactName = contactName,
            number = number,
            uri = uri,
            playing = true,
            speed = 1f,
            durationMs = fallbackDuration,
            positionMs = 0L,
        )
        if (!uri.isNullOrBlank() && !uri.startsWith("demo://")) {
            runCatching {
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                exo.prepare()
                exo.playWhenReady = true
                exo.setPlaybackSpeed(1f)
            }.onFailure {
                _state.update { it.copy(durationMs = fallbackDuration, playing = true) }
            }
        }
        startTicker()
    }

    fun toggle() {
        val exo = player
        val current = _state.value
        if (!current.visible) return
        if (exo != null && !current.uri.isNullOrBlank() && !current.uri.startsWith("demo://")) {
            if (exo.isPlaying) exo.pause() else exo.play()
            _state.update { it.copy(playing = exo.isPlaying) }
        } else {
            _state.update { it.copy(playing = !it.playing) }
        }
    }

    fun seekTo(positionMs: Long) {
        val exo = player
        val duration = _state.value.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, duration)
        if (exo != null && !_state.value.uri.isNullOrBlank() && !_state.value.uri!!.startsWith("demo://")) {
            exo.seekTo(clamped)
        }
        _state.update { it.copy(positionMs = clamped) }
    }

    fun cycleSpeed() {
        val current = _state.value.speed
        val idx = speeds.indexOfFirst { it == current }.let { if (it < 0) 0 else (it + 1) % speeds.size }
        val next = speeds[idx]
        player?.setPlaybackSpeed(next)
        _state.update { it.copy(speed = next) }
    }

    fun hide() {
        ticker?.cancel()
        player?.stop()
        player?.clearMediaItems()
        _state.value = PlaybackUiState()
    }

    private fun ensurePlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(appContext).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _state.update {
                            it.copy(durationMs = exo.duration.coerceAtLeast(0L))
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        _state.update { it.copy(playing = false, positionMs = it.durationMs) }
                    }
                }
            })
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = _state.value
                if (!current.visible) break
                val exo = player
                if (exo != null && !current.uri.isNullOrBlank() && !current.uri.startsWith("demo://")) {
                    _state.update {
                        it.copy(
                            positionMs = exo.currentPosition.coerceAtLeast(0L),
                            durationMs = exo.duration.coerceAtLeast(it.durationMs),
                            playing = exo.isPlaying,
                        )
                    }
                } else if (current.playing) {
                    _state.update {
                        val next = (it.positionMs + 200L).coerceAtMost(it.durationMs.coerceAtLeast(1L))
                        it.copy(
                            positionMs = next,
                            playing = next < it.durationMs,
                        )
                    }
                }
                delay(200)
            }
        }
    }
}
