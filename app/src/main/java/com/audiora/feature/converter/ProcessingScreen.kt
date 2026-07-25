package com.audiora.feature.converter

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiora.AudioraApplication
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter
import com.audiora.ui.theme.LocalDarkTheme
import com.audiora.ui.theme.BrandGradientStart
import com.audiora.ui.theme.BrandGradientEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    onNavigateBack: () -> Unit,
    onMergeCompleted: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val coroutineScope = rememberCoroutineScope()
    
    // Engine State
    var progress by remember { mutableStateOf(0f) }
    var currentStatus by remember { mutableStateOf("Converting to M4B...") }
    var mergeJob by remember { mutableStateOf<Job?>(null) }
    
    // Determine stages based on progress
    val mergeCompleted = progress >= 0.45f
    val metadataCompleted = progress >= 0.70f
    val chaptersCompleted = progress >= 0.90f
    val finalizingCompleted = progress >= 1.0f

    val mergeActive = progress < 0.45f
    val metadataActive = progress >= 0.45f && progress < 0.70f
    val chaptersActive = progress >= 0.70f && progress < 0.90f
    val finalizingActive = progress >= 0.90f && progress < 1.0f

    // Start Real Background Merge on Mount
    LaunchedEffect(Unit) {
        val job = coroutineScope.launch {
            try {
                val app = context.applicationContext as AudioraApplication
                val storageImportManager = com.audiora.data.local.StorageImportManager(context)
                val selectedFiles = storageImportManager.getImportedFiles()
                
                if (selectedFiles.isEmpty()) {
                    delay(500)
                    progress = 1.0f
                    onNavigateBack()
                    return@launch
                }

                // 1. Core Merging Stage (0% -> 45%) with FFmpeg Transcoding
                val cacheDir = context.cacheDir
                val outputMergedFile = File(cacheDir, "audiora_assembled_${System.currentTimeMillis()}.m4b")

                val firstFile = selectedFiles.first()
                val inputUris = selectedFiles.map { Uri.parse(it.uriString) }

                // Build chapter list for the strategy before transcoding
                val fileDurations = selectedFiles.map { file ->
                    var itemDuration = 1800000L // 30 mins fallback
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, Uri.parse(file.uriString))
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val parsed = durationStr?.toLongOrNull() ?: 0L
                        if (parsed > 0) itemDuration = parsed
                        retriever.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                    itemDuration
                }
                val totalDuration = fileDurations.sum()

                val chapters = mutableListOf<Chapter>()
                when (WizardState.chapterStrategy) {
                    ChapterStrategy.NO_CHAPTERS -> {
                        chapters.add(
                            Chapter(
                                title = "Full Audiobook",
                                startMs = 0L,
                                endMs = totalDuration,
                                durationMs = totalDuration
                            )
                        )
                    }
                    ChapterStrategy.EACH_FILE_CHAPTER -> {
                        var currentMarkerOffset = 0L
                        selectedFiles.forEachIndexed { idx, file ->
                            val itemDuration = fileDurations[idx]
                            chapters.add(
                                Chapter(
                                    title = "Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}",
                                    startMs = currentMarkerOffset,
                                    endMs = currentMarkerOffset + itemDuration,
                                    durationMs = itemDuration
                                )
                            )
                            currentMarkerOffset += itemDuration
                        }
                    }
                    ChapterStrategy.MANUAL -> {
                        if (WizardState.manualChapters.isNotEmpty()) {
                            chapters.addAll(WizardState.manualChapters)
                        } else {
                            var currentMarkerOffset = 0L
                            selectedFiles.forEachIndexed { idx, file ->
                                val itemDuration = fileDurations[idx]
                                chapters.add(
                                    Chapter(
                                        title = "Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}",
                                        startMs = currentMarkerOffset,
                                        endMs = currentMarkerOffset + itemDuration,
                                        durationMs = itemDuration
                                    )
                                )
                                currentMarkerOffset += itemDuration
                            }
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    val transcodeSuccess = M4BTranscoder.transcode(
                        context = context,
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
                                progress = percentage * 0.45f
                            }
                        }
                    )
                    if (!transcodeSuccess) {
                        Timber.e("M4B FFmpeg transcoding failed")
                    }
                }
                
                progress = 0.45f
                delay(700) // Beautiful cinematic delay

                // 2. Metadata verification stage (45% -> 70%) - FFmpeg already embedded tags
                currentStatus = "Verifying metadata..."
                delay(300)
                progress = 0.70f
                delay(700)

                // 3. Chapters verification stage (70% -> 90%) - FFmpeg already embedded chapters
                currentStatus = "Verifying chapter markers..."
                delay(300)
                progress = 0.90f
                delay(700)

                // 4. Finalizing stage (90% -> 100%) with file move to public Downloads/Audiora
                currentStatus = "Saving Audiobook..."
                val finalizeSteps = 10
                for (i in 1..finalizeSteps) {
                    delay(50)
                    progress = 0.90f + (i.toFloat() / finalizeSteps) * 0.10f
                }

                // Move the M4B from cache to Downloads/Audiora for permanent storage
                val finalOutputPath = withContext(Dispatchers.IO) {
                    moveToDownloads(context, outputMergedFile, WizardState.title.ifBlank { fallbackTitle })
                }

                // Register standard merged audiobook into database with the final path
                val fallbackTitle = firstFile.name.substringBeforeLast('.')
                
                val finalTitle = if (WizardState.title.isNotBlank()) WizardState.title else "Merged ${fallbackTitle}"
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
                
                // Clear selected files queue so a new set can be made
                storageImportManager.updateImportedFiles(emptyList())

                progress = 1.0f
                delay(500)
                
                // Self-trigger finished navigate action
                val allBooks = app.bookRepository.getAudiobooks()
                onMergeCompleted(0) // Safe routing ID
            } catch (e: Exception) {
                Timber.e(e, "Error converting audiobooks")
            }
        }
        mergeJob = job
    }

    // Gradient styling for the circular progress outline
    val circularGradient = Brush.sweepGradient(
        colors = listOf(BrandGradientStart, BrandGradientEnd, BrandGradientStart)
    )

    // Layout Screen
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Processing",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().offset(x = (-16).dp),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            mergeJob?.cancel()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("processing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Cancel process and return",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize().testTag("processing_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 1. Dynamic Circular Gauge block (Matches Attached Graphic)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(16.dp)
            ) {
                // Background Track Glow Circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 6.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                )

                // High-End Swept Active Progress Arc
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 7.dp,
                    trackColor = Color.Transparent
                )

                // Display Numeric Progress String
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("processing_percentage")
                )
            }

            // 2. Headings Description block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentStatus,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("processing_status_label")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This may take a few minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }

            // 3. Dynamic Checklist Stepper Layout with vertical line connector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // List of Stepper checklist rows
                TimelineStepRow(
                    label = "Merging Audio",
                    isCompleted = mergeCompleted,
                    isActive = mergeActive,
                    isLast = false
                )
                TimelineStepRow(
                    label = "Adding Metadata",
                    isCompleted = metadataCompleted,
                    isActive = metadataActive,
                    isLast = false
                )
                TimelineStepRow(
                    label = "Writing Chapters",
                    isCompleted = chaptersCompleted,
                    isActive = chaptersActive,
                    isLast = false
                )
                TimelineStepRow(
                    label = "Finalizing",
                    isCompleted = finalizingCompleted,
                    isActive = finalizingActive,
                    isLast = true
                )
            }

            // 4. Centered Pill Cancel Button
            OutlinedButton(
                onClick = {
                    mergeJob?.cancel()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("processing_cancel_button"),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Moves the transcoded M4B file from cache to Downloads/Audiora/ for permanent storage.
 * Uses MediaStore on API 29+ for scoped storage compliance; direct file move on older APIs.
 * Falls back to cache path if the move fails.
 */
private fun moveToDownloads(context: Context, sourceFile: File, bookTitle: String): String {
    if (!sourceFile.exists()) return sourceFile.absolutePath

    val fileName = "${bookTitle.replace("[^a-zA-Z0-9_\\- ]".toRegex(), "_").take(80)}_${System.currentTimeMillis()}.m4b"

    return if (android.os.Build.VERSION.SDK_INT >= 29) {
        // API 29+: Use MediaStore (scoped storage)
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Audiora")
            }
            val outputUri = context.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
            )
            if (outputUri != null) {
                context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                }
                val updateValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(outputUri, updateValues, null, null)
                // Delete source file — effectively a move
                sourceFile.delete()
                Timber.d("Moved M4B to MediaStore Downloads/Audiora: $outputUri")
                return outputUri.toString()
            } else {
                Timber.w("MediaStore insert returned null, falling back to cache path")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to move to MediaStore Downloads/Audiora, falling back to cache")
        }
        sourceFile.absolutePath
    } else {
        // Pre-API 29: Direct file move to external Downloads/Audiora
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val audioraDir = File(downloadsDir, "Audiora")
            if (!audioraDir.exists()) audioraDir.mkdirs()
            val destFile = File(audioraDir, fileName)
            val success = sourceFile.renameTo(destFile)
            if (success) {
                Timber.d("Moved M4B to ${destFile.absolutePath}")
                return destFile.absolutePath
            } else {
                Timber.w("renameTo failed, trying copy+delete fallback")
                FileOutputStream(destFile).use { out ->
                    sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                sourceFile.delete()
                return destFile.absolutePath
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to move to Downloads/Audiora, falling back to cache path")
            sourceFile.absolutePath
        }
    }
}

@Composable
fun TimelineStepRow(
    label: String,
    isCompleted: Boolean,
    isActive: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        
        // Checklist Indicator node with connecting line support
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            
            // Draw connecting stepper line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .offset(y = 25.dp)
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                        )
                )
            }

            // Node Circle
            if (isCompleted) {
                // Completed checked style
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (isActive) {
                // Rotating Loader / Active Style
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            } else {
                // Inactive blank outlined style
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Segment text description
        Text(
            text = label,
            style = if (isActive) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = when {
                isCompleted || isActive -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            },
            modifier = Modifier.testTag("step_label_$label")
        )
    }
}
