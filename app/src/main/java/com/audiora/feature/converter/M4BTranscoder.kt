package com.audiora.feature.converter

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import com.audiora.domain.model.Chapter
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object M4BTranscoder {

    private const val TARGET_SAMPLE_RATE = 44100
    private const val TARGET_CHANNELS = 2
    private const val TARGET_BITRATE = 128000

    interface ProgressListener {
        fun onProgress(percentage: Float)
    }

    /**
     * Transcodes multiple input audio files into a single M4B file using FFmpeg.
     * Handles SAF content:// URIs, concatenation, AAC encoding, metadata embedding,
     * and chapter embedding in a single pass.
     */
    suspend fun transcode(
        context: Context,
        inputUris: List<Uri>,
        outputFile: File,
        title: String,
        author: String,
        narrator: String,
        publisher: String,
        genre: String,
        year: String,
        description: String,
        chapters: List<Chapter>,
        coverSeed: String? = null,
        listener: ProgressListener
    ): Boolean {
        if (inputUris.isEmpty()) return false

        // Temp files to track for cleanup
        val tempFiles = mutableListOf<File>()
        var concatFileList: File? = null
        var metadataFile: File? = null
        var coverJpeg: File? = null

        return try {
            // 1. Copy SAF URIs to temp files (FFmpeg C code cannot read content:// URIs)
            val inputFiles = inputUris.mapIndexed { index, uri ->
                val ext = getExtension(context, uri)
                val tempFile = File(
                    context.cacheDir,
                    "ffmpeg_input_${index}_${System.nanoTime()}.$ext"
                )
                copySafToTemp(context, uri, tempFile)
                tempFiles.add(tempFile)
                tempFile
            }

            // 2. Calculate total estimated duration for progress reporting
            val totalDurationMs = calculateTotalDuration(context, inputUris)
            val effectiveDurationMs = if (totalDurationMs > 0) totalDurationMs else 1L

            // 3. Generate concat demuxer file list
            concatFileList = File(context.cacheDir, "ffmpeg_concat_${System.nanoTime()}.txt")
            concatFileList.writeText(inputFiles.joinToString("\n") { "file '${it.absolutePath}'" })

            // 4. Generate FFMETADATA file with chapters and tags
            metadataFile = File(context.cacheDir, "ffmpeg_metadata_${System.nanoTime()}.txt")
            metadataFile.writeText(buildMetadataString(
                title, author, publisher, genre, year, description, chapters
            ))

            // 5. (Optional) Generate cover art JPEG
            if (!coverSeed.isNullOrBlank()) {
                coverJpeg = File(context.cacheDir, "ffmpeg_cover_${System.nanoTime()}.jpg")
                generateCoverJpeg(coverSeed, coverJpeg)
            }

            // 6. Build the FFmpeg command
            val cmd = buildCommand(
                concatFileList!!, metadataFile, coverJpeg, outputFile
            )

            Timber.d("FFmpeg command: $cmd")

            // 7. Execute FFmpeg with progress reporting
            suspendCancellableCoroutine<Boolean> { continuation ->
                val session = FFmpegKit.executeAsync(
                    cmd,
                    { session ->
                        val rc = session.returnCode
                        if (ReturnCode.isSuccess(rc)) {
                            Timber.d("FFmpeg transcoding completed successfully")
                            listener.onProgress(1f)
                            continuation.resume(true, onCancellation = null)
                        } else if (ReturnCode.isCancel(rc)) {
                            Timber.d("FFmpeg transcoding was cancelled")
                            continuation.resume(false, onCancellation = null)
                        } else {
                            val error = session.failStackTrace ?: "Unknown FFmpeg error"
                            Timber.e("FFmpeg transcoding failed with code ${rc.value}: $error")
                            continuation.resumeWithException(IOException("FFmpeg failed: $error"))
                        }
                    },
                    LogCallback { log: Log ->
                        Timber.d("FFmpeg: ${log.message?.trimEnd()}")
                    },
                    StatisticsCallback { statistics: Statistics ->
                        val timeMs = statistics.time
                        val fraction = (timeMs.toFloat() / effectiveDurationMs).coerceIn(0f, 1f)
                        listener.onProgress(fraction)
                    }
                )

                continuation.invokeOnCancellation {
                    Timber.d("Cancelling FFmpeg session ${session.sessionId}")
                    session.cancel()
                }
            }

        } catch (e: Exception) {
            if (e is IOException && e.message?.contains("FFmpeg failed", true) == true) {
                Timber.e(e, "FFmpeg transcoding error")
            } else {
                Timber.e(e, "Error during transcoding preparation")
            }
            false
        } finally {
            // 8. Cleanup all temp files
            cleanupFiles(listOfNotNull(concatFileList, metadataFile, coverJpeg) + tempFiles)
        }
    }

    // ---- Private helpers ----

    private fun buildCommand(
        concatFile: File,
        metadataFile: File,
        coverFile: File?,
        output: File
    ): String {
        val sb = StringBuilder()
        sb.append("-f concat -safe 0 -i \"${concatFile.absolutePath}\" ")
        sb.append("-f ffmetadata -i \"${metadataFile.absolutePath}\" ")
        if (coverFile != null && coverFile.exists()) {
            sb.append("-i \"${coverFile.absolutePath}\" ")
            sb.append("-map 0:a -map 2:v ")
            sb.append("-disposition:v attached_pic -c:v mjpeg ")
        } else {
            sb.append("-map 0:a ")
        }
        sb.append("-c:a aac -b:a $TARGET_BITRATE ")
        sb.append("-ar $TARGET_SAMPLE_RATE -ac $TARGET_CHANNELS ")
        sb.append("-map_metadata 1 ")
        sb.append("-movflags +faststart ")
        sb.append("-y \"${output.absolutePath}\"")
        return sb.toString()
    }

    private fun buildMetadataString(
        title: String,
        author: String,
        publisher: String,
        genre: String,
        year: String,
        description: String,
        chapters: List<Chapter>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(";FFMETADATA1")

        if (title.isNotBlank()) sb.appendLine("title=$title")
        if (author.isNotBlank()) sb.appendLine("artist=$author")
        if (title.isNotBlank()) sb.appendLine("album=$title")
        if (publisher.isNotBlank()) sb.appendLine("publisher=$publisher")
        if (genre.isNotBlank()) sb.appendLine("genre=$genre")
        if (year.isNotBlank()) sb.appendLine("date=$year")
        if (description.isNotBlank()) sb.appendLine("comment=$description")

        // Append chapters
        for (ch in chapters) {
            sb.appendLine()
            sb.appendLine("[CHAPTER]")
            sb.appendLine("TIMEBASE=1/1000")
            sb.appendLine("START=${ch.startMs}")
            sb.appendLine("END=${ch.endMs}")
            sb.appendLine("title=${ch.title}")
        }

        return sb.toString()
    }

    private fun calculateTotalDuration(context: Context, uris: List<Uri>): Long {
        var total = 0L
        for (uri in uris) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                total += durationStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Timber.w("Could not read duration for $uri")
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        return total
    }

    private fun copySafToTemp(context: Context, uri: Uri, tempFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot open input stream for $uri")
    }

    private fun getExtension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        val name = uri.pathSegments?.lastOrNull() ?: ""
        return when {
            name.contains('.') -> name.substringAfterLast('.')
            mime.contains("mpeg") -> "mp3"
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("m4b") -> "m4a"
            mime.contains("aac") -> "aac"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") || mime.contains("opus") || mime.contains("vorbis") -> "ogg"
            mime.contains("flac") -> "flac"
            mime.contains("wma") -> "wma"
            else -> "mp3"
        }
    }

    private fun generateCoverJpeg(seed: String, outputFile: File) {
        val colors = coverGradientColors(seed) ?: return
        try {
            val width = 400
            val height = 400
            val bitmap = android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
            }

            // Extract ARGB components from Long-packed colors
            val r1 = (colors.first shr 16 and 0xFF).toInt()
            val g1 = (colors.first shr 8 and 0xFF).toInt()
            val b1 = (colors.first and 0xFF).toInt()
            val r2 = (colors.second shr 16 and 0xFF).toInt()
            val g2 = (colors.second shr 8 and 0xFF).toInt()
            val b2 = (colors.second and 0xFF).toInt()

            // Draw a simple two-color vertical gradient
            for (y in 0 until height) {
                val fraction = y.toFloat() / height
                val r = (r1 * (1 - fraction) + r2 * fraction).toInt()
                val g = (g1 * (1 - fraction) + g2 * fraction).toInt()
                val b = (b1 * (1 - fraction) + b2 * fraction).toInt()
                paint.color = android.graphics.Color.rgb(r, g, b)
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            }

            outputFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate cover JPEG for seed: $seed")
        }
    }

    private data class GradientPair(val first: Long, val second: Long)

    private fun coverGradientColors(seed: String): GradientPair? {
        // Colors are stored as ARGB Longs to avoid Int overflow on hex literals
        return when (seed.lowercase()) {
            "nebula" -> GradientPair(0xFF8E2DE2L, 0xFF4A00E0L)
            "horizon" -> GradientPair(0xFF00C6FFL, 0xFF0072FFL)
            "eternity" -> GradientPair(0xFFF12711L, 0xFFF5AF19L)
            "neon" -> GradientPair(0xFFF80759L, 0xFFBC4E9CL)
            "infinite" -> GradientPair(0xFF0F2027L, 0xFF203A43L)
            "cosmic" -> GradientPair(0xFF11998EL, 0xFF38EF7DL)
            else -> null
        }
    }

    private fun cleanupFiles(files: List<File>) {
        for (f in files) {
            try {
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Timber.w("Failed to delete temp file: ${f.absolutePath}")
            }
        }
    }

    /**
     * Embeds chapter markers into an existing M4B file using FFmpeg FFMETADATA.
     * Replaces existing chapters with the provided list.
     * Handles both local file paths and content:// URIs.
     * NOTE: To preserve existing metadata, this generates FFMETADATA that includes
     * chapter entries only. The caller should re-apply title/author/etc tags
     * afterward if they need to be preserved.
     */
    suspend fun embedChaptersInFile(
        context: Context,
        filePath: String,
        chapters: List<Chapter>
    ): Boolean {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            try {
                val isContentUri = isContentUri(filePath)
                val sourceFile: File
                val cleanupSource: (() -> Unit)?

                if (isContentUri) {
                    val uri = android.net.Uri.parse(filePath)
                    val tempInput = File(context.cacheDir, "ffmpeg_embed_input_${System.nanoTime()}.m4b")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempInput.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Cannot open input stream for $filePath")
                    sourceFile = tempInput
                    cleanupSource = { tempInput.delete() }
                } else {
                    sourceFile = File(filePath)
                    if (!sourceFile.exists()) throw IOException("File not found: $filePath")
                    cleanupSource = null
                }

                // Generate FFMETADATA with chapters, preserving existing metadata by first
                // extracting metadata from source file via FFprobe and combining it
                val existingMetadata = extractMetadataTags(context, sourceFile)
                val metadataStr = buildChaptersMetadataWithExisting(existingMetadata, chapters)
                val metadataFile = File(context.cacheDir, "ffmpeg_embed_meta_${System.nanoTime()}.txt")
                metadataFile.writeText(metadataStr)

                val outputFile = File(context.cacheDir, "ffmpeg_embed_out_${System.nanoTime()}.m4b")
                val command = "-i \"${sourceFile.absolutePath}\" -f ffmetadata -i \"${metadataFile.absolutePath}\" -map_metadata 1 -c copy -y \"${outputFile.absolutePath}\""

                val session = FFmpegKit.executeAsync(
                    command,
                    { session ->
                        try {
                            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                                if (isContentUri) {
                                    val uri = android.net.Uri.parse(filePath)
                                    context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                                        outputFile.inputStream().use { input -> input.copyTo(output) }
                                    }
                                } else {
                                    outputFile.copyTo(sourceFile, overwrite = true)
                                }
                                Timber.d("Chapters embedded successfully via FFmpeg in $filePath")
                                continuation.resume(true, onCancellation = null)
                            } else {
                                Timber.e("FFmpeg chapter embedding failed with code ${session.returnCode.value}")
                                continuation.resume(false, onCancellation = null)
                            }
                        } finally {
                            cleanupSource?.invoke()
                            metadataFile.delete()
                            outputFile.delete()
                        }
                    },
                    com.arthenica.ffmpegkit.LogCallback { log ->
                        Timber.d("FFmpeg embed: ${log.message?.trimEnd()}")
                    },
                    null, /* no statistics callback needed */
                    null  /* use default executor */
                )

                continuation.invokeOnCancellation {
                    session.cancel()
                    cleanupSource?.invoke()
                    metadataFile.delete()
                    outputFile.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error embedding chapters in file: $filePath")
                continuation.resume(false, onCancellation = null)
            }
        }
    }

    /**
     * Extracts existing metadata tags from the source file using MediaMetadataRetriever.
     * Returns a map of metadata keys to values.
     */
    private fun extractMetadataTags(context: Context, sourceFile: java.io.File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, android.net.Uri.fromFile(sourceFile))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val cdTrackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            retriever.release()
            if (!title.isNullOrBlank()) map["title"] = title
            if (!artist.isNullOrBlank()) map["artist"] = artist
            if (!album.isNullOrBlank()) map["album"] = album
            if (!composer.isNullOrBlank()) map["composer"] = composer
            if (!date.isNullOrBlank()) map["date"] = date
            if (!genre.isNullOrBlank()) map["genre"] = genre
            if (!year.isNullOrBlank()) map["date"] = year
            if (!cdTrackNumber.isNullOrBlank()) map["track"] = cdTrackNumber
        } catch (e: Exception) {
            Timber.w(e, "Could not extract metadata from source file")
        }
        return map
    }

    /**
     * Builds a FFMETADATA string that includes both existing metadata tags and chapters.
     * This prevents metadata loss when -map_metadata 1 overwrites with the FFMETADATA file.
     */
    private fun buildChaptersMetadataWithExisting(existingTags: Map<String, String>, chapters: List<Chapter>): String {
        val sb = StringBuilder()
        sb.appendLine(";FFMETADATA1")
        // Include existing metadata tags to prevent them from being lost
        for ((key, value) in existingTags) {
            sb.appendLine("$key=$value")
        }
        // Append chapters
        for (ch in chapters) {
            sb.appendLine()
            sb.appendLine("[CHAPTER]")
            sb.appendLine("TIMEBASE=1/1000")
            sb.appendLine("START=${ch.startMs}")
            sb.appendLine("END=${ch.endMs}")
            sb.appendLine("title=${ch.title}")
        }
        return sb.toString()
    }

    /**
     * Helper to check if a file path is a content:// URI.
     */
    private fun isContentUri(path: String): Boolean = path.startsWith("content://")
