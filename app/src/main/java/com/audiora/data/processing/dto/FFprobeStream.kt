package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's stream JSON output format.
 * Maps each entry in the "streams" array from FFprobe JSON output.
 */
data class FFprobeStream(
    val index: Int,
    val codecName: String?,
    val codecLongName: String?,
    val codecType: String?,
    val codecTagString: String?,
    val codecTag: String?,
    val sampleRate: String?,
    val channels: Int?,
    val channelLayout: String?,
    val bitRate: String?,
    val maxBitRate: String?,
    val duration: String?,
    val durationTs: Long?,
)
