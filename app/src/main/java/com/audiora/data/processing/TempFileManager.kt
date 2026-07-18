package com.audiora.data.processing

import android.content.Context
import com.audiora.domain.model.Chapter
import timber.log.Timber
import java.io.File

/**
 * Manages temporary file creation and cleanup for FFmpeg operations.
 *
 * Creates temp files in the app's cache directory with descriptive prefixes.
 * Supports automatic cleanup of registered files, including on failure.
 */
class TempFileManager(
    private val context: Context,
) {
    private val registeredFiles = mutableSetOf<File>()

    /**
     * Create a metadata file for FFmpeg's -metadata option.
     * Format: "key=value\n" per line.
     */
    suspend fun createMetadataFile(metadata: Map<String, String>): File {
        val file = createTempFile("metadata_", ".txt")
        file.writeText(metadata.entries.joinToString("\n") { (key, value) ->
            "$key=$value"
        })
        return file
    }

    /**
     * Create a chapters file in FFmpeg-compatible metadata format.
     *
     * FFmpeg chapter metadata format:
     * ;FFMETADATA1
     * [CHAPTER]
     * TIMEBASE=1/1000
     * START=0
     * END=123456
     * title=Chapter Title
     */
    suspend fun createChaptersFile(chapters: List<Chapter>): File {
        val file = createTempFile("chapters_", ".txt")
        file.writeText(buildString {
            appendLine(";FFMETADATA1")
            chapters.forEachIndexed { index, chapter ->
                appendLine()
                appendLine("[CHAPTER]")
                appendLine("TIMEBASE=1/1000")
                appendLine("START=${chapter.startMs}")
                appendLine("END=${chapter.endMs}")
                appendLine("title=${chapter.title}")
            }
        })
        return file
    }

    /**
     * Create a temporary output file with the given extension.
     */
    suspend fun createOutputFile(extension: String): File {
        val prefix = "output_${System.nanoTime()}_"
        return createTempFile(prefix, ".$extension")
    }

    /**
     * Write cover image bytes to a temp file for FFmpeg input.
     */
    suspend fun createCoverFile(coverData: ByteArray, extension: String = "jpg"): File {
        val file = createTempFile("cover_", ".$extension")
        file.writeBytes(coverData)
        return file
    }

    /**
     * Delete the specified temp files.
     */
    fun cleanup(files: List<File>) {
        files.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Timber.tag("FFMPEG").d("Cleaned up temp file: ${file.name}")
                }
            } catch (e: Exception) {
                Timber.tag("FFMPEG").w("Failed to delete temp file: ${file.name}")
            }
        }
        registeredFiles.removeAll(files)
    }

    /**
     * Register a file for cleanup on cleanupAll().
     */
    fun registerForCleanup(file: File) {
        registeredFiles.add(file)
    }

    /**
     * Clean up ALL registered temp files. Call on processing completion or failure.
     */
    fun cleanupAll() {
        cleanup(registeredFiles.toList())
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val file = File.createTempFile(prefix, suffix, context.cacheDir)
        registerForCleanup(file)
        return file
    }
}
