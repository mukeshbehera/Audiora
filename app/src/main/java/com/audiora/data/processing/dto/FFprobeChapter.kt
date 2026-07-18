package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's chapter JSON output format.
 * Each chapter in the "chapters" array of FFprobe JSON output.
 */
data class FFprobeChapter(
    val id: Int,
    val timeBase: String?,
    val start: Long,
    val end: Long,
    val startTime: String?,
    val endTime: String?,
    val title: String?,
)
