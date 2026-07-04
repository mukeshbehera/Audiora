package com.audiora.feature.player

import android.media.audiofx.LoudnessEnhancer
import timber.log.Timber

/**
 * Wraps Android's LoudnessEnhancer to provide volume gain/boost.
 * Mirrors Voice's VolumeGainSetter + VolumeGain pattern.
 * Max gain is 9.0 dB (Voice's VolumeGain.MAX_GAIN).
 */
class VolumeGain {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSession: Int? = null
    private var currentGain: Float = 0f

    fun setGain(gainDb: Float, audioSessionId: Int) {
        currentGain = gainDb.coerceIn(0f, MAX_GAIN_DB)
        currentAudioSession = audioSessionId

        if (gainDb <= 0f) {
            reset()
            return
        }

        val enhancer = loudnessEnhancer
        if (enhancer != null && currentAudioSession == audioSessionId) {
            // Reuse existing enhancer — just update gain
            applyGain(enhancer, gainDb)
        } else {
            // Create new enhancer for this audio session
            createEnhancer(audioSessionId, gainDb)
        }
    }

    fun reset() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        currentGain = 0f
    }

    private fun createEnhancer(audioSessionId: Int, gainDb: Float) {
        reset()
        try {
            LoudnessEnhancer(audioSessionId).apply {
                enabled = true
                applyGain(this, gainDb)
                loudnessEnhancer = this
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to create LoudnessEnhancer")
        }
    }

    private fun applyGain(enhancer: LoudnessEnhancer, gainDb: Float) {
        try {
            enhancer.setTargetGain((gainDb * 100).toInt()) // dB → millibel
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to set LoudnessEnhancer gain")
        }
    }

    companion object {
        const val MAX_GAIN_DB = 9f
    }
}
