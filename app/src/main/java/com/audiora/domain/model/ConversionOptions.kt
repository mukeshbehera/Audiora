package com.audiora.domain.model

data class ConversionOptions(
    val bitRate: Int = 128000,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val codec: String = "aac",
    val includeChapters: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeCover: Boolean = true,
) {
    companion object {
        val DEFAULT = ConversionOptions()
        val HIGH_QUALITY = ConversionOptions(bitRate = 192000, sampleRate = 48000)
        val VOICE = ConversionOptions(bitRate = 64000, sampleRate = 22050, channelCount = 1)
    }
}
