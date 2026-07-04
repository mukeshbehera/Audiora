package com.audiora.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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

        mediaSession = MediaSession.Builder(this, ExoPlayerInstance.player!!)
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
