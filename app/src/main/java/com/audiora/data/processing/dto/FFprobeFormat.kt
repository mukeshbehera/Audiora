package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's format JSON output.
 * Maps the "format" object from FFprobe JSON output.
 */
data class FFprobeFormat(
    val filename: String?,
    val nbStreams: Int?,
    val nbPrograms: Int?,
    val formatName: String?,
    val formatLongName: String?,
    val startTime: String?,
    val duration: String?,
    val size: String?,
    val bitRate: String?,
    val probeScore: Int?,
)
