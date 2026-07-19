package com.audiora.data.processing

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.data.processing.dto.FFprobeStream
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.data.processing.parser.ProgressParser
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates all FFmpeg and FFprobe workflows using ffmpeg-kit (JNI native libraries).
 *
 * ffmpeg-kit loads FFmpeg as .so shared libraries via System.loadLibrary() — the same
 * approach used by VLC Android. No subprocess execution, no setExecutable(true), no
 * noexec mount issues.
 *
 * API reference: https://github.com/arthenica/ffmpeg-kit
 */
class FFmpegService(
    private val tempFileManager: TempFileManager,
    private val ffprobeJsonParser: FFprobeJsonParser,
    private val progressParser: ProgressParser,
) {
    companion object {
        private const val TAG = "FFMPEG"
    }

    // ─── FFprobe: Reading ──────────────────────────────────────────────

    suspend fun readChapters(filePath: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val command = listOf("-v", "quiet", "-print_format", "json", "-show_chapters", filePath)
            val session = FFprobeKit.execute(command.toTypedArray())
            val output = session.output
            if (output.isNullOrBlank()) return@withContext emptyList()

            val parseResult = ffprobeJsonParser.parseChapters(output)
            val ffprobeChapters = parseResult.getOrNull() ?: return@withContext emptyList()
            convertChapters(ffprobeChapters)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readChapters failed for $filePath")
            emptyList()
        }
    }

    suspend fun readFormat(filePath: String): FFprobeFormat? = withContext(Dispatchers.IO) {
        try {
            val command = listOf("-v", "quiet", "-print_format", "json", "-show_format", filePath)
            val session = FFprobeKit.execute(command.toTypedArray())
            val output = session.output
            if (output.isNullOrBlank()) return@withContext null
            ffprobeJsonParser.parseFormat(output).getOrNull()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readFormat failed for $filePath")
            null
        }
    }

    suspend fun readStreams(filePath: String): List<FFprobeStream> = withContext(Dispatchers.IO) {
        try {
            val command = listOf("-v", "quiet", "-print_format", "json", "-show_streams", filePath)
            val session = FFprobeKit.execute(command.toTypedArray())
            val output = session.output
            if (output.isNullOrBlank()) return@withContext emptyList()
            ffprobeJsonParser.parseStreams(output).getOrNull() ?: emptyList()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readStreams failed for $filePath")
            emptyList()
        }
    }

    suspend fun readAllInfo(filePath: String): FFprobeJsonParser.AllInfo? = withContext(Dispatchers.IO) {
        try {
            val command = listOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", "-show_chapters", filePath)
            val session = FFprobeKit.execute(command.toTypedArray())
            val output = session.output
            if (output.isNullOrBlank()) return@withContext null
            ffprobeJsonParser.parseAll(output).getOrNull()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readAllInfo failed for $filePath")
            null
        }
    }

    // ─── FFmpeg: Writing ───────────────────────────────────────────────

    private suspend fun executeFFmpeg(
        arguments: List<String>,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean {
        return try {
            Timber.tag(TAG).d("Executing ffmpeg: ${arguments.joinToString(" ")}")

            // ffmpeg-kit's execute() is synchronous and blocking. We wrap in withContext(IO).
            val session = FFmpegKit.execute(arguments.toTypedArray())

            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                Timber.tag(TAG).d("FFmpeg succeeded")
                true
            } else {
                val logs = session.allLogs?.joinToString("\n") { it.message } ?: ""
                val failLogs = logs.take(500)
                Timber.tag(TAG).w("FFmpeg failed (rc=$returnCode): $failLogs")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "FFmpeg execution error")
            false
        }
    }

    suspend fun createM4B(
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var coverFile: java.io.File? = null
        try {
            if (chapters != null) {
                chaptersFile = tempFileManager.createChaptersFile(chapters)
            }
            if (coverData != null) {
                coverFile = tempFileManager.createCoverFile(coverData)
            }

            val arguments = buildFFmpegArgs(
                inputs = inputFiles,
                outputPath = outputPath,
                options = options,
                metadata = metadata,
                coverPath = coverFile?.absolutePath,
                chaptersFilePath = chaptersFile?.absolutePath,
            )

            executeFFmpeg(arguments, onProgress)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "createM4B failed")
            false
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, coverFile))
        }
    }

    suspend fun addChapters(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            val args = mutableListOf("-y", "-i", filePath, "-i", chaptersFile.absolutePath,
                "-map_metadata", "1", "-codec", "copy", outputFile.absolutePath)

            val ok = executeFFmpeg(args, onProgress)
            if (ok) {
                java.io.File(filePath).outputStream().use { out ->
                    outputFile.inputStream().use { inp -> inp.copyTo(out) }
                }
            }
            ok
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "addChapters failed")
            false
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    suspend fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val args = mutableListOf<String>()
            args.add("-y")
            inputs.forEach { args.addAll(listOf("-i", it)) }
            val concatInputs = inputs.indices.joinToString("") { "[$it:a]" }
            args.addAll(listOf("-filter_complex", "${concatInputs}concat=n=${inputs.size}:v=0:a=1[out]",
                "-map", "[out]", "-c:a", options.codec, "-b:a", "${options.bitRate}",
                "-ar", "${options.sampleRate}", "-ac", "${options.channelCount}",
                "-movflags", "+faststart", outputPath))

            executeFFmpeg(args, onProgress)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "mergeAudio failed")
            false
        }
    }

    suspend fun exportAudiobook(
        inputPath: String,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val args = mutableListOf("-y", "-i", inputPath)
            if (!options.includeMetadata) { args.addAll(listOf("-map_metadata", "-1")) }
            if (!options.includeChapters) { args.addAll(listOf("-map_chapters", "-1")) }
            args.addAll(listOf("-c:a", options.outputFormat, "-b:a", "${options.bitRate}",
                "-ar", "${options.sampleRate}", "-ac", "${options.channelCount}", outputPath))

            executeFFmpeg(args, onProgress)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "exportAudiobook failed")
            false
        }
    }

    suspend fun replaceChaptersInFile(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            // Remove old chapters
            val removeArgs = listOf("-y", "-i", filePath, "-map_chapters", "-1", "-codec", "copy", outputFile.absolutePath)
            val removeOk = executeFFmpeg(removeArgs)
            if (!removeOk) return@withContext false

            // Add new chapters
            val intermediateFile = outputFile.absolutePath
            val tmpFile = filePath + ".tmp"
            val addArgs = listOf("-y", "-i", intermediateFile, "-i", chaptersFile.absolutePath,
                "-map_metadata", "1", "-codec", "copy", tmpFile)
            val addOk = executeFFmpeg(addArgs, onProgress)
            if (addOk) {
                java.io.File(tmpFile).renameTo(java.io.File(filePath))
            }
            addOk
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "replaceChaptersInFile failed")
            false
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    // ─── Argument Builder ──────────────────────────────────────────────

    private fun buildFFmpegArgs(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: Map<String, String>,
        coverPath: String?,
        chaptersFilePath: String?,
    ): List<String> {
        val args = mutableListOf<String>()
        args.add("-y")

        inputs.forEach { input ->
            args.add("-i")
            args.add(input)
        }

        metadata.forEach { (key, value) ->
            args.add("-metadata")
            args.add("$key=$value")
        }

        if (coverPath != null) {
            args.addAll(listOf("-i", coverPath, "-map", "0:a", "-map", "1:v",
                "-disposition:v:0", "attached_pic"))
        }

        if (chaptersFilePath != null) {
            args.addAll(listOf("-i", chaptersFilePath, "-map_metadata", "1"))
        }

        args.addAll(listOf("-c:a", options.codec, "-b:a", "${options.bitRate}",
            "-ar", "${options.sampleRate}", "-ac", "${options.channelCount}",
            "-movflags", "+faststart", "-f", "mp4", outputPath))

        return args
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private fun convertChapters(ffprobeChapters: List<com.audiora.data.processing.dto.FFprobeChapter>): List<Chapter> {
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
