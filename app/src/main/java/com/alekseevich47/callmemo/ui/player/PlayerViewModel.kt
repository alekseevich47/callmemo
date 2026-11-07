package com.alekseevich47.callmemo.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class StickyPlayerState(
    val visible: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val uri: String? = null,
    val playing: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(StickyPlayerState())
    val state: StateFlow<StickyPlayerState> = _state.asStateFlow()

    fun play(title: String, subtitle: String, uri: String?) {
        _state.value = StickyPlayerState(
            visible = true,
            title = title,
            subtitle = subtitle,
            uri = uri,
            playing = true,
        )
    }

    fun toggle() {
        _state.update { current ->
            if (!current.visible) current else current.copy(playing = !current.playing)
        }
    }

    fun hide() {
        _state.value = StickyPlayerState()
    }
}
