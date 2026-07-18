package com.audiora.data.processing.command

import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions

/**
 * Builds FFmpeg commands as List<String> for various audiobook processing operations.
 *
 * Every method returns a clean List<String> — first element is the FFmpeg binary path,
 * remaining elements are arguments. Never uses string concatenation for arguments.
 */
class FFmpegCommandBuilder(
    private val ffmpegPath: String,
) {
    /**
     * Create an M4B file from one or more input audio files.
     * Optionally embeds metadata, cover art, and chapters.
     */
    fun createM4B(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverPath: String? = null,
        chaptersFilePath: String? = null,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y") // Overwrite output

        // Input files
        inputs.forEach { input ->
            add("-i")
            add(input)
        }

        // Metadata
        metadata.forEach { (key, value) ->
            add("-metadata")
            add("$key=$value")
        }

        // Cover art (map_cover: use first image input as cover)
        if (coverPath != null) {
            add("-i")
            add(coverPath)
            add("-map")
            add("0:a")
            add("-map")
            add("1:v")
            add("-disposition:v:0")
            add("attached_pic")
        }

        // Chapters: if chapters file provided, use -f ffmetadata
        if (chaptersFilePath != null) {
            add("-i")
            add(chaptersFilePath)
            add("-map_metadata")
            add("1")
        }

        // Audio codec settings
        add("-c:a")
        add(options.codec)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        // Output format and movflags for fast start
        add("-movflags")
        add("+faststart")
        add("-f")
        add("mp4")

        // Output file
        add(outputPath)
    }

    /**
     * Add chapters to an existing audio file using FFmpeg metadata.
     * Creates a new file with chapters embedded in the container.
     */
    fun addChapters(
        inputPath: String,
        outputPath: String,
        chaptersFilePath: String,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-i")
        add(chaptersFilePath)
        add("-map_metadata")
        add("1")
        add("-codec")
        add("copy") // No re-encoding — just metadata mux
        add(outputPath)
    }

    /**
     * Remove all chapters from an audio file by writing metadata without chapter info.
     */
    fun removeChapters(
        inputPath: String,
        outputPath: String,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-map_chapters")
        add("-1") // Remove all chapters
        add("-codec")
        add("copy")
        add(outputPath)
    }

    /**
     * Merge multiple audio files into a single file with chapter markers.
     *
     * Uses the concat filter for merging multiple audio streams.
     */
    fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")

        // Use concat protocol for multiple inputs
        inputs.forEach { input ->
            add("-i")
            add(input)
        }

        add("-filter_complex")
        val concatInputs = inputs.indices.joinToString("") { "[$it:a]" }
        add("${concatInputs}concat=n=${inputs.size}:v=0:a=1[out]")
        add("-map")
        add("[out]")

        add("-c:a")
        add(options.codec)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        add("-movflags")
        add("+faststart")
        add(outputPath)
    }

    /**
     * Export an audiobook to a different format.
     */
    fun exportAudio(
        inputPath: String,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)

        if (!options.includeMetadata) {
            add("-map_metadata")
            add("-1")
        }
        if (!options.includeChapters) {
            add("-map_chapters")
            add("-1")
        }

        add("-c:a")
        add(options.outputFormat)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        if (options.outputFormat == "mp3") {
            add("-id3v2_version")
            add("3")
        }

        add(outputPath)
    }

    /**
     * Split an audiobook into segments at the specified timestamps.
     */
    fun splitAudio(
        inputPath: String,
        outputPathPattern: String,
        splitTimes: List<Long>, // split points in seconds
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-c")
        add("copy") // Stream copy — no re-encode
        add("-f")
        add("segment")
        add("-segment_times")
        add(splitTimes.joinToString(","))
        add("-reset_timestamps")
        add("1")
        add(outputPathPattern)
    }
}
