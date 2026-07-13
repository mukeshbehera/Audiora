package com.audiora.domain.model

data class Audiobook(
    val id: Int = 0,
    val filePath: String,
    val title: String,
    val author: String,
    val narrator: String,
    val publisher: String,
    val genre: String,
    val year: String,
    val description: String,
    val durationMs: Long,
    val currentPositionMs: Long = 0,
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val folderUri: String? = null,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val language: String = "",
    val copyright: String = "",
    val chaptersJson: String? = null,
    val skipSilence: Boolean = false,
    val volumeGain: Float = 0f,
    // Per-book playback speed. 0f means "use global default".
    val playbackSpeed: Float = 0f,
    // Timestamp of last playback start (epoch millis). 0L means never played.
    val lastPlayedAt: Long = 0L,
) {
    val progress: Float
        get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
}
