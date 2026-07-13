package com.audiora.feature.player

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Cross-process commands sent from PlaybackManager (UI process) to PlaybackService.
 *
 * Mirrors Voice's CustomCommand sealed interface.
 * Uses simple Bundle encoding instead of kotlinx.serialization to avoid adding a dependency.
 */
sealed interface PlaybackCommand {

    companion object {
        const val CUSTOM_COMMAND_ACTION = "audiora_custom_command"
        const val EXTRA_COMMAND_TYPE = "command_type"
        const val EXTRA_BOOL_VALUE = "command_bool"
        const val EXTRA_FLOAT_VALUE = "command_float"

        const val TYPE_SET_SKIP_SILENCE = "set_skip_silence"
        const val TYPE_SET_GAIN = "set_gain"

        fun parse(customCommand: SessionCommand, args: Bundle): PlaybackCommand? {
            if (customCommand.customAction != CUSTOM_COMMAND_ACTION) return null
            return when (args.getString(EXTRA_COMMAND_TYPE)) {
                TYPE_SET_SKIP_SILENCE -> SetSkipSilence(args.getBoolean(EXTRA_BOOL_VALUE))
                TYPE_SET_GAIN -> SetGain(args.getFloat(EXTRA_FLOAT_VALUE))
                else -> null
            }
        }

        fun result(): ListenableFuture<androidx.media3.session.SessionResult> {
            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
    }

    data class SetSkipSilence(val enabled: Boolean) : PlaybackCommand
    data class SetGain(val gainDb: Float) : PlaybackCommand
}

/** Sends a PlaybackCommand to the service via the MediaController. */
internal fun MediaController.sendPlaybackCommand(command: PlaybackCommand) {
    val extras = Bundle().apply {
        when (command) {
            is PlaybackCommand.SetSkipSilence -> {
                putString(PlaybackCommand.EXTRA_COMMAND_TYPE, PlaybackCommand.TYPE_SET_SKIP_SILENCE)
                putBoolean(PlaybackCommand.EXTRA_BOOL_VALUE, command.enabled)
            }
            is PlaybackCommand.SetGain -> {
                putString(PlaybackCommand.EXTRA_COMMAND_TYPE, PlaybackCommand.TYPE_SET_GAIN)
                putFloat(PlaybackCommand.EXTRA_FLOAT_VALUE, command.gainDb)
            }
        }
    }
    sendCustomCommand(
        SessionCommand(PlaybackCommand.CUSTOM_COMMAND_ACTION, android.os.Bundle.EMPTY),
        extras,
    )
}
