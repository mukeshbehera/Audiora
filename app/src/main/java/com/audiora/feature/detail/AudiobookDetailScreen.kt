package com.audiora.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.audiora.AudioraApplication
import com.audiora.domain.util.toDisplayPath
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.GlassmorphicEmptyState
import com.audiora.core.design.GlassmorphicLoadingState
import com.audiora.core.design.SectionHeader
import com.audiora.domain.model.Audiobook
import com.audiora.feature.library.AudiobookCoverArt
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookDetailScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AudioraApplication
    val detailViewModel: AudiobookDetailViewModel = viewModel(
        factory = AudiobookDetailViewModel.provideFactory(app.bookRepository, bookId)
    )
    val uiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val exportStatus by detailViewModel.exportStatus.collectAsStateWithLifecycle()
    val exportProgress by detailViewModel.exportProgress.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        if (uri != null) {
            detailViewModel.exportAudiobook(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Audiobook Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("detail_title_label")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        val book = (uiState as DetailUiState.Success).audiobook
                        IconButton(
                            onClick = {
                                val suggestedName = "${book.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}.m4b"
                                exportLauncher.launch(suggestedName)
                            },
                            modifier = Modifier.testTag("detail_export_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Export M4B",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (uiState is DetailUiState.Success) {
                                val book = (uiState as DetailUiState.Success).audiobook
                                onNavigateToEdit(book.id)
                            }
                        },
                        modifier = Modifier.testTag("detail_edit_button")
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Edit,
                            contentDescription = "Edit Metadata",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState is DetailUiState.Success) {
                val book = (uiState as DetailUiState.Success).audiobook
                ExtendedFloatingActionButton(
                    onClick = {
                        app.playbackManager.playBook(book)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Audiobook") },
                    text = { Text("Listen Now") },
                    modifier = Modifier.testTag("detail_play_fab")
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    GlassmorphicLoadingState(message = "Retrieving metadata...")
                }
                is DetailUiState.Error -> {
                    GlassmorphicEmptyState(
                        title = "Details Error",
                        description = "Could not locate this audiobook or its database record is missing.",
                        icon = Icons.Rounded.Warning,
                        actionText = "Go Back",
                        onActionClick = onNavigateBack
                    )
                }
                is DetailUiState.Success -> {
                    val book = state.audiobook
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Top Section: Beautiful Dynamic Artwork Cover & Title/Author Summary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large glowing book jacket cover
                            Box(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .aspectRatio(0.75f)
                            ) {
                                AudiobookCoverArt(
                                    title = book.title,
                                    author = book.author,
                                    genre = book.genre,
                                    coverColorSeed = book.coverPath ?: "default",
                                    modifier = Modifier.fillMaxSize().testTag("detail_cover")
                                )
                            }

                            // Meta titles
                            Column(
                                modifier = Modifier.weight(0.55f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = book.genre.uppercase(),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Text(
                                    text = book.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("detail_title")
                                )

                                Text(
                                    text = "By ${book.author}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    modifier = Modifier.testTag("detail_author")
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timelapse,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = formatDuration(book.durationMs),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("detail_duration")
                                    )
                                }
                            }
                        }

                        // Horizontal separator
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))

                        // Description Section
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectionHeader(text = "Synopsis")
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 14.dp
                            ) {
                                Text(
                                    text = book.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    modifier = Modifier.testTag("detail_description")
                                )
                            }
                        }

                        // Complete Audiobook Metadata Specs Container
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectionHeader(text = "Metadata Specifications")
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SpecRow(label = "Voice Narrator", value = book.narrator, icon = Icons.Rounded.RecordVoiceOver)
                                    SpecRow(label = "Genre / Category", value = book.genre, icon = Icons.Rounded.Category)
                                    SpecRow(label = "Language", value = if (book.language.isNotEmpty()) book.language else "English", icon = Icons.Rounded.Language)
                                    SpecRow(label = "Publisher House", value = book.publisher, icon = Icons.Rounded.Business)
                                    SpecRow(label = "Copyright Info", value = if (book.copyright.isNotEmpty()) book.copyright else "All Rights Reserved", icon = Icons.Rounded.Copyright)
                                    SpecRow(label = "Original Release", value = book.year, icon = Icons.Rounded.CalendarToday)
                                    SpecRow(label = "Added to Audiora", value = formatDate(book.addedAt), icon = Icons.Rounded.Download)
                                    SpecRow(
                                        label = "File Identifier / Stream URI",
                                        value = if (book.filePath.isNotEmpty()) toDisplayPath(book.filePath) else "Virtual Synth Stream (Seed)",
                                        icon = Icons.Rounded.Link,
                                        isSingleLine = false
                                    )
                                }
                            }
                        }

                        // Export Audiobook Interactive Panel Card
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectionHeader(
                                text = "Export System",
                                modifier = Modifier.testTag("detail_export_section_heading")
                            )
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(0.65f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Save As M4B Audiobook",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Pick directory and export standard, tag-preserved audiobook audio file.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val suggestedName = "${book.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}.m4b"
                                            exportLauncher.launch(suggestedName)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(0.35f)
                                            .testTag("detail_export_button")
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Export",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chapters Section (showing chapter count and the read-only index table)
                        val chapters = remember(book) {
                            generateMockChapters(book)
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(
                                    text = "Logical Chapters (${chapters.size})",
                                    modifier = Modifier.testTag("detail_chapter_header")
                                )
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${chapters.size} Chapters",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chapters.forEachIndexed { index, chapter ->
                                        ChapterRow(
                                            index = index + 1,
                                            title = chapter.title,
                                            durationStr = chapter.durationStr,
                                            timeRange = chapter.timeRange
                                        )
                                        if (index < chapters.size - 1) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }

            if (exportStatus !is ExportStatus.Idle) {
                AlertDialog(
                    onDismissRequest = {
                        if (exportStatus !is ExportStatus.Exporting) {
                            detailViewModel.resetExportStatus()
                        }
                    },
                    confirmButton = {
                        if (exportStatus is ExportStatus.Success || exportStatus is ExportStatus.Error) {
                            Button(
                                onClick = { detailViewModel.resetExportStatus() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("export_complete_ok_button")
                            ) {
                                Text("OK")
                            }
                        }
                    },
                    title = {
                        Text(
                            text = when (exportStatus) {
                                is ExportStatus.Exporting -> "Exporting Audiobook..."
                                is ExportStatus.Success -> "Export Successful!"
                                is ExportStatus.Error -> "Export Failed"
                                else -> ""
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (val status = exportStatus) {
                                is ExportStatus.Exporting -> {
                                    CircularProgressIndicator(
                                        progress = { exportProgress },
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(56.dp).testTag("export_progress_indicator")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${(exportProgress * 100).toInt()}% Done",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Please keep the app open while we copy and finalize your premium M4B audiobook file.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                is ExportStatus.Success -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Success",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(64.dp).testTag("export_success_icon")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Your high-fidelity M4B audiobook has been exported safely to your selected destination.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                is ExportStatus.Error -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Error,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(64.dp).testTag("export_error_icon")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = status.message,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {}
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun SpecRow(
    label: String,
    value: String,
    icon: ImageVector,
    isSingleLine: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isSingleLine) 1 else 5,
                overflow = if (isSingleLine) TextOverflow.Ellipsis else TextOverflow.Clip
            )
        }
    }
}

@Composable
fun ChapterRow(
    index: Int,
    title: String,
    durationStr: String,
    timeRange: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", index),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier.width(28.dp)
            )
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = timeRange,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        Text(
            text = durationStr,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (3600 * 1000)
    val minutes = (durationMs % (3600 * 1000)) / (60 * 1000)
    val seconds = (durationMs % (60 * 1000)) / 1000
    return if (hours > 0) {
        String.format(Locale.US, "%dh %dm %ds", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%dm %ds", minutes, seconds)
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    return format.format(date)
}

private fun generateMockChapters(book: Audiobook): List<MockChapter> {
    val duration = book.durationMs
    val count = when {
        duration < 600000L -> 1 // < 10 mins
        duration < 1800000L -> 3 // < 30 mins
        duration < 7200000L -> 6 // < 2 hours
        else -> 10
    }
    
    val list = mutableListOf<MockChapter>()
    val step = duration / count
    
    val names = listOf(
        "Prologue & Foundations",
        "The Spark of Intention",
        "Navigating the Wilderness",
        "Uncharted Dimensions",
        "Shattered Artifacts",
        "Echoes of Memory",
        "Unlocking the Cipher",
        "The Confluence Edge",
        "Into the Neon Zenith",
        "Epilogue & Looking Forward",
        "Afterword Notes",
        "Collector's Appendix"
    )

    for (i in 0 until count) {
        val startMs = i * step
        val endMs = if (i == count - 1) duration else (i + 1) * step
        val chapterDur = endMs - startMs
        
        val startMinStr = formatToTime(startMs)
        val endMinStr = formatToTime(endMs)
        
        val title = if (i < names.size) names[i] else "Section ${i + 1}"
        list.add(
            MockChapter(
                title = title,
                durationStr = formatDuration(chapterDur),
                timeRange = "$startMinStr - $endMinStr"
            )
        )
    }
    return list
}

private fun formatToTime(ms: Long): String {
    val secondsStr = String.format(Locale.US, "%02d", (ms / 1000) % 60)
    val minutesStr = String.format(Locale.US, "%02d", (ms / (60 * 1000)) % 60)
    val hours = ms / (3600 * 1000)
    return if (hours > 0) {
        "$hours:$minutesStr:$secondsStr"
    } else {
        "$minutesStr:$secondsStr"
    }
}

private data class MockChapter(val title: String, val durationStr: String, val timeRange: String)
