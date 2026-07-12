package com.audiora.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.audiora.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.List

/**
 * Media playback service hosted in a foreground process.
 *
 * Uses [MediaLibraryService] (rather than [androidx.media3.session.MediaSessionService]) so that
 * Media3's [DefaultMediaNotificationProvider] automatically creates and manages the media
 * notification with skip-forward / skip-backward buttons, cover art, and lock-screen controls.
 *
 * Mirrors Voice's [voice.core.playback.session.PlaybackService] pattern.
 */
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handles audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15000) // 15 seconds rewind
            .setSeekForwardIncrementMs(30000) // 30 seconds fast-forward
            .build()

        // Wrap the player so notification skip buttons become rewind / fast-forward.
        // Keep the raw ExoPlayer in ExoPlayerInstance so PlaybackManager can still access
        // ExoPlayer-specific properties (audioSessionId, skipSilenceEnabled).
        val wrappedPlayer = AudioraPlayer(exoPlayer)
        ExoPlayerInstance.player = exoPlayer

        // Build a PendingIntent for notification tap — use a flag (not book ID) so it works
        // regardless of which book is currently playing, matching Voice's approach.
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, wrappedPlayer, callback)
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                        .setDisplayName("Rewind")
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setSlots(CommandButton.SLOT_BACK)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                        .setDisplayName("Fast Forward")
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .build(),
                )
            )
            .build()

        // Wire the notification provider so Media3 builds the notification automatically
        setMediaNotificationProvider(VoiceMediaNotificationProvider(this))

        Timber.d("PlaybackService: MediaLibrarySession created with notification provider")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession.takeUnless { session ->
            session.invokeIsReleased
        }.also {
            if (it == null) {
                Timber.w("onGetSession returns null because the session is already released")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        ExoPlayerInstance.player = null
    }

    /**
     * Minimal callback that handles the library session contract.
     * Audiora doesn't use the browsable-media-tree features of MediaLibraryService,
     * but MediaLibrarySession requires a non-null callback.
     */
    private val callback = object : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            return Futures.immediateFuture(mediaItems)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.accept(
                session.availableSessionCommands,
                session.availablePlayerCommands,
            )
        }
    }
}

/**
 * Workaround to check whether the session is released via reflection.
 * Mirrors Voice's same workaround:
 * https://github.com/androidx/media/issues/422
 */
private val MediaSession.invokeIsReleased: Boolean
    get() = try {
        MediaSession::class.java.getDeclaredMethod("isReleased")
            .apply { isAccessible = true }
            .invoke(this) as Boolean
    } catch (e: Exception) {
        Timber.w(e, "Couldn't check if it's released")
        false
    }
