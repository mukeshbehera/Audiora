package com.audiora.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.GlassmorphicPrimaryButton
import com.audiora.core.design.GlassmorphicTextField
import com.audiora.core.design.ScreenTitle
import com.audiora.core.design.SectionHeader
import com.audiora.domain.util.toDisplayPath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    bookId: Int = -1,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.audiora.AudioraApplication
    
    val viewModel: EditViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
        factory = EditViewModel.provideFactory(app, app.bookRepository, bookId)
    )

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.updateCoverArt(uri)
        }
    }

    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()
    val selectedBook by viewModel.selectedBook.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val pendingCoverAction by viewModel.pendingCoverAction.collectAsStateWithLifecycle()

    val titleInput by viewModel.titleInput.collectAsStateWithLifecycle()
    val authorInput by viewModel.authorInput.collectAsStateWithLifecycle()
    val narratorInput by viewModel.narratorInput.collectAsStateWithLifecycle()
    val publisherInput by viewModel.publisherInput.collectAsStateWithLifecycle()
    val genreInput by viewModel.genreInput.collectAsStateWithLifecycle()
    val languageInput by viewModel.languageInput.collectAsStateWithLifecycle()
    val descriptionInput by viewModel.descriptionInput.collectAsStateWithLifecycle()
    val copyrightInput by viewModel.copyrightInput.collectAsStateWithLifecycle()
    val yearInput by viewModel.yearInput.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showBookSelector by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }

    // Handle snackbar notifications on save status change
    LaunchedEffect(saveStatus) {
        when (saveStatus) {
            is SaveStatus.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Saved directly to Audiobook container successfully!",
                        duration = SnackbarDuration.Short
                    )
                    viewModel.resetStatus()
                }
            }
            is SaveStatus.Error -> {
                val errorMsg = (saveStatus as SaveStatus.Error).message
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Warning: Updated database, but file write skipped ($errorMsg)",
                        duration = SnackbarDuration.Long
                    )
                    viewModel.resetStatus()
                }
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ScreenTitle(text = "Metadata Editor")
                        Text(
                            text = "Direct tag injection for M4B containers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("edit_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (selectedBook != null) {
                        if (saveStatus is SaveStatus.Saving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(24.dp)
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.resetChanges() },
                                modifier = Modifier.testTag("edit_top_bar_reset")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Reset Changes",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                onClick = { viewModel.saveChanges() },
                                modifier = Modifier.testTag("edit_top_bar_save")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = "Save Changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (allBooks.isEmpty()) {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Audiobooks Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Import or scan files first in Library to configure metadata tags.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Book Info Selector display Card
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBookSelector = !showBookSelector }
                        .testTag("edit_book_selector"),
                    cornerRadius = 20.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedBook?.title ?: "Select Audiobook...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = selectedBook?.let {
                                    "File: ${toDisplayPath(it.filePath)}"
                                } ?: "No book chosen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { showBookSelector = !showBookSelector }) {
                            Icon(
                                imageVector = if (showBookSelector) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle selector",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Selector expand list / dropdown simulation
                AnimatedVisibility(
                    visible = showBookSelector,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            allBooks.forEach { book ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectBook(book)
                                            showBookSelector = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Book,
                                        contentDescription = null,
                                        tint = if (selectedBook?.id == book.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = book.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedBook?.id == book.id) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedBook?.id == book.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = book.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (selectedBook?.id == book.id) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                        }
                    }
                }

                if (selectedBook != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SectionHeader(text = "Cover Art Artwork")

                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Use pending cover preview if set; otherwise show current cover
                            val hasPendingRemove = pendingCoverAction == "__REMOVE__"
                            val pendingReplaceUri = pendingCoverAction?.takeIf { it != "__REMOVE__" }
                            val displayCoverPath = if (hasPendingRemove) "" else (pendingReplaceUri ?: selectedBook?.coverPath ?: "default")
                            val isRealCustomCover = if (hasPendingRemove) false else (displayCoverPath.startsWith("/") || displayCoverPath.startsWith("content://"))

                            com.audiora.feature.library.AudiobookCoverArt(
                                title = selectedBook?.title ?: "",
                                author = selectedBook?.author ?: "",
                                genre = selectedBook?.genre ?: "Default",
                                coverColorSeed = displayCoverPath,
                                modifier = Modifier
                                    .width(140.dp)
                                    .testTag("edit_cover_art_preview")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { selectImageLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("edit_change_cover_button")
                                ) {
                                    Icon(
                                        imageVector = if (isRealCustomCover) Icons.Rounded.Edit else Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isRealCustomCover) "Replace Cover" else "Add Cover",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isRealCustomCover) {
                                    Button(
                                        onClick = { viewModel.updateCoverArt(null) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("edit_remove_cover_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Remove Cover",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val chaptersList by viewModel.chapters.collectAsStateWithLifecycle()

                // Segmented Tabs to switch between Metadata Attributes and Chapters Editor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { activeTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = if (activeTab == 0) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).testTag("tab_attributes"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attributes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { activeTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = if (activeTab == 1) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).testTag("tab_chapters"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ViewList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chapters (${chaptersList.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (activeTab == 0) {
                    // --- TAB 0: ATTRIBUTES EDITOR ---
                    SectionHeader(text = "Attributes Configuration")

                    // Editable Fields
                    GlassmorphicTextField(
                        value = titleInput,
                        onValueChange = { viewModel.titleInput.value = it },
                        label = "Book Title",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_title_input")
                    )

                    GlassmorphicTextField(
                        value = authorInput,
                        onValueChange = { viewModel.authorInput.value = it },
                        label = "Author",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_author_input")
                    )

                    GlassmorphicTextField(
                        value = narratorInput,
                        onValueChange = { viewModel.narratorInput.value = it },
                        label = "Narrator",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_narrator_input")
                    )

                    GlassmorphicTextField(
                        value = publisherInput,
                        onValueChange = { viewModel.publisherInput.value = it },
                        label = "Publisher",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_publisher_input")
                    )

                    GlassmorphicTextField(
                        value = genreInput,
                        onValueChange = { viewModel.genreInput.value = it },
                        label = "Genre",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_genre_input")
                    )

                    GlassmorphicTextField(
                        value = languageInput,
                        onValueChange = { viewModel.languageInput.value = it },
                        label = "Language",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_language_input")
                    )

                    GlassmorphicTextField(
                        value = yearInput,
                        onValueChange = { viewModel.yearInput.value = it },
                        label = "Year",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_year_input")
                    )

                    GlassmorphicTextField(
                        value = copyrightInput,
                        onValueChange = { viewModel.copyrightInput.value = it },
                        label = "Copyright",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_copyright_input")
                    )

                    GlassmorphicTextField(
                        value = descriptionInput,
                        onValueChange = { viewModel.descriptionInput.value = it },
                        label = "Description",
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_description_input")
                    )

                } else {
                    // --- TAB 1: CHAPTERS EDITOR ---
                    var showAddChapterDialog by remember { mutableStateOf(false) }
                    var showRenameDialogForIndex by remember { mutableStateOf<Int?>(null) }
                    
                    var newChapterTitleInput by remember { mutableStateOf("") }
                    var newChapterTimeInput by remember { mutableStateOf("") }

                    var renameChapterTitleInput by remember { mutableStateOf("") }
                    var renameChapterTimeInput by remember { mutableStateOf("") }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(text = "Chapters Studio")
                            Button(
                                onClick = {
                                    newChapterTitleInput = "Chapter ${chaptersList.size + 1}"
                                    val lastEndMs = chaptersList.lastOrNull()?.endMs ?: 0L
                                    newChapterTimeInput = formatMsToTime(lastEndMs)
                                    showAddChapterDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_chapter_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add Chapter", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Chapter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (chaptersList.isNotEmpty()) {
                            VisualChapterTimeline(
                                chapters = chaptersList,
                                totalDurationMs = selectedBook?.durationMs ?: 3600000L,
                                onChapterTimeChanged = { index, title, startMs ->
                                    viewModel.updateChapter(index, title, startMs)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("visual_chapter_timeline")
                            )
                        }

                        if (chaptersList.isEmpty()) {
                            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text("No Chapters Defined", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("Create structured chapter offsets directly inside the media container database.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        } else {
                            chaptersList.forEachIndexed { index, chapter ->
                                GlassmorphicCard(
                                    modifier = Modifier.fillMaxWidth().testTag("chapter_card_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chapter.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("chapter_title_$index")
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${formatMsToTime(chapter.startMs)} - ${formatMsToTime(chapter.endMs)} (Duration: ${formatMsToTime(chapter.durationMs)})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    renameChapterTitleInput = chapter.title
                                                    renameChapterTimeInput = formatMsToTime(chapter.startMs)
                                                    showRenameDialogForIndex = index
                                                },
                                                modifier = Modifier.size(36.dp).testTag("btn_rename_chapter_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.EditNote,
                                                    contentDescription = "Rename Chapter",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteChapter(index) },
                                                modifier = Modifier.size(36.dp).testTag("btn_delete_chapter_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete Chapter",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dialog to Add Chapter
                    if (showAddChapterDialog) {
                        val isNewTimeValid = remember(newChapterTimeInput) {
                            parseTimeToMs(newChapterTimeInput) != null
                        }
                        AlertDialog(
                            onDismissRequest = { showAddChapterDialog = false },
                            title = { Text("Add New Chapter", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = newChapterTitleInput,
                                        onValueChange = { newChapterTitleInput = it },
                                        label = { Text("Chapter Title") },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_add_title_field")
                                    )
                                    OutlinedTextField(
                                        value = newChapterTimeInput,
                                        onValueChange = { newChapterTimeInput = it },
                                        label = { Text("Start Time (e.g. 01:23:45 or 12:34)") },
                                        placeholder = { Text("HH:MM:SS or MM:SS") },
                                        isError = !isNewTimeValid && newChapterTimeInput.isNotBlank(),
                                        supportingText = {
                                            if (!isNewTimeValid && newChapterTimeInput.isNotBlank()) {
                                                Text("Use HH:MM:SS or MM:SS format", color = MaterialTheme.colorScheme.error)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_add_time_field")
                                    )
                                    Text(
                                        text = "Note: Chapter boundaries recalculate automatically so that times fit perfectly without gaps.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val parsedMs = parseTimeToMs(newChapterTimeInput)
                                        if (parsedMs != null && newChapterTitleInput.isNotBlank()) {
                                            viewModel.addChapter(newChapterTitleInput.trim(), parsedMs)
                                            showAddChapterDialog = false
                                        }
                                    },
                                    enabled = isNewTimeValid && newChapterTitleInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Add")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddChapterDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Dialog to Edit Chapter (Rename and Edit Start Time)
                    if (showRenameDialogForIndex != null) {
                        val indexToRename = showRenameDialogForIndex!!
                        val isRenameTimeValid = remember(renameChapterTimeInput) {
                            indexToRename == 0 || parseTimeToMs(renameChapterTimeInput) != null
                        }
                        AlertDialog(
                            onDismissRequest = { showRenameDialogForIndex = null },
                            title = { Text("Edit Chapter", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = renameChapterTitleInput,
                                        onValueChange = { renameChapterTitleInput = it },
                                        label = { Text("Chapter Title") },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_rename_field")
                                    )
                                    OutlinedTextField(
                                        value = renameChapterTimeInput,
                                        onValueChange = { renameChapterTimeInput = it },
                                        label = { Text("Start Time (e.g. 01:23:45 or 12:34)") },
                                        placeholder = { Text("HH:MM:SS or MM:SS") },
                                        enabled = indexToRename > 0,
                                        isError = !isRenameTimeValid && renameChapterTimeInput.isNotBlank(),
                                        supportingText = {
                                            if (!isRenameTimeValid && renameChapterTimeInput.isNotBlank()) {
                                                Text("Use HH:MM:SS or MM:SS format", color = MaterialTheme.colorScheme.error)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_rename_time_field")
                                    )
                                    if (indexToRename == 0) {
                                        Text(
                                            text = "The first chapter must start at 00:00 to keep continuous playback.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val parsedMs = parseTimeToMs(renameChapterTimeInput)
                                        if (renameChapterTitleInput.isNotBlank() && (indexToRename == 0 || parsedMs != null)) {
                                            val validStartMs = if (indexToRename == 0) 0L else parsedMs ?: 0L
                                            viewModel.updateChapter(indexToRename, renameChapterTitleInput.trim(), validStartMs)
                                            showRenameDialogForIndex = null
                                        }
                                    },
                                    enabled = isRenameTimeValid && renameChapterTitleInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRenameDialogForIndex = null }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (saveStatus is SaveStatus.Saving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        GlassmorphicPrimaryButton(
                            text = "Save All Changes",
                            onClick = { viewModel.saveChanges() },
                            icon = Icons.Rounded.Save,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_save_button")
                        )
                    }
                }

                // Extra bottom spacing to clear the floating bottom navigation bar overlay
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

private fun parseTimeToMs(timeStr: String): Long? {
    val cleanStr = timeStr.replace("\\s".toRegex(), "").replace(".", ":")
    val parts = cleanStr.split(":")
    return try {
        when (parts.size) {
            1 -> parts[0].toLongOrNull()?.let { it * 1000 }
            2 -> {
                val mins = parts[0].toLongOrNull() ?: return null
                val secs = parts[1].toLongOrNull() ?: return null
                (mins * 60 + secs) * 1000
            }
            3 -> {
                val hrs = parts[0].toLongOrNull() ?: return null
                val mins = parts[1].toLongOrNull() ?: return null
                val secs = parts[2].toLongOrNull() ?: return null
                (hrs * 3600 + mins * 60 + secs) * 1000
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatMsToTime(ms: Long): String {
    val hrs = ms / 3600000
    val mins = (ms % 3600000) / 60000
    val secs = (ms % 60000) / 1000
    return if (hrs > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }
}

/**
 * Generates a synthetic audio waveform — a list of amplitude values (0f..1f)
 * that visually resembles an audiobook's audio envelope. Uses a combination of
 * sine waves, pseudo-random segments, and chapter-like pauses to create a
 * realistic-looking waveform without reading actual audio data.
 *
 * Computed once per (totalDurationMs, zoomScale) — no frame-by-frame cost.
 */
private fun generateWaveformData(totalDurationMs: Long, zoomScale: Float): FloatArray {
    // Target ~400 bars at 1x zoom, scales with zoom so more detail is visible
    val barCount = (400 * zoomScale).toInt().coerceIn(100, 2000)
    val data = FloatArray(barCount)

    // Use the total duration as a seed for pseudo-random variation
    val seed = (totalDurationMs / 1000).toInt().coerceAtLeast(1)

    // Create "sections" that simulate the audio envelope across the book
    val sectionCount = (barCount / 80).coerceAtLeast(3)
    val sectionBounds = IntArray(sectionCount + 1)
    for (i in 0..sectionCount) {
        sectionBounds[i] = (barCount.toLong() * i / sectionCount).toInt()
    }

    // Per-section baseline amplitude and noise level
    val sectionAmps = FloatArray(sectionCount) { i ->
        0.15f + kotlin.math.sin(i * 1.7f + seed * 0.1f).let { (it + 1f) / 2f } * 0.7f
    }

    // Generate waveform with per-bar variation
    var phase = 0f
    for (i in 0 until barCount) {
        val sectionIdx = (i * sectionCount / barCount).coerceAtMost(sectionCount - 1)
        val baseAmp = sectionAmps[sectionIdx]

        // Sine wave for smooth undulation
        phase += 0.02f + baseAmp * 0.03f
        val sine = (kotlin.math.sin(phase.toDouble()) + 1f).toFloat() / 2f

        // Pseudo-random variation using simple hash
        val hash = ((i * 2654435761L) % 1000).toInt().let { (it.toFloat() / 1000f) }

        // Create occasional "silent" gaps (like pauses between sentences)
        val gapMod = if ((i % 37) == 0) 0.08f * hash else 1f

        // Combine sine envelope + random noise + gap modulation
        val amp = baseAmp * (0.3f + 0.7f * sine) * (0.5f + 0.5f * hash) * gapMod
        data[i] = amp.coerceIn(0.02f, 1f)
    }

    return data
}

@Composable
fun VisualChapterTimeline(
    chapters: List<com.audiora.domain.model.Chapter>,
    totalDurationMs: Long,
    onChapterTimeChanged: (index: Int, newName: String, newStartMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapters.isEmpty()) return
    val primaryColor = MaterialTheme.colorScheme.primary // Resolve once in Composable context
    var zoomScale by remember { mutableStateOf(1f) }
    
    // Track dragging states — declared BEFORE localChapterTimes since LaunchedEffect references it
    var draggingIndex by remember { mutableStateOf<Int?>(null) }

    // Smooth local state representation of the current start times.
    // Decoupled from the chapters parameter to prevent mid-drag resets from
    // ViewModel recomposition. Synced from chapters only when not dragging.
    var localChapterTimes by remember { mutableStateOf(chapters.map { it.startMs }) }
    // Sync from ViewModel when NOT dragging (e.g., after drag-end reconstruction).
    val currentDragIndex = draggingIndex
    LaunchedEffect(chapters, currentDragIndex) {
        if (currentDragIndex == null) {
            localChapterTimes = chapters.map { it.startMs }
        }
    }
    
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Zoom and status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Visual Timeline Editor",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Drag markers to fine-tune chapter splits",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            // Zoom controller
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Zoom: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                listOf(1f, 2f, 4f, 8f).forEach { scale ->
                    val isSelected = zoomScale == scale
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { zoomScale = scale }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("timeline_zoom_${scale.toInt()}x")
                    ) {
                        Text(
                            text = "${scale.toInt()}x",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        
        // Scrollable timeline viewport — deferred by one frame so form fields
        // render immediately, giving an instant tab-switch feel. The timeline
        // appears on the next frame (~16ms later).
        var timelineReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            androidx.compose.runtime.withFrameNanos { }
            timelineReady = true
        }
        if (timelineReady) {
        val scrollState = rememberScrollState()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        ) {
            val baseWidth = maxWidth
            val timelineWidthDp = baseWidth * zoomScale
            val density = androidx.compose.ui.platform.LocalDensity.current
            val timelineWidthPx = with(density) { timelineWidthDp.toPx() }
            
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(timelineWidthDp)
                    .horizontalScroll(scrollState)
            ) {
                // 1. Draw synthetic audio waveform behind track
                val waveformBars = remember(totalDurationMs, zoomScale) {
                    generateWaveformData(totalDurationMs, zoomScale)
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    val totalWidth = size.width
                    val barWidth = totalWidth / waveformBars.size.coerceAtLeast(1)
                    val centerY = size.height / 2f
                    val halfHeight = size.height / 2f

                    for (i in waveformBars.indices) {
                        val amplitude = waveformBars[i]
                        val barHeight = amplitude * halfHeight * 0.9f
                        val x = i * barWidth
                        val alpha = (0.3f + amplitude * 0.5f).coerceIn(0f, 1f)
                        drawRect(
                            color = primaryColor.copy(alpha = alpha),
                            topLeft = Offset(x, centerY - barHeight),
                            size = androidx.compose.ui.geometry.Size(
                                width = barWidth.coerceAtLeast(1f),
                                height = barHeight * 2f
                            )
                        )
                    }
                }
                
                // 2. Draw chapter segments track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 4.dp)
                ) {
                    // Safe offset limits
                    val maxTrackWidthDp = timelineWidthDp - 8.dp
                    
                    localChapterTimes.forEachIndexed { idx, startMs ->
                        key(idx) {
                        val endMs = localChapterTimes.getOrNull(idx + 1) ?: totalDurationMs
                        val startFr = startMs.toDouble() / totalDurationMs
                        val endFr = endMs.toDouble() / totalDurationMs

                        val startX = maxTrackWidthDp * startFr.toFloat()
                        val segmentWidth = maxTrackWidthDp * (endFr - startFr).toFloat()

                        // Alternate alpha colors
                        val colors = listOf(
                            primaryColor.copy(alpha = 0.3f),
                            primaryColor.copy(alpha = 0.12f),
                            primaryColor.copy(alpha = 0.22f)
                        )
                        val segmentColor = colors[idx % colors.size]
                        
                        Box(
                            modifier = Modifier
                                .offset(x = startX)
                                .width(segmentWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(segmentColor)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = chapters[idx].title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatMsToTime(endMs - startMs),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        } // key(idx)
                    }
                }

                // 3. Draw draggable markers/handles on top
                val maxTrackWidthDp = timelineWidthDp - 8.dp
                localChapterTimes.forEachIndexed { idx, startMs ->
                    // First chapter starts at 0ms and is NOT draggable
                    if (idx > 0) {
                        val startFr = startMs.toDouble() / totalDurationMs
                        val markerX = maxTrackWidthDp * startFr.toFloat()
                        
                        Box(
                            modifier = Modifier
                                .offset(x = markerX + 4.dp - 18.dp, y = 20.dp) // align visually centered on split point
                                .width(36.dp)
                                .height(56.dp)
                                .testTag("timeline_handle_$idx")
                                .pointerInput(idx) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = idx
                                        },
                                        onDragEnd = {
                                            draggingIndex?.let { draggingIdx ->
                                                onChapterTimeChanged(
                                                    draggingIdx,
                                                    chapters[draggingIdx].title,
                                                    localChapterTimes[draggingIdx]
                                                )
                                            }
                                            draggingIndex = null
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            // Scale dragAmount.x relative to track size
                                            val maxWidthPx = with(density) { maxTrackWidthDp.toPx() }
                                            val dxMs = (dragAmount.x / maxWidthPx) * totalDurationMs
                                            
                                            val currentVal = localChapterTimes[idx]
                                            val minStartMs = localChapterTimes[idx - 1] + 2000L // 2 seconds safety buffer
                                            val maxEndMs = (localChapterTimes.getOrNull(idx + 1) ?: totalDurationMs) - 2000L
                                            
                                            // Handle case if space is tiny < 2s
                                            val clampedMin = minOf(minStartMs, maxEndMs)
                                            val clampedMax = maxOf(minStartMs, maxEndMs)
                                            val targetVal = (currentVal + dxMs).toLong().coerceIn(clampedMin, clampedMax)
                                            
                                            val updated = localChapterTimes.toMutableList()
                                            updated[idx] = targetVal
                                            localChapterTimes = updated
                                        }
                                    )
                                }
                        ) {
                            // Split line marker UI with a circular drag pill in the middle
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Vertical track line
                                drawLine(
                                    color = primaryColor,
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, size.height),
                                    strokeWidth = 4f
                                )
                                // Active knob circle
                                drawCircle(
                                    color = primaryColor,
                                    radius = 16f,
                                    center = Offset(size.width / 2, size.height / 2)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 7f,
                                    center = Offset(size.width / 2, size.height / 2)
                                )
                            }
                        }
                        
                        // Tooltip above moving handle
                        if (draggingIndex == idx) {
                            Box(
                                modifier = Modifier
                                    .offset(x = markerX + 4.dp - 32.dp, y = 0.dp)
                                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatMsToTime(localChapterTimes[idx]),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        } // if (timelineReady)
    }
}

