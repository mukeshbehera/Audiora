package com.audiora.feature.converter

/**
 * Shared state for the transcode foreground service → UI communication.
 * Emitted by TranscodeService via AudioraApplication.transcodeState StateFlow.
 */
sealed class TranscodeState {
    data object Idle : TranscodeState()

    data class Processing(
        val progress: Float,
        val status: String
    ) : TranscodeState()

    data class Completed(
        val bookId: Int,
        val title: String
    ) : TranscodeState()

    data class Failed(
        val error: String
    ) : TranscodeState()
}
