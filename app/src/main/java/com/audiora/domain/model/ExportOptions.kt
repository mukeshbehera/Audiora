package com.audiora.domain.model

data class ExportOptions(
    val outputFormat: String = "mp3",
    val bitRate: Int = 128000,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val includeChapters: Boolean = false,
    val includeMetadata: Boolean = true,
    val includeCover: Boolean = false,
) {
    companion object {
        val DEFAULT = ExportOptions()
        val MP3_HIGH = ExportOptions(outputFormat = "mp3", bitRate = 320000)
        val FLAC = ExportOptions(outputFormat = "flac")
    }
}
