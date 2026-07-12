package com.audiora.feature.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Extends [DefaultMediaNotificationProvider] to ensure skip-forward / skip-backward
 * buttons appear in compact notification mode on Android < 13.
 *
 * Mirrors Voice's [voice.core.playback.session.VoiceMediaNotificationProvider].
 *
 * @see DefaultMediaNotificationProvider
 */
class VoiceMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
            .apply {
                forEachIndexed { index, commandButton ->
                    // Without this, Android < 13 only shows play/pause in compact mode.
                    // Each button is assigned its sequential index so all three
                    // (previous, play/pause, next) appear in the compact notification.
                    // https://github.com/PaulWoitaschek/Voice/issues/1904
                    commandButton.extras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
                }
            }
    }
}
