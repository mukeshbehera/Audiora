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
import com.audiora.core.design.AudioraGlassButton
import com.audiora.core.design.AudioraGlassCard
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
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

    // 🔬 TEMPORARY DIAGNOSTIC — shows FFmpeg binary status for validation
    var diagFfmpegStatus by remember { mutableStateOf("Checking...") }
    var diagFfmpegVersion by remember { mutableStateOf<String?>(null) }
    var diagBackend by remember { mutableStateOf("pending") }
    var showDiag by remember { mutableStateOf(false) }
    
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

                // 🔬 TEMPORARY DIAGNOSTIC — check FFmpeg binary status
                showDiag = true
                if (app.ffmpegBinaryManager.isInitialized()) {
                    diagFfmpegStatus = "✅ FFmpeg was previously initialized"
                    diagFfmpegVersion = app.ffmpegBinaryManager.getVersion()
                } else {
                    val assetExists = try {
                        context.assets.open("ffmpeg/ffmpeg").close(); true
                    } catch (_: Exception) { false }
                    if (assetExists) {
                        diagFfmpegStatus = "⚠️ FFmpeg found in assets — will init on first use"
                    } else {
                        diagFfmpegStatus = "❌ No FFmpeg in assets — using M4BTranscoder"
                    }
                    diagFfmpegVersion = null
                }

                if (selectedFiles.isEmpty()) {
                    delay(500)
                    progress = 1.0f
                    onNavigateBack()
                    return@launch
                }

                val cacheDir = context.cacheDir
                val outputMergedFile = File(cacheDir, "audiora_assembled_${System.currentTimeMillis()}.m4b")
                val inputUris = selectedFiles.map { Uri.parse(it.uriString) }

                // ── Pre-calculate file durations and chapters (needed for both FFmpeg and fallback) ──
                currentStatus = "Analyzing audio files..."
                val fileDurations = selectedFiles.map { file ->
                    var itemDuration = 1800000L // 30 mins fallback
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, Uri.parse(file.uriString))
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val parsed = durationStr?.toLongOrNull() ?: 0L
                        if (parsed > 0) itemDuration = parsed
                        retriever.release()
                    } catch (_: Exception) { }
                    itemDuration
                }
                val totalDuration = fileDurations.sum()

                // Build chapter list based on strategy
                val chapters = mutableListOf<Chapter>()
                when (WizardState.chapterStrategy) {
                    ChapterStrategy.NO_CHAPTERS -> {
                        chapters.add(Chapter("Full Audiobook", 0L, totalDuration, totalDuration))
                    }
                    ChapterStrategy.EACH_FILE_CHAPTER -> {
                        var offset = 0L
                        selectedFiles.forEachIndexed { idx, file ->
                            val dur = fileDurations[idx]
                            chapters.add(Chapter("Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}", offset, offset + dur, dur))
                            offset += dur
                        }
                    }
                    ChapterStrategy.MANUAL -> {
                        if (WizardState.manualChapters.isNotEmpty()) {
                            chapters.addAll(WizardState.manualChapters)
                        } else {
                            var offset = 0L
                            selectedFiles.forEachIndexed { idx, file ->
                                val dur = fileDurations[idx]
                                chapters.add(Chapter("Chapter ${idx + 1}: ${file.name.substringBeforeLast('.')}", offset, offset + dur, dur))
                                offset += dur
                            }
                        }
                    }
                }

                // ── 1. Core Merging Stage (0% -> 45%) ──
                currentStatus = "Converting to M4B..."
                val ffmpegUsed = withContext(Dispatchers.IO) {
                    // Prepare inputs: copy content:// URIs to temp files since FFmpeg only supports file://
                    val ffmpegInputPaths = mutableListOf<String>()
                    val tempInputFiles = mutableListOf<File>()
                    try {
                        for (uri in inputUris) {
                            if (uri.scheme == "content") {
                                val ext = uri.lastPathSegment?.substringAfterLast('.', "mp3") ?: "mp3"
                                val tempFile = File.createTempFile("input_", ".$ext", cacheDir)
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                                }
                                tempInputFiles.add(tempFile)
                                ffmpegInputPaths.add(tempFile.absolutePath)
                            } else {
                                ffmpegInputPaths.add(uri.toString())
                            }
                        }

                        // Build metadata map from WizardState
                        val ffmpegMetadata = mutableMapOf(
                            "title" to (WizardState.title.ifBlank { "Merged Audiobook" }),
                            "artist" to (WizardState.author.ifBlank { "Unknown Artist" }),
                            "album_artist" to (WizardState.author.ifBlank { "Unknown Artist" }),
                            "composer" to (WizardState.narrator.ifBlank { "Unknown Narrator" }),
                            "publisher" to (WizardState.publisher.ifBlank { "Audiora" }),
                            "genre" to (WizardState.genre.ifBlank { "Audiobook" }),
                            "date" to (WizardState.year.ifBlank { "2026" }),
                            "description" to (WizardState.description.ifBlank { "" }),
                        )

                        // Try FFmpeg with metadata and chapters
                        val result = app.ffmpegService.createM4B(
                            inputFiles = ffmpegInputPaths,
                            outputPath = outputMergedFile.absolutePath,
                            options = ConversionOptions.DEFAULT,
                            metadata = ffmpegMetadata,
                            chapters = chapters,
                            onProgress = { pct -> progress = pct * 0.45f },
                        )

                        if (result.isSuccess) {
                            Timber.i("FFmpeg M4B creation succeeded with metadata and chapters")
                            diagBackend = "FFmpeg ✅"
                            true
                        } else {
                            Timber.w("FFmpeg M4B creation failed, falling back")
                            false
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "FFmpeg creation failed")
                        false
                    } finally {
                        // Clean up temp input files
                        tempInputFiles.forEach { it.delete() }
                    }
                }

                if (!ffmpegUsed) {
                    diagBackend = "M4BTranscoder ⚠️"
                    // Fallback: M4BTranscoder (metadata/chapters added with JAudiotagger after)
                    withContext(Dispatchers.IO) {
                        val transcodeOk = M4BTranscoder.transcode(context, inputUris, outputMergedFile, object : M4BTranscoder.ProgressListener {
                            override fun onProgress(percentage: Float) { progress = percentage * 0.45f }
                        })
                        if (!transcodeOk) {
                            Timber.e("All transcoding methods failed. Falling back to copy merge.")
                            outputMergedFile.createNewFile()
                            val buffer = ByteArray(1024 * 64)
                            FileOutputStream(outputMergedFile).use { out ->
                                selectedFiles.forEach { item ->
                                    try {
                                        context.contentResolver.openInputStream(Uri.parse(item.uriString))?.use { inp ->
                                            var len: Int; while (inp.read(buffer).also { len = it } > 0) out.write(buffer, 0, len)
                                        }
                                    } catch (e: Exception) { Timber.e(e, "Error during copy fallback: ${item.name}") }
                                }
                            }
                        }
                    }

                    // JAudiotagger metadata (only for fallback path — FFmpeg already embeds it)
                    withContext(Dispatchers.IO) {
                        try {
                            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(outputMergedFile)
                            val tag = audioFile.tag ?: audioFile.createDefaultTag()
                            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, WizardState.title.ifBlank { outputMergedFile.nameWithoutExtension })
                            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, WizardState.author.ifBlank { "Unknown Artist" })
                            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, "Audiora Combined Studio")
                            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, WizardState.author.ifBlank { "Unknown Artist" })
                            tag.setField(org.jaudiotagger.tag.FieldKey.COMPOSER, WizardState.narrator.ifBlank { "Unknown Narrator" })
                            tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, WizardState.genre.ifBlank { "Audiobook" })
                            tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, WizardState.year.ifBlank { "2026" })
                            audioFile.tag = tag; audioFile.commit()
                        } catch (e: Exception) { Timber.e(e, "Could not write metadata tags") }
                    }
                }

                progress = 0.45f

                // ── 2. Metadata / Progress animation ──
                currentStatus = if (ffmpegUsed) "Finalizing metadata..." else "Injecting High-Fidelity Tags..."
                for (i in 1..20) { delay(50); progress = 0.45f + (i.toFloat() / 20) * 0.25f }
                progress = 0.70f

                // ── 3. Chapters (already done in FFmpeg path, stored to DB here) ──
                currentStatus = "Assembling Dynamic Chapter Map..."
                for (i in 1..15) { delay(50); progress = 0.70f + (i.toFloat() / 15) * 0.20f }
                progress = 0.90f

                // ── 4. Finalizing stage (90% -> 100%) ──
                currentStatus = "Saving Audiobook..."
                for (i in 1..10) { delay(50); progress = 0.90f + (i.toFloat() / 10) * 0.10f }

                // Build and save the Audiobook entry to Room
                val firstFile = selectedFiles.first()
                val fallbackTitle = firstFile.name.substringBeforeLast('.')
                val finalTitle = if (WizardState.title.isNotBlank()) WizardState.title else "Merged $fallbackTitle"
                val finalCover = if (WizardState.coverSeed.isNotBlank()) WizardState.coverSeed else {
                    listOf("nebula", "horizon", "eternity", "neon", "infinite")[Math.abs(finalTitle.hashCode()) % 5]
                }

                val newBook = Audiobook(
                    filePath = outputMergedFile.absolutePath,
                    title = finalTitle,
                    author = if (WizardState.author.isNotBlank()) WizardState.author else "System Creator",
                    narrator = if (WizardState.narrator.isNotBlank()) WizardState.narrator else "Narrator Team",
                    publisher = if (WizardState.publisher.isNotBlank()) WizardState.publisher else "Audiora Merged",
                    genre = if (WizardState.genre.isNotBlank()) WizardState.genre else "Audiobook",
                    year = if (WizardState.year.isNotBlank()) WizardState.year else "2026",
                    description = if (WizardState.description.isNotBlank()) WizardState.description else "High-fidelity assembled seamless stream.",
                    durationMs = totalDuration,
                    currentPositionMs = 0,
                    coverPath = finalCover,
                    addedAt = System.currentTimeMillis(),
                    completed = false,
                    chaptersJson = Chapter.serializeList(chapters),
                )
                app.bookRepository.saveAudiobook(newBook)
                storageImportManager.updateImportedFiles(emptyList())

                progress = 1.0f
                delay(500)
                onMergeCompleted(0)
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
            
            // 1. 🔬 TEMPORARY DIAGNOSTIC — FFmpeg binary status
            if (showDiag) {
                val diagBg = when {
                    diagFfmpegStatus.startsWith("✅") -> Color(0xFF1B5E20)
                    diagFfmpegStatus.startsWith("⚠️") -> Color(0xFFE65100)
                    else -> Color(0xFFB71C1C)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = diagBg.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "🔬", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FFmpeg: $diagFfmpegStatus",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            if (diagFfmpegVersion != null) {
                                Text(
                                    text = "Version: $diagFfmpegVersion | Backend: $diagBackend",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            // 2. Dynamic Circular Gauge block (Matches Attached Graphic)
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
