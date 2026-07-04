package com.audiora.feature.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.audiora.MainActivity

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        ExoPlayerInstance.player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handles audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15000) // 15 seconds rew
            .setSeekForwardIncrementMs(30000) // 30 seconds ff
            .build()

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

        mediaSession = MediaSession.Builder(this, ExoPlayerInstance.player!!)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        ExoPlayerInstance.player = null
        super.onDestroy()
    }
}

/**
 * Process-safe singleton holding the ExoPlayer reference.
 * PlaybackManager accesses this for skipSilence, volume gain (audio session ID).
 * Mirrors Voice's approach of accessing the player from the service process.
 */
internal object ExoPlayerInstance {
    var player: ExoPlayer? = null
}
