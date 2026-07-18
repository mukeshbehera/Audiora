package com.audiora.data.processing.parser

import timber.log.Timber

/**
 * Parsed progress information from FFmpeg stderr output.
 */
data class ProgressEvent(
    val percentage: Float?,
    val speed: String?,
    val bitrate: String?,
    val timeMs: Long?,
    val frame: Int?,
)

/**
 * Parses FFmpeg's stderr progress lines to extract progress events.
 *
 * FFmpeg progress line format:
 * frame=  123 fps= 12 q=28.0 size=    1024kB time=00:01:23.45 bitrate= 128.0kbits/s speed=1.2x
 */
class ProgressParser {

    fun parse(line: String, totalDurationMs: Long): ProgressEvent? {
        if (!line.contains("time=")) return null

        return try {
            val timeMs = parseTime(line)
            val percentage = if (timeMs != null && totalDurationMs > 0) {
                (timeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
            } else null

            ProgressEvent(
                percentage = percentage,
                speed = parseField(line, "speed="),
                bitrate = parseField(line, "bitrate="),
                timeMs = timeMs,
                frame = parseField(line, "frame=")?.trim()?.toIntOrNull(),
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").d("Failed to parse progress line: $line")
            null
        }
    }

    /**
     * Parses time string from FFmpeg progress line.
     * Format: HH:MM:SS.mmm or MM:SS.mmm
     */
    private fun parseTime(line: String): Long? {
        val timeStr = parseField(line, "time=") ?: return null
        // Handle format like "00:01:23.45" or "01:23.45"
        val parts = timeStr.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].replace(",", ".").toDouble()
                ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
            }
            2 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].replace(",", ".").toDouble()
                ((minutes * 60 + seconds) * 1000).toLong()
            }
            else -> null
        }
    }

    /**
     * Extracts a field value from a key=value line.
     * e.g. parseField("speed=1.2x bitrate=128k", "speed=") => "1.2x"
     */
    private fun parseField(line: String, key: String): String? {
        val startIndex = line.indexOf(key)
        if (startIndex < 0) return null
        val valueStart = startIndex + key.length
        // Read until next space or end of string
        val endIndex = line.indexOf(' ', valueStart)
        return if (endIndex < 0) line.substring(valueStart) else line.substring(valueStart, endIndex)
    }
}
