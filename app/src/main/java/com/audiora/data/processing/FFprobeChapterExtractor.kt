package com.audiora.data.processing

import android.content.Context
import android.net.Uri
import com.audiora.data.local.ChapterExtractor
import com.audiora.data.processing.command.FFprobeCommandBuilder
import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.executor.ProcessExecutor
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ChapterExtractor implementation using FFprobe for chapter detection.
 *
 * Falls back gracefully if FFprobe is unavailable or the file has no chapters.
 */
class FFprobeChapterExtractor(
    private val binaryManager: FfmpegBinaryManager,
    private val processExecutor: ProcessExecutor,
    private val ffprobeJsonParser: FFprobeJsonParser,
) : ChapterExtractor {

    override suspend fun extract(
        context: Context,
        uri: Uri,
        totalDurationMs: Long,
    ): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val paths = binaryManager.ensureInitialized()
            val commandBuilder = FFprobeCommandBuilder(paths.ffprobePath)
            val filePath = uri.toString()

            val command = commandBuilder.readChapters(filePath)
            val result = processExecutor.execute(command)

            if (result.isError) {
                Timber.tag("FFMPEG").w("FFprobe chapter extraction failed: ${(result as com.audiora.data.processing.dto.FFmpegResult.Error).message}")
                return@withContext emptyList()
            }

            val output = result.getOrNull() ?: return@withContext emptyList()
            val parseResult = ffprobeJsonParser.parseChapters(output)

            if (parseResult.isError) {
                Timber.tag("FFMPEG").w("Failed to parse FFprobe chapters JSON")
                return@withContext emptyList()
            }

            val ffprobeChapters = parseResult.getOrNull() ?: return@withContext emptyList()
            if (ffprobeChapters.isEmpty()) return@withContext emptyList()

            convertToDomainChapters(ffprobeChapters, totalDurationMs)
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "FFprobeChapterExtractor failed")
            emptyList()
        }
    }

    private fun convertToDomainChapters(
        ffprobeChapters: List<FFprobeChapter>,
        totalDurationMs: Long,
    ): List<Chapter> {
        val sorted = ffprobeChapters.sortedBy { it.id }
        return sorted.mapIndexed { index, ch ->
            val startMs = parseTimeToMs(ch.startTime) ?: ch.start
            val endMs = parseTimeToMs(ch.endTime) ?: ch.end

            Chapter(
                title = ch.title ?: "Chapter ${index + 1}",
                startMs = startMs,
                endMs = endMs,
                durationMs = (endMs - startMs).coerceAtLeast(1000L),
                index = index,
            )
        }
    }

    /**
     * Parse FFprobe's time string (seconds as decimal) to milliseconds.
     */
    private fun parseTimeToMs(timeStr: String?): Long? {
        if (timeStr == null) return null
        return (timeStr.toDoubleOrNull()?.let { (it * 1000).toLong() })
    }
}
