package com.audiora.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton play state tracker providing instant access to play/pause state
 * without depending on MediaController connection.
 *
 * Mirrors Voice's [voice.core.playback.playstate.PlayStateManager].
 *
 * Usage:
 *   - PlaybackManager.setupControllerListener() updates this on every state change.
 *   - PlayerScreen reads [playStateFlow] directly — no controller dependency.
 */
class PlayStateManager {
    private val _playStateFlow = MutableStateFlow(PlayState.Paused)
    val playStateFlow: StateFlow<PlayState> = _playStateFlow

    var playState: PlayState
        set(value) {
            _playStateFlow.value = value
        }
        get() = _playStateFlow.value

    enum class PlayState {
        Playing,
        Paused,
    }
}
