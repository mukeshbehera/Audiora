package com.audiora.feature.converter

import android.app.PendingIntent
import android.app.Service
import com.audiora.MainActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.audiora.AudioraApplication
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class TranscodeService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "transcode_progress"

        private const val ACTION_START = "com.audiora.action.START_TRANSCODE"
        private const val ACTION_CANCEL = "com.audiora.action.CANCEL_TRANSCODE"
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_BOOK_TITLE = "book_title"

        fun start(context: Context) {
            val intent = Intent(context, TranscodeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, TranscodeService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    private val app by lazy { application as AudioraApplication }
    private var transcodeJob: Job? = null
    private var cancelReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("TranscodeService created")
        registerCancelReceiver()
    }

    override fun onDestroy() {
        unregisterCancelReceiver()
        transcodeJob?.cancel()
        Timber.d("TranscodeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                app.transcodeState.value = TranscodeState.Processing(0f, "Starting...")
                showProcessingNotification(0f, "Starting...")
                transcodeJob = app.appScope.launch {
                    try {
                        runTranscodePipeline()
                    } catch (e: Exception) {
                        Timber.e(e, "Transcode pipeline failed")
                        app.transcodeState.value = TranscodeState.Failed(e.message ?: "Unknown error")
                        showFailedNotification(e.message ?: "Unknown error")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_CANCEL -> {
                Timber.d("TranscodeService cancel requested")
                app.transcodeState.value = TranscodeState.Idle
                transcodeJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runTranscodePipeline() = coroutineScope {
        val storageImportManager = com.audiora.data.local.StorageImportManager(this@TranscodeService)
        val selectedFiles = storageImportManager.getImportedFiles()
        if (selectedFiles.isEmpty()) {
            app.transcodeState.value = TranscodeState.Failed("No audio files selected")
            showFailedNotification("No audio files selected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return@coroutineScope
        }

        val firstFile = selectedFiles.first()
        val cacheDir = File(cacheDir, "transcode")
        cacheDir.mkdirs()

        val baseName = WizardState.title
            .takeUnless { it.isBlank() }
            ?: firstFile.name.substringBeforeLast('.')
        val safeBaseName = baseName.replace("[^a-zA-Z0-9_\\- ]".toRegex(), "_").take(80)
        val outputMergedFile = resolveUniqueFile(cacheDir, safeBaseName, ".m4b")
        val inputUris = selectedFiles.map { Uri.parse(it.uriString) }

        // Build chapter list
        val fileDurations = selectedFiles.map { file ->
            var itemDuration = 1800000L
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this@TranscodeService, Uri.parse(file.uriString))
                val durationStr = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                val parsed = durationStr?.toLongOrNull() ?: 0L
                if (parsed > 0) itemDuration = parsed
                retriever.release()
            } catch (_: Exception) { }
            itemDuration
        }
        val totalDuration = fileDurations.sum()

        val chapters = buildChapterList(selectedFiles, fileDurations, totalDuration)

        // Update status
        app.transcodeState.value = TranscodeState.Processing(0.05f, "Converting to M4B...")
        showProcessingNotification(0.05f, "Converting to M4B...")

        // Run FFmpeg transcode
        val transcodeSuccess = M4BTranscoder.transcode(
            context = this@TranscodeService,
            inputUris = inputUris,
            outputFile = outputMergedFile,
            title = if (WizardState.title.isNotBlank()) WizardState.title else firstFile.name.substringBeforeLast('.'),
            author = if (WizardState.author.isNotBlank()) WizardState.author else "System Creator",
            narrator = if (WizardState.narrator.isNotBlank()) WizardState.narrator else "Narrator Team",
            publisher = if (WizardState.publisher.isNotBlank()) WizardState.publisher else "Audiora Merged",
            genre = if (WizardState.genre.isNotBlank()) WizardState.genre else "Audiobook",
            year = if (WizardState.year.isNotBlank()) WizardState.year else "2026",
            description = if (WizardState.description.isNotBlank()) WizardState.description else "High-fidelity assembled seamless stream.",
            chapters = chapters,
            coverSeed = if (WizardState.coverSeed.isNotBlank()) WizardState.coverSeed else null,
            listener = object : M4BTranscoder.ProgressListener {
                override fun onProgress(percentage: Float) {
                    val state = TranscodeState.Processing(0.05f + percentage * 0.40f, "Converting to M4B...")
                    app.transcodeState.value = state
                    showProcessingNotification(state.progress, state.status)
                }
            }
        )

        if (!transcodeSuccess) {
            throw java.io.IOException("FFmpeg transcoding failed")
        }

        // Move to Downloads/Audiora
        app.transcodeState.value = TranscodeState.Processing(0.50f, "Saving audiobook...")
        showProcessingNotification(0.50f, "Saving audiobook...")

        val displayTitle = WizardState.title.ifBlank { firstFile.name.substringBeforeLast('.') }
        val finalOutputPath = moveToDownloads(outputMergedFile, displayTitle)

        // Save to Room DB
        val fallbackTitle = firstFile.name.substringBeforeLast('.')
        val finalTitle = if (WizardState.title.isNotBlank()) WizardState.title else "Merged $fallbackTitle"
        val finalAuthor = if (WizardState.author.isNotBlank()) WizardState.author else "System Creator"
        val finalNarrator = if (WizardState.narrator.isNotBlank()) WizardState.narrator else "Narrator Team"
        val finalPublisher = if (WizardState.publisher.isNotBlank()) WizardState.publisher else "Audiora Merged"
        val finalGenre = if (WizardState.genre.isNotBlank()) WizardState.genre else "Audiobook"
        val finalYear = if (WizardState.year.isNotBlank()) WizardState.year else "2026"
        val finalDescription = if (WizardState.description.isNotBlank()) WizardState.description else "High-fidelity assembled seamless stream."
        val finalCover = if (WizardState.coverSeed.isNotBlank()) WizardState.coverSeed else {
            val coverSeeds = listOf("nebula", "horizon", "eternity", "neon", "infinite")
            coverSeeds[Math.abs(finalTitle.hashCode()) % coverSeeds.size]
        }

        val newBook = Audiobook(
            filePath = finalOutputPath,
            title = finalTitle,
            author = finalAuthor,
            narrator = finalNarrator,
            publisher = finalPublisher,
            genre = finalGenre,
            year = finalYear,
            description = finalDescription,
            durationMs = totalDuration,
            currentPositionMs = 0,
            coverPath = finalCover,
            addedAt = System.currentTimeMillis(),
            completed = false,
            chaptersJson = Chapter.serializeList(chapters)
        )

        app.bookRepository.saveAudiobook(newBook)
        storageImportManager.updateImportedFiles(emptyList())
        WizardState.reset()

        // Notify completion
        app.transcodeState.value = TranscodeState.Completed(newBook.id, finalTitle)
        showCompletedNotification(newBook.id, finalTitle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildChapterList(
        files: List<com.audiora.data.local.ImportedFile>,
        fileDurations: List<Long>,
        totalDuration: Long
    ): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        when (WizardState.chapterStrategy) {
            ChapterStrategy.NO_CHAPTERS -> {
                chapters.add(Chapter("Full Audiobook", 0L, totalDuration, totalDuration))
            }
            ChapterStrategy.EACH_FILE_CHAPTER -> {
                var offset = 0L
                files.forEachIndexed { idx, file ->
                    val dur = fileDurations[idx]
                    chapters.add(
                        Chapter(
                            "Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}",
                            offset, offset + dur, dur
                        )
                    )
                    offset += dur
                }
            }
            ChapterStrategy.MANUAL -> {
                if (WizardState.manualChapters.isNotEmpty()) {
                    chapters.addAll(WizardState.manualChapters)
                } else {
                    var offset = 0L
                    files.forEachIndexed { idx, file ->
                        val dur = fileDurations[idx]
                        chapters.add(
                            Chapter(
                                "Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}",
                                offset, offset + dur, dur
                            )
                        )
                        offset += dur
                    }
                }
            }
        }
        return chapters
    }

    private fun moveToDownloads(sourceFile: File, bookTitle: String): String {
        if (!sourceFile.exists()) return sourceFile.absolutePath

        val safeBaseName = bookTitle.replace("[^a-zA-Z0-9_\\- ]".toRegex(), "_").take(80)
        val fileName = if (Build.VERSION.SDK_INT >= 29) {
            "$safeBaseName.m4b"
        } else {
            val downloadsDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ), "Audiora"
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            resolveUniqueFile(downloadsDir, safeBaseName, ".m4b").name
        }

        return if (Build.VERSION.SDK_INT >= 29) {
            try {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Audiora")
                }
                val outputUri = contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
                )
                if (outputUri != null) {
                    contentResolver.openOutputStream(outputUri)?.use { output ->
                        sourceFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    val updateValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    }
                    contentResolver.update(outputUri, updateValues, null, null)
                    sourceFile.delete()
                    outputUri.toString()
                } else {
                    sourceFile.absolutePath
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to move to Downloads/Audiora")
                sourceFile.absolutePath
            }
        } else {
            try {
                val downloadsDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "Audiora"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = resolveUniqueFile(downloadsDir, safeBaseName, ".m4b")
                if (!sourceFile.renameTo(destFile)) {
                    java.io.FileOutputStream(destFile).use { out ->
                        sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    sourceFile.delete()
                }
                destFile.absolutePath
            } catch (e: Exception) {
                Timber.e(e, "Failed to move to Downloads/Audiora")
                sourceFile.absolutePath
            }
        }
    }

    // ---- Notification helpers ----

    private fun showProcessingNotification(progress: Float, status: String) {
        val cancelIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_CANCEL).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Creating Audiobook")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showCompletedNotification(bookId: Int, title: String) {
        val openIntent = Intent(this@TranscodeService, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_DETAILS, true)
            putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            putExtra(EXTRA_BOOK_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            bookId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audiobook Created")
            .setContentText("Tap to view \"$title\"")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        // Use NotificationManagerCompat instead of startForeground since we're stopping foreground
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun showFailedNotification(error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Creation Failed")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    // ---- Broadcast Receiver for Cancel action ----

    private fun registerCancelReceiver() {
        val filter = IntentFilter(ACTION_CANCEL)
        cancelReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_CANCEL == intent.action) {
                    cancel(context)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, filter)
        }
    }

    private fun unregisterCancelReceiver() {
        cancelReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { }
        }
        cancelReceiver = null
    }

    // ---- Extra helper: resolveUniqueFile (duplicated from ProcessingScreen) ----

    private fun resolveUniqueFile(directory: File, baseName: String, extension: String): File {
        val candidate = File(directory, "$baseName$extension")
        if (!candidate.exists()) return candidate
        var counter = 1
        while (true) {
            val next = File(directory, "$baseName ($counter)$extension")
            if (!next.exists()) return next
            counter++
        }
    }
}
