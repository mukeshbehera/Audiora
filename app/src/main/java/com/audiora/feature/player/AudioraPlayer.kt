package com.audiora.feature.player

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import timber.log.Timber

/**
 * Wraps ExoPlayer to remap seek-to-next / seek-to-previous commands into
 * fast-forward / rewind, matching Voice's [voice.core.playback.player.VoicePlayer]
 * pattern.
 *
 * Without this wrapper [MediaLibraryService] exposes "skip to next" / "skip to previous"
 * notification buttons that skip entire tracks rather than seeking within the current book.
 */
class AudioraPlayer(player: Player) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .addAll(
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }

    override fun seekToPreviousMediaItem() {
        Timber.d("AudioraPlayer: seekToPreviousMediaItem -> seekBack")
        seekBack()
    }

    override fun seekToNextMediaItem() {
        Timber.d("AudioraPlayer: seekToNextMediaItem -> seekForward")
        seekForward()
    }

    override fun seekToPrevious() {
        Timber.d("AudioraPlayer: seekToPrevious -> seekBack")
        seekBack()
    }

    override fun seekToNext() {
        Timber.d("AudioraPlayer: seekToNext -> seekForward")
        seekForward()
    }

    override fun seekBack() {
        // Delegate to the underlying ExoPlayer's built-in seek-back increment
        val currentPosition = currentPosition.takeUnless { it == C.TIME_UNSET } ?: return
        val seekBackIncrement = seekBackIncrement.takeUnless { it == C.TIME_UNSET } ?: 15000L
        val targetPosition = (currentPosition - seekBackIncrement).coerceAtLeast(0L)
        seekTo(targetPosition)
    }

    override fun seekForward() {
        // Delegate to the underlying ExoPlayer's built-in seek-forward increment
        val currentPosition = currentPosition.takeUnless { it == C.TIME_UNSET } ?: return
        val seekForwardIncrement = seekForwardIncrement.takeUnless { it == C.TIME_UNSET } ?: 30000L
        val duration = duration.takeUnless { it == C.TIME_UNSET } ?: return
        val targetPosition = (currentPosition + seekForwardIncrement).coerceAtMost(duration)
        seekTo(targetPosition)
    }
}
