package com.audiora.feature.converter

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.audiora.core.design.ClickableGlassmorphicCard
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.GlassmorphicPrimaryButton
import com.audiora.data.local.ImportedFile
import com.audiora.data.local.StorageImportManager
import com.audiora.domain.model.Chapter
import com.audiora.ui.theme.PrimaryPurple
import com.audiora.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onNavigateBack: () -> Unit,
    onStartMerge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Storage manager instance
    val storageImportManager = remember { StorageImportManager(context) }
    var importedFiles by remember { mutableStateOf<List<ImportedFile>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Wizard Steps
    var currentStep by remember { mutableStateOf(1) } // 1. Files, 2. Metadata, 3. Cover, 4. Chapters

    // State bindings matching WizardState
    var title by remember { mutableStateOf(WizardState.title) }
    var author by remember { mutableStateOf(WizardState.author) }
    var narrator by remember { mutableStateOf(WizardState.narrator) }
    var publisher by remember { mutableStateOf(WizardState.publisher) }
    var genre by remember { mutableStateOf(WizardState.genre) }
    var year by remember { mutableStateOf(WizardState.year) }
    var description by remember { mutableStateOf(WizardState.description) }
    var coverSeed by remember { mutableStateOf(WizardState.coverSeed) }
    var chapterStrategy by remember { mutableStateOf(WizardState.chapterStrategy) }
    
    // Manual Chapters List
    val manualChapters = remember { mutableStateListOf<Chapter>() }
    
    // Edit Chapter dialog support
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingChapterIndex by remember { mutableStateOf(-1) }
    
    // Dialog input temporary states
    var dialogTitle by remember { mutableStateOf("") }
    var dialogStartMin by remember { mutableStateOf("") }
    var dialogDurationMin by remember { mutableStateOf("") }

    // Read stored audiobooks from SAF Storage on entry
    LaunchedEffect(Unit) {
        importedFiles = storageImportManager.getImportedFiles()
        // Sync custom chapters state on load
        manualChapters.clear()
        manualChapters.addAll(WizardState.manualChapters)
    }

    // Prefill helper when moving to Metadata Step
    LaunchedEffect(currentStep) {
        if (currentStep == 2) {
            if (title.isBlank() && importedFiles.isNotEmpty()) {
                title = importedFiles.first().name.substringBeforeLast('.')
                WizardState.title = title
            }
            if (author.isBlank() && importedFiles.isNotEmpty()) {
                // Try to get artist
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(importedFiles.first().uriString))
                    val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    if (!artist.isNullOrBlank()) {
                        author = artist
                        WizardState.author = artist
                    } else {
                        author = "System Creator"
                        WizardState.author = "System Creator"
                    }
                    retriever.release()
                } catch (e: Exception) {
                    author = "System Creator"
                    WizardState.author = "System Creator"
                }
            }
        }
        
        // Auto-generate manual chapters if strategy loaded and list is empty
        if (currentStep == 4 && chapterStrategy == ChapterStrategy.MANUAL && manualChapters.isEmpty()) {
            var currentOffset = 0L
            importedFiles.forEachIndexed { index, file ->
                var duration = 1800000L // default 30 min
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(file.uriString))
                    val extracted = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val parsed = extracted?.toLongOrNull() ?: 0L
                    if (parsed > 0) duration = parsed
                    retriever.release()
                } catch (e: Exception) {
                    // fallback
                }
                manualChapters.add(
                    Chapter(
                        title = "Chapter ${index + 1}: ${file.name.substringBeforeLast('.')}",
                        startMs = currentOffset,
                        endMs = currentOffset + duration,
                        durationMs = duration
                    )
                )
                currentOffset += duration
            }
            WizardState.manualChapters = manualChapters.toList()
        }
    }

    fun handleImportUris(uris: List<Uri>) {
        var addedCount = 0
        var rejectedCount = 0
        
        uris.forEach { uri ->
            val name = getUriFileName(context, uri)
            val extension = name.substringAfterLast('.', "").lowercase()
            val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
            
            // Strict validation for: M4B, M4A, MP3, AAC
            val isValid = extension in listOf("mp3", "m4a", "m4b", "aac") ||
                    mimeType.contains("mpeg") ||
                    mimeType.contains("mp4") ||
                    mimeType.contains("aac") ||
                    mimeType.contains("audio/x-m4b") ||
                    mimeType.contains("audio/x-m4a")

            if (isValid) {
                val size = getUriFileSize(context, uri)
                val type = when {
                    extension == "mp3" || mimeType.contains("mpeg") -> "MP3"
                    extension == "m4a" || mimeType.contains("m4a") -> "M4A"
                    extension == "m4b" || mimeType.contains("m4b") -> "M4B"
                    extension == "aac" || mimeType.contains("aac") -> "AAC"
                    else -> extension.uppercase()
                }
                val success = storageImportManager.saveImportedFile(uri, name, size, type)
                if (success) {
                    addedCount++
                    
                    // Extract metadata and save to Room Database
                    coroutineScope.launch {
                        val metadata = getAudioDurationAndMetadata(context, uri)
                        val finalTitle = if (metadata.title.isNotEmpty()) metadata.title else name.substringBeforeLast('.')
                        val finalDuration = if (metadata.durationMs > 0) metadata.durationMs else 1800000L // 30 mins fallback
                        val coverSeeds = listOf("nebula", "horizon", "eternity", "neon", "infinite")
                        val randomCoverSeed = coverSeeds[Math.abs(finalTitle.hashCode()) % coverSeeds.size]
                        
                        val app = context.applicationContext as com.audiora.AudioraApplication
                        val newBook = com.audiora.domain.model.Audiobook(
                            filePath = uri.toString(),
                            title = finalTitle,
                            author = metadata.author,
                            narrator = "System Narrator",
                            publisher = "Imported Audio",
                            genre = when (type) {
                                "M4B" -> "Audiobook"
                                "M4A" -> "Music/Voice"
                                "MP3" -> "MP3"
                                else -> "AAC Voice"
                            },
                            year = "2026",
                            description = "Beautifully imported high-fidelity local audiobook stream.",
                            durationMs = finalDuration,
                            currentPositionMs = 0,
                            coverPath = randomCoverSeed,
                            addedAt = System.currentTimeMillis(),
                            completed = false
                        )
                        app.bookRepository.saveAudiobook(newBook)
                    }
                }
            } else {
                rejectedCount++
            }
        }
        
        // Re-read modern state from device persistence
        importedFiles = storageImportManager.getImportedFiles()
        
        coroutineScope.launch {
            if (addedCount > 0 && rejectedCount > 0) {
                snackbarHostState.showSnackbar("Imported $addedCount files. Rejected $rejectedCount files of unsupported formats.")
            } else if (addedCount > 0) {
                snackbarHostState.showSnackbar("Successfully imported $addedCount audiobook files.")
            } else if (rejectedCount > 0) {
                snackbarHostState.showSnackbar("Unsupported type! Please select M4B, M4A, MP3, or AAC files.")
            }
        }
    }

    // Storage Access Framework file picker with multiple document selection support
    val multipleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            handleImportUris(uris)
        }
    }

    // Storage Access Framework file picker with single document selection support
    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportUris(listOf(uri))
        }
    }

    val isDark = LocalDarkTheme.current
    val adaptiveBg = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF9F8FD)
    val adaptiveTitle = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF0F172A)
    val adaptiveSecondaryText = if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f) else Color(0xFF374151)
    val adaptiveTertiaryText = if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else Color(0xFF6B7280)

    val dashedBoxBg = if (isDark) Color(0xFF1E152E) else Color(0xFFFAF5FF)
    val dashedBoxBorder = if (isDark) Color(0xFF7C3AED) else Color(0xFFC084FC)
    val dashedBoxText = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Create Audiobook",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = adaptiveTitle,
                        modifier = Modifier.testTag("create_title_label")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) {
                                currentStep--
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("create_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Navigate back",
                            tint = adaptiveTitle,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = adaptiveBg
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = adaptiveBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Elegantly formatted dynamic Progress indicator
            WizardStepIndicator(currentStep = currentStep)
            
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                when (currentStep) {
                    1 -> {
                        // ==================== STEP 1: SELECT FILES ====================
                        
                        // Dashed select box matching creator_wizard.png
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .drawBehind {
                                    val strokeWidthPx = 1.5.dp.toPx()
                                    val cornerRadiusPx = 24.dp.toPx()
                                    drawRoundRect(
                                        color = dashedBoxBorder, // soft adaptive border
                                        style = Stroke(
                                            width = strokeWidthPx,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                                    )
                                }
                                .clip(RoundedCornerShape(24.dp))
                                .background(dashedBoxBg)
                                .clickable {
                                    multipleFilePickerLauncher.launch(arrayOf("audio/*", "audio/mpeg", "audio/mp3"))
                                }
                                .testTag("trigger_multiple_picker_card"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = "Select Files",
                                    tint = if (isDark) Color(0xFFA78BFA) else Color(0xFF8B5CF6),
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Select MP3 Files",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dashedBoxText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap to browse or drag & drop\nMP3 files here",
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    color = dashedBoxText.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Options title
                        Text(
                            text = "Options",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = adaptiveSecondaryText,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        // Option Cards matching the design perfectly
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Step1OptionRow(
                                title = "Merge all files into one",
                                icon = Icons.Rounded.CloudQueue,
                                isSelected = chapterStrategy == ChapterStrategy.NO_CHAPTERS,
                                onClick = {
                                    chapterStrategy = ChapterStrategy.NO_CHAPTERS
                                    WizardState.chapterStrategy = ChapterStrategy.NO_CHAPTERS
                                }
                            )

                            Step1OptionRow(
                                title = "Each file as a chapter",
                                icon = Icons.Rounded.Autorenew,
                                isSelected = chapterStrategy == ChapterStrategy.EACH_FILE_CHAPTER,
                                onClick = {
                                    chapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
                                    WizardState.chapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
                                }
                            )
                        }

                        // Selected Files list details
                        if (importedFiles.isNotEmpty()) {
                            Text(
                                text = "Selected Files (${importedFiles.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = adaptiveSecondaryText,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                importedFiles.forEachIndexed { index, item ->
                                    ImportedFileRow(
                                        fileItem = item,
                                        index = index,
                                        totalCount = importedFiles.size,
                                        onMoveUp = {
                                            if (index > 0) {
                                                val mutableList = importedFiles.toMutableList()
                                                val temp = mutableList[index]
                                                mutableList[index] = mutableList[index - 1]
                                                mutableList[index - 1] = temp
                                                storageImportManager.updateImportedFiles(mutableList)
                                                importedFiles = mutableList
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < importedFiles.size - 1) {
                                                val mutableList = importedFiles.toMutableList()
                                                val temp = mutableList[index]
                                                mutableList[index] = mutableList[index + 1]
                                                mutableList[index + 1] = temp
                                                storageImportManager.updateImportedFiles(mutableList)
                                                importedFiles = mutableList
                                            }
                                        },
                                        onDelete = {
                                            storageImportManager.removeImportedFile(item.uriString)
                                            importedFiles = storageImportManager.getImportedFiles()
                                            coroutineScope.launch {
                                                try {
                                                    val app = context.applicationContext as com.audiora.AudioraApplication
                                                    val currentBooks = app.bookRepository.getAudiobooks().first()
                                                    val matchingBook = currentBooks.find { it.filePath == item.uriString }
                                                    if (matchingBook != null) {
                                                        app.bookRepository.deleteAudiobook(matchingBook.id)
                                                    }
                                                } catch (e: Exception) {
                                                    // ignore
                                                }
                                                snackbarHostState.showSnackbar("Removed ${item.name}")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    2 -> {
                        // ==================== STEP 2: ENTER METADATA ====================
                        Text(
                            text = "Enter Audiobook Details",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = adaptiveSecondaryText
                        )
                        
                        val textFieldBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                        val textFieldBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                        val txtColor = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF1F2937)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it; WizardState.title = it },
                                label = { Text("Title", color = adaptiveTertiaryText) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_title_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = textFieldBg,
                                    unfocusedContainerColor = textFieldBg,
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = textFieldBorder,
                                    focusedLabelColor = Color(0xFF8B5CF6),
                                    focusedTextColor = txtColor,
                                    unfocusedTextColor = txtColor
                                )
                            )

                            OutlinedTextField(
                                value = author,
                                onValueChange = { author = it; WizardState.author = it },
                                label = { Text("Author / Artist", color = adaptiveTertiaryText) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_author_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = textFieldBg,
                                    unfocusedContainerColor = textFieldBg,
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = textFieldBorder,
                                    focusedLabelColor = Color(0xFF8B5CF6),
                                    focusedTextColor = txtColor,
                                    unfocusedTextColor = txtColor
                                )
                            )

                            OutlinedTextField(
                                value = narrator,
                                onValueChange = { narrator = it; WizardState.narrator = it },
                                label = { Text("Narrator", color = adaptiveTertiaryText) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_narrator_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = textFieldBg,
                                    unfocusedContainerColor = textFieldBg,
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = textFieldBorder,
                                    focusedLabelColor = Color(0xFF8B5CF6),
                                    focusedTextColor = txtColor,
                                    unfocusedTextColor = txtColor
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = year,
                                    onValueChange = { year = it; WizardState.year = it },
                                    label = { Text("Production Year", color = adaptiveTertiaryText) },
                                    modifier = Modifier.weight(1f).testTag("metadata_year_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = textFieldBg,
                                        unfocusedContainerColor = textFieldBg,
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = textFieldBorder,
                                        focusedLabelColor = Color(0xFF8B5CF6),
                                        focusedTextColor = txtColor,
                                        unfocusedTextColor = txtColor
                                    )
                                )

                                OutlinedTextField(
                                    value = genre,
                                    onValueChange = { genre = it; WizardState.genre = it },
                                    label = { Text("Genre", color = adaptiveTertiaryText) },
                                    modifier = Modifier.weight(1f).testTag("metadata_genre_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = textFieldBg,
                                        unfocusedContainerColor = textFieldBg,
                                        focusedBorderColor = Color(0xFF8B5CF6),
                                        unfocusedBorderColor = textFieldBorder,
                                        focusedLabelColor = Color(0xFF8B5CF6),
                                        focusedTextColor = txtColor,
                                        unfocusedTextColor = txtColor
                                    )
                                )
                            }

                            OutlinedTextField(
                                value = publisher,
                                onValueChange = { publisher = it; WizardState.publisher = it },
                                label = { Text("Publisher", color = adaptiveTertiaryText) },
                                modifier = Modifier.fillMaxWidth().testTag("metadata_publisher_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = textFieldBg,
                                    unfocusedContainerColor = textFieldBg,
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = textFieldBorder,
                                    focusedLabelColor = Color(0xFF8B5CF6),
                                    focusedTextColor = txtColor,
                                    unfocusedTextColor = txtColor
                                )
                            )

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it; WizardState.description = it },
                                label = { Text("Synopses / Description", color = adaptiveTertiaryText) },
                                modifier = Modifier.fillMaxWidth().height(120.dp).testTag("metadata_description_input"),
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = textFieldBg,
                                    unfocusedContainerColor = textFieldBg,
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = textFieldBorder,
                                    focusedLabelColor = Color(0xFF8B5CF6),
                                    focusedTextColor = txtColor,
                                    unfocusedTextColor = txtColor
                                )
                            )
                        }
                    }

                    3 -> {
                        // ==================== STEP 3: CHOOSE COVER ====================
                        Text(
                            text = "Select Master Cover Theme",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = adaptiveSecondaryText
                        )
                        Text(
                            text = "Choose a gorgeous, futuristic gradient cover visual scheme for the finished M4B Audiobook.",
                            fontSize = 12.sp,
                            color = adaptiveTertiaryText
                        )

                        val coverThemes = listOf(
                            CoverOption("nebula", "Cosmic Purple", Brush.sweepGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))),
                            CoverOption("horizon", "Skyline Aqua", Brush.sweepGradient(listOf(Color(0xFF00c6ff), Color(0xFF0072ff)))),
                            CoverOption("eternity", "Golden Sunset", Brush.sweepGradient(listOf(Color(0xFFf12711), Color(0xFFf5af19)))),
                            CoverOption("neon", "Vaporwave Violet", Brush.sweepGradient(listOf(Color(0xFFf80759), Color(0xFFbc4e9c)))),
                            CoverOption("infinite", "Infinity Abyss", Brush.sweepGradient(listOf(Color(0xFF0f2027), Color(0xFF203a43)))),
                            CoverOption("cosmic", "Starlight Emerald", Brush.sweepGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d))))
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            coverThemes.chunked(2).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    chunk.forEach { option ->
                                        val isSelected = coverSeed == option.seed
                                        val optBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                                        val optBorder = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(optBg)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = optBorder,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    coverSeed = option.seed
                                                    WizardState.coverSeed = option.seed
                                                }
                                                .padding(6.dp)
                                        ) {
                                            // Dynamic preview background
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(option.brush),
                                                contentAlignment = Alignment.BottomStart
                                            ) {
                                                // Glass bottom label
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = option.label,
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (isSelected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(16.dp)
                                                                    .background(Color.White, CircleShape),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Check,
                                                                    contentDescription = "Selected",
                                                                    tint = Color(0xFF7C3AED),
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // ==================== STEP 4: CHAPTER CONFIGS ====================
                        Text(
                            text = "Configure Chapters Strategy",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = adaptiveSecondaryText
                        )

                        // Radio options selector styled clean and unified
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. NO CHAPTERS
                            Step4OptionRow(
                                title = "No Chapters",
                                subtitle = "Generates standard combined continuous timeline.",
                                icon = Icons.Rounded.LibraryMusic,
                                isSelected = chapterStrategy == ChapterStrategy.NO_CHAPTERS,
                                onClick = {
                                    chapterStrategy = ChapterStrategy.NO_CHAPTERS
                                    WizardState.chapterStrategy = ChapterStrategy.NO_CHAPTERS
                                }
                            )

                            // 2. EACH FILE CHAPTERS
                            Step4OptionRow(
                                title = "Each separate file is one chapter",
                                subtitle = "Generates distinct logical navigation markers per file.",
                                icon = Icons.Rounded.Bookmarks,
                                isSelected = chapterStrategy == ChapterStrategy.EACH_FILE_CHAPTER,
                                onClick = {
                                    chapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
                                    WizardState.chapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
                                }
                            )

                            // 3. MANUAL CHAPTERS
                            Step4OptionRow(
                                title = "Manual chapter creation",
                                subtitle = "Fine-tune, rename, split, or add marker intervals manually.",
                                icon = Icons.Rounded.EditNote,
                                isSelected = chapterStrategy == ChapterStrategy.MANUAL,
                                onClick = {
                                    chapterStrategy = ChapterStrategy.MANUAL
                                    WizardState.chapterStrategy = ChapterStrategy.MANUAL
                                    // Trigger regeneration if empty
                                    if (manualChapters.isEmpty()) {
                                        var currentOffset = 0L
                                        importedFiles.forEachIndexed { num, file ->
                                            manualChapters.add(
                                                Chapter(
                                                    title = "Chapter ${num + 1}: ${file.name.substringBeforeLast('.')}",
                                                    startMs = currentOffset,
                                                    endMs = currentOffset + 1800000L,
                                                    durationMs = 1800000L
                                                )
                                            )
                                            currentOffset += 1800000L
                                        }
                                        WizardState.manualChapters = manualChapters.toList()
                                    }
                                }
                            )
                        }

                        // Detailed chapter map layout if MANUAL selected
                        if (chapterStrategy == ChapterStrategy.MANUAL) {
                            val accentPrimaryColor = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
                            val itemBgColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                            val itemBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                            val mainTextCol = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF1F2937)

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chapters Layout Mapping (${manualChapters.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = adaptiveSecondaryText
                                )
                                TextButton(
                                    onClick = {
                                        dialogTitle = ""
                                        dialogStartMin = ""
                                        dialogDurationMin = ""
                                        showAddDialog = true
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = accentPrimaryColor)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add custom chapter button",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Chapter", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                manualChapters.forEachIndexed { idx, ch ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemBgColor)
                                            .border(0.5.dp, itemBorderColor, RoundedCornerShape(12.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = ch.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = mainTextCol,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Schedule,
                                                        contentDescription = "Marker Offset Icon",
                                                        tint = adaptiveTertiaryText,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Starts at: ${formatMs(ch.startMs)} • Ends at: ${formatMs(ch.endMs)}",
                                                        fontSize = 11.sp,
                                                        color = adaptiveTertiaryText
                                                    )
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingChapterIndex = idx
                                                        dialogTitle = ch.title
                                                        dialogStartMin = (ch.startMs / 60000L).toString()
                                                        dialogDurationMin = (ch.durationMs / 60000L).toString()
                                                        showEditDialog = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = "Edit manual chapter",
                                                        tint = accentPrimaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (idx in manualChapters.indices) {
                                                            manualChapters.removeAt(idx)
                                                            WizardState.manualChapters = manualChapters.toList()
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Delete manual chapter",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wizard Action Bottom Gradient Pill Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6),
                                Color(0xFF6D28D9)
                            )
                        )
                    )
                    .clickable {
                        coroutineScope.launch {
                            if (currentStep == 1) {
                                if (importedFiles.isEmpty()) {
                                    snackbarHostState.showSnackbar("Select at least one audio file to work with.")
                                } else {
                                    currentStep++
                                }
                            } else if (currentStep == 2) {
                                if (title.isBlank()) {
                                    snackbarHostState.showSnackbar("Please fill out the audiobook title field.")
                                } else {
                                    currentStep++
                                }
                            } else if (currentStep == 3) {
                                currentStep++
                            } else {
                                // Final Step 4: start the merge progress screen
                                onStartMerge()
                            }
                        }
                    }
                    .testTag("next_voice_step_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentStep == 4) "Create Audiobook" else "Next",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = "Next step",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(20.dp)
                )
            }
        }
    }

    // ==================== MANUAL CHAPTERS ADD DIALOG ====================
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom Chapter", fontWeight = FontWeight.Bold, color = PrimaryPurple) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dialogTitle,
                        onValueChange = { dialogTitle = it },
                        label = { Text("Chapter Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dialogStartMin,
                        onValueChange = { dialogStartMin = it },
                        label = { Text("Starts at (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dialogDurationMin,
                        onValueChange = { dialogDurationMin = it },
                        label = { Text("Duration (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numStart = dialogStartMin.toLongOrNull() ?: 0L
                        val numDur = dialogDurationMin.toLongOrNull() ?: 30L
                        val startMs = numStart * 60000L
                        val durationMs = numDur * 60000L
                        
                        manualChapters.add(
                            Chapter(
                                title = dialogTitle.ifBlank { "Custom Chapter ${manualChapters.size + 1}" },
                                startMs = startMs,
                                endMs = startMs + durationMs,
                                durationMs = durationMs
                            )
                        )
                        // sort chronologically
                        val sorted = manualChapters.sortedBy { it.startMs }
                        manualChapters.clear()
                        manualChapters.addAll(sorted)
                        WizardState.manualChapters = manualChapters.toList()
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = PrimaryPurple)
                }
            }
        )
    }

    // ==================== MANUAL CHAPTERS EDIT DIALOG ====================
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Chapter Marker", fontWeight = FontWeight.Bold, color = PrimaryPurple) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dialogTitle,
                        onValueChange = { dialogTitle = it },
                        label = { Text("Chapter Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dialogStartMin,
                        onValueChange = { dialogStartMin = it },
                        label = { Text("Starts at (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dialogDurationMin,
                        onValueChange = { dialogDurationMin = it },
                        label = { Text("Duration (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingChapterIndex in manualChapters.indices) {
                            val numStart = dialogStartMin.toLongOrNull() ?: 0L
                            val numDur = dialogDurationMin.toLongOrNull() ?: 30L
                            val startMs = numStart * 60000L
                            val durationMs = numDur * 60000L
                            
                            manualChapters[editingChapterIndex] = Chapter(
                                title = dialogTitle.ifBlank { "Chapter ${editingChapterIndex + 1}" },
                                startMs = startMs,
                                endMs = startMs + durationMs,
                                durationMs = durationMs
                            )
                            val sorted = manualChapters.sortedBy { it.startMs }
                            manualChapters.clear()
                            manualChapters.addAll(sorted)
                            WizardState.manualChapters = manualChapters.toList()
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = PrimaryPurple)
                }
            }
        )
    }
}

@Composable
fun Step1OptionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val background = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val borderColor = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
    val checkColor = if (isSelected) Color(0xFF7C3AED) else (if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFFD1D5DB))
    val textColor = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF1F2937)
    val iconColor = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF9CA3AF))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF7C3AED), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.5.dp, checkColor, CircleShape)
                )
            }
        }
    }
}

@Composable
fun Step4OptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val background = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val borderColor = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
    val checkColor = if (isSelected) Color(0xFF7C3AED) else (if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFFD1D5DB))
    val textColor = if (isDark) MaterialTheme.colorScheme.onBackground else Color(0xFF1F2937)
    val subtitleColor = if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f) else Color(0xFF6B7280)
    val iconColor = if (isSelected) Color(0xFF8B5CF6) else (if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF9CA3AF))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF7C3AED), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.5.dp, checkColor, CircleShape)
                )
            }
        }
    }
}

/**
 * Premium glassmorphic row depicting the imported audio document
 */
@Composable
fun ImportedFileRow(
    fileItem: ImportedFile,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("imported_file_${index}"),
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Media Type Icon Badge representation
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(PrimaryPurple.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fileItem.type,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = PrimaryPurple
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = fileItem.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatSize(fileItem.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )

                        // Proving dynamic persistent URI works across restarts
                        if (fileItem.isPermissionValid) {
                            Icon(
                                imageVector = Icons.Rounded.PublishedWithChanges,
                                contentDescription = "Active Persistent Permission",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Permission Survived",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Permission Lost",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Verify Access",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("move_up_${index}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move file up in order",
                        tint = if (index > 0) PrimaryPurple else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("move_down_${index}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move file down in order",
                        tint = if (index < totalCount - 1) PrimaryPurple else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_file_${index}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove file item",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WizardStepIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val activeColor = Color(0xFF8B5CF6)
    val completedColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
    val inactiveDotBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f)
    val inactiveDotTextColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)
    
    val activeLabelColor = if (isDark) Color.White else Color(0xFF1F2937)
    val inactiveLabelColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.35f)
    
    val activeLineColor = Color(0xFF8B5CF6).copy(alpha = 0.4f)
    val inactiveLineColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val steps = listOf("Files", "Metadata", "Cover", "Chapters")
        steps.forEachIndexed { idx, stepName ->
            val stepNum = idx + 1
            val isActive = stepNum == currentStep
            val isCompleted = stepNum < currentStep
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Dot/Number
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = when {
                                isActive -> activeColor
                                isCompleted -> completedColor
                                else -> inactiveDotBg
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Completed",
                            tint = activeColor,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = stepNum.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else inactiveDotTextColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = stepName,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive || isCompleted) activeLabelColor else inactiveLabelColor
                )
            }
            
            if (idx < steps.size - 1) {
                // Connection bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (stepNum < currentStep) activeLineColor
                            else inactiveLineColor
                        )
                )
            }
        }
    }
}

private data class CoverOption(val seed: String, val label: String, val brush: Brush)

/**
 * Secondary metadata calculation utils
 */
private fun getUriFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result ?: "Unknown Audio File"
}

private fun getUriFileSize(context: Context, uri: Uri): Long {
    var result: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    result = cursor.getLong(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    return result
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatMs(milliseconds: Long): String {
    val totalSec = milliseconds / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}

/**
 * Extract actual duration, title, and artist metadata from local Uri using MediaMetadataRetriever
 */
private fun getAudioDurationAndMetadata(context: Context, uri: Uri): AudioMetadata {
    val retriever = android.media.MediaMetadataRetriever()
    var duration = 0L
    var title = ""
    var author = "Unknown Author"
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        duration = durationStr?.toLongOrNull() ?: 0L
        title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
        val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        if (!artist.isNullOrBlank()) {
            author = artist
        }
    } catch (e: Exception) {
        // Fallback
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
    }
    return AudioMetadata(duration, title, author)
}

private data class AudioMetadata(val durationMs: Long, val title: String, val author: String)
