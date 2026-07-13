package com.audiora.feature.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Disables video, text, metadata, camera motion, and miscellaneous renderers.
 *
 * Audiobooks only need audio decoding — allocating the other renderers wastes
 * decoder resources. Matches Voice's [voice.core.playback.player.OnlyAudioRenderersFactory].
 */
class OnlyAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        // No-op: audiobooks have no video track
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        // No-op: audiobooks have no text/subtitle track
    }

    override fun buildMetadataRenderers(
        context: Context,
        output: MetadataOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        // No-op: audiobooks don't need metadata track rendering
    }

    override fun buildCameraMotionRenderers(
        context: Context,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        // No-op: audiobooks have no camera motion
    }

    override fun buildMiscellaneousRenderers(
        context: Context,
        eventHandler: Handler,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        // No-op: audiobooks need no miscellaneous renderers
    }
}
