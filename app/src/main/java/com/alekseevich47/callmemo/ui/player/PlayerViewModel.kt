package com.alekseevich47.callmemo.ui.player

import androidx.lifecycle.ViewModel
import com.alekseevich47.callmemo.player.CallPlaybackController
import com.alekseevich47.callmemo.player.PlaybackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: CallPlaybackController,
) : ViewModel() {
    val state: StateFlow<PlaybackUiState> = controller.state

    fun play(
        callId: Long?,
        contactName: String,
        number: String,
        uri: String?,
        durationHintMs: Long = 0L,
        startedAtEpochMs: Long = 0L,
    ) {
        controller.play(callId, contactName, number, uri, durationHintMs, startedAtEpochMs)
    }

    fun toggle() = controller.toggle()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun cycleSpeed() = controller.cycleSpeed()

    fun hide() = controller.hide()
}
