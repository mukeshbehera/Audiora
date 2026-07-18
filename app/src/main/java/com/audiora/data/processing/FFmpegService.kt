package com.audiora.data.processing

import com.audiora.data.processing.command.FFmpegCommandBuilder
import com.audiora.data.processing.command.FFprobeCommandBuilder
import com.audiora.data.processing.dto.FFmpegResult
import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.data.processing.dto.FFprobeStream
import com.audiora.data.processing.executor.ProcessExecutor
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.data.processing.parser.ProgressParser
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates all FFmpeg and FFprobe workflows.
 *
 * Acts as the single entry point for all media processing operations.
 * Delegates to specialized components for command building, execution, and parsing.
 * Does NOT construct commands or execute processes directly.
 */
class FFmpegService(
    private val binaryManager: FfmpegBinaryManager,
    private val processExecutor: ProcessExecutor,
    private val tempFileManager: TempFileManager,
    private val ffprobeJsonParser: FFprobeJsonParser,
    private val progressParser: ProgressParser,
) {
    private var cachedFfmpegBuilder: FFmpegCommandBuilder? = null
    private var cachedFfprobeBuilder: FFprobeCommandBuilder? = null

    private suspend fun getFfmpegBuilder(): FFmpegCommandBuilder {
        val cached = cachedFfmpegBuilder
        if (cached != null) return cached
        val paths = binaryManager.ensureInitialized()
        return FFmpegCommandBuilder(paths.ffmpegPath).also { cachedFfmpegBuilder = it }
    }

    private suspend fun getFfprobeBuilder(): FFprobeCommandBuilder {
        val cached = cachedFfprobeBuilder
        if (cached != null) return cached
        val paths = binaryManager.ensureInitialized()
        return FFprobeCommandBuilder(paths.ffprobePath).also { cachedFfprobeBuilder = it }
    }

    // ─── FFprobe: Reading ──────────────────────────────────────────────

    suspend fun readChapters(filePath: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readChapters(filePath)
            val result = processExecutor.execute(command, ProcessExecutor.ExecutionConfig(useShell = true))
            val json = result.getOrNull() ?: return@withContext emptyList()
            val parseResult = ffprobeJsonParser.parseChapters(json)
            val ffprobeChapters = parseResult.getOrNull() ?: return@withContext emptyList()
            convertChapters(ffprobeChapters)
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readChapters failed for $filePath")
            emptyList()
        }
    }

    suspend fun readFormat(filePath: String): FFprobeFormat? = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readFormat(filePath)
            val result = processExecutor.execute(command, ProcessExecutor.ExecutionConfig(useShell = true))
            val json = result.getOrNull() ?: return@withContext null
            ffprobeJsonParser.parseFormat(json).getOrNull()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readFormat failed for $filePath")
            null
        }
    }

    suspend fun readStreams(filePath: String): List<FFprobeStream> = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readStreams(filePath)
            val result = processExecutor.execute(command, ProcessExecutor.ExecutionConfig(useShell = true))
            val json = result.getOrNull() ?: return@withContext emptyList()
            ffprobeJsonParser.parseStreams(json).getOrNull() ?: emptyList()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readStreams failed for $filePath")
            emptyList()
        }
    }

    suspend fun readAllInfo(filePath: String): FFprobeJsonParser.AllInfo? = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readAll(filePath)
            val result = processExecutor.execute(command, ProcessExecutor.ExecutionConfig(useShell = true))
            val json = result.getOrNull() ?: return@withContext null
            ffprobeJsonParser.parseAll(json).getOrNull()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readAllInfo failed for $filePath")
            null
        }
    }

    // ─── FFmpeg: Writing ───────────────────────────────────────────────

    suspend fun createM4B(
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var coverFile: java.io.File? = null
        try {
            // Prepare chapters file
            if (chapters != null) {
                chaptersFile = tempFileManager.createChaptersFile(chapters)
            }

            // Prepare cover file
            if (coverData != null) {
                coverFile = tempFileManager.createCoverFile(coverData)
            }

            val command = getFfmpegBuilder().createM4B(
                inputs = inputFiles,
                outputPath = outputPath,
                options = options,
                metadata = metadata,
                coverPath = coverFile?.absolutePath,
                chaptersFilePath = chaptersFile?.absolutePath,
            )

            processExecutor.execute(
                command = command,
                config = ProcessExecutor.ExecutionConfig(useShell = true),
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "createM4B failed")
            FFmpegResult.error(-3, "createM4B failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, coverFile))
        }
    }

    suspend fun addChapters(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            val command = getFfmpegBuilder().addChapters(
                inputPath = filePath,
                outputPath = outputFile.absolutePath,
                chaptersFilePath = chaptersFile.absolutePath,
            )

            val result = processExecutor.execute(
                command = command,
                config = ProcessExecutor.ExecutionConfig(useShell = true),
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )

            if (result.isSuccess) {
                // Copy output file back to original location
                try {
                    java.io.File(filePath).outputStream().use { out ->
                        outputFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                } catch (e: Exception) {
                    Timber.tag("FFMPEG").e(e, "Failed to copy output back to $filePath")
                    return@withContext FFmpegResult.error(-4, "Failed to write result back to file: ${e.message}")
                }
            }
            result
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "addChapters failed")
            FFmpegResult.error(-3, "addChapters failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    suspend fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        try {
            val command = getFfmpegBuilder().mergeAudio(
                inputs = inputs,
                outputPath = outputPath,
                options = options,
            )
            processExecutor.execute(
                command = command,
                config = ProcessExecutor.ExecutionConfig(useShell = true),
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "mergeAudio failed")
            FFmpegResult.error(-3, "mergeAudio failed: ${e.message}")
        }
    }

    suspend fun exportAudiobook(
        inputPath: String,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        try {
            val command = getFfmpegBuilder().exportAudio(
                inputPath = inputPath,
                outputPath = outputPath,
                options = options,
            )
            processExecutor.execute(
                command = command,
                config = ProcessExecutor.ExecutionConfig(useShell = true),
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "exportAudiobook failed")
            FFmpegResult.error(-3, "exportAudiobook failed: ${e.message}")
        }
    }

    suspend fun replaceChaptersInFile(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            // Remove old chapters
            val removeCommand = getFfmpegBuilder().removeChapters(
                inputPath = filePath,
                outputPath = outputFile.absolutePath,
            )
            val removeResult = processExecutor.execute(removeCommand, ProcessExecutor.ExecutionConfig(useShell = true))
            if (removeResult.isError) return@withContext removeResult

            // Now add new chapters to the un-chaptered copy
            val intermediateFile = outputFile.absolutePath
            val addCommand = getFfmpegBuilder().addChapters(
                inputPath = intermediateFile,
                outputPath = filePath + ".tmp",
                chaptersFilePath = chaptersFile.absolutePath,
            )
            val addResult = processExecutor.execute(
                command = addCommand,
                config = ProcessExecutor.ExecutionConfig(useShell = true),
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )

            if (addResult.isSuccess) {
                try {
                    val tempOutput = java.io.File(filePath + ".tmp")
                    tempOutput.renameTo(java.io.File(filePath))
                } catch (e: Exception) {
                    Timber.tag("FFMPEG").e(e, "Failed to replace file")
                    return@withContext FFmpegResult.error(-4, "Failed to replace file: ${e.message}")
                }
            }
            addResult
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "replaceChaptersInFile failed")
            FFmpegResult.error(-3, "replaceChaptersInFile failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private fun convertChapters(ffprobeChapters: List<FFprobeChapter>): List<Chapter> {
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

    private fun parseTimeToMs(timeStr: String?): Long? {
        if (timeStr == null) return null
        return (timeStr.toDoubleOrNull()?.let { (it * 1000).toLong() })
    }
}
