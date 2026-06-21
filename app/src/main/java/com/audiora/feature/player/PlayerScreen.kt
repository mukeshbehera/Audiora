package com.audiora.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiora.AudioraApplication
import com.audiora.core.design.GlassmorphicCard
import com.audiora.feature.library.AudiobookCoverArt
import com.audiora.ui.theme.PrimaryPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AudioraApplication
    val playbackManager = app.playbackManager

    val currentBook by playbackManager.currentBook.collectAsStateWithLifecycle()
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by playbackManager.currentPosition.collectAsStateWithLifecycle()
    val duration by playbackManager.duration.collectAsStateWithLifecycle()
    val playbackSpeed by playbackManager.playbackSpeed.collectAsStateWithLifecycle()

    var showSpeedDialog by remember { mutableStateOf(false) }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    text = "Playback Speed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.2fx", playbackSpeed),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryPurple
                    )

                    Slider(
                        value = playbackSpeed,
                        onValueChange = {
                            playbackManager.setSpeed(it.coerceIn(0.5f, 3.0f))
                        },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryPurple,
                            activeTrackColor = PrimaryPurple,
                            inactiveTrackColor = PrimaryPurple.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("player_speed_slider")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f).forEach { preset ->
                            val isSelected = Math.abs(playbackSpeed - preset) < 0.05f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) PrimaryPurple.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        playbackManager.setSpeed(preset)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${preset}x",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSpeedDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryPurple)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val scope = rememberCoroutineScope()
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var bookmarkToRename by remember { mutableStateOf<com.audiora.domain.model.Bookmark?>(null) }
    var renameText by remember { mutableStateOf("") }

    val sleepTimerType by playbackManager.sleepTimerType.collectAsStateWithLifecycle()
    val sleepTimerRemaining by playbackManager.sleepTimerRemaining.collectAsStateWithLifecycle()

    var showChaptersSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val chapters by playbackManager.chapters.collectAsStateWithLifecycle()
    val currentChapterIndex by playbackManager.currentChapterIndex.collectAsStateWithLifecycle()

    if (showChaptersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("chapters_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Table of Chapters",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
                
                Text(
                    text = "${chapters.size} Chapters found in this audiobook",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(chapters) { idx, chapter ->
                        val isPlayingChapter = currentChapterIndex == idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isPlayingChapter) PrimaryPurple.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable {
                                    playbackManager.seekToChapter(idx)
                                    showChaptersSheet = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .testTag("chapter_item_$idx"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (isPlayingChapter) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPlayingChapter) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Start: ${formatTime(chapter.startMs)} • Duration: ${formatTime(chapter.durationMs)}",
                                    fontSize = 12.sp,
                                    color = if (isPlayingChapter) PrimaryPurple.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            if (isPlayingChapter) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircle,
                                    contentDescription = "Now Playing",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("sleep_timer_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sleep Timer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )

                if (sleepTimerType != SleepTimerType.OFF) {
                    val activeLabel = when (sleepTimerType) {
                        SleepTimerType.END_OF_CHAPTER -> "Pause at end of current chapter"
                        else -> "Remaining: ${formatSleepTimeRemaining(sleepTimerRemaining)}"
                    }
                    Text(
                        text = activeLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Button(
                        onClick = {
                            playbackManager.cancelSleepTimer()
                            showSleepTimerSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("sleep_timer_option_off")
                    ) {
                        Text("Turn Off Sleep Timer", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Pause playback automatically after a chosen duration.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                // List of options (5 min, 10 min, 15 min, 30 min, 45 min, 60 min, End of chapter)
                val options = listOf(
                    SleepTimerType.MIN_5 to "5 Minutes",
                    SleepTimerType.MIN_10 to "10 Minutes",
                    SleepTimerType.MIN_15 to "15 Minutes",
                    SleepTimerType.MIN_30 to "30 Minutes",
                    SleepTimerType.MIN_45 to "45 Minutes",
                    SleepTimerType.MIN_60 to "60 Minutes",
                    SleepTimerType.END_OF_CHAPTER to "End of Chapter"
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { (type, optionLabel) ->
                        val isSelected = sleepTimerType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) PrimaryPurple.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .clickable {
                                    playbackManager.startSleepTimer(type)
                                    showSleepTimerSheet = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .testTag("sleep_timer_option_${type.name.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = optionLabel,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val bookmarks by remember(currentBook) {
        if (currentBook != null) {
            app.bookRepository.getBookmarks(currentBook!!.id)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    if (bookmarkToRename != null) {
        AlertDialog(
            onDismissRequest = { bookmarkToRename = null },
            title = { Text("Rename Bookmark", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Bookmark Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_bookmark_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeBookmark = bookmarkToRename
                        if (activeBookmark != null && renameText.isNotBlank()) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                app.bookRepository.saveBookmark(
                                    activeBookmark.copy(name = renameText.trim())
                                )
                            }
                        }
                        bookmarkToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("bookmarks_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bookmarks",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple
                    )
                    
                    IconButton(
                        onClick = {
                            val activeBook = currentBook
                            if (activeBook != null) {
                                val position = currentPosition
                                val formattedTime = formatTime(position)
                                val defaultName = "Bookmark at $formattedTime"
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    app.bookRepository.saveBookmark(
                                        com.audiora.domain.model.Bookmark(
                                            bookId = activeBook.id,
                                            positionMs = position,
                                            name = defaultName
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("add_bookmark_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "Add Bookmark",
                            tint = PrimaryPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (bookmarks.isEmpty()) {
                    // Empty list state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No Bookmarks Yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Add bookmarks to easily skip back to your favorite moments in this audiobook.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable {
                                        playbackManager.seekTo(bookmark.positionMs)
                                        showBookmarksSheet = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Position: ${formatTime(bookmark.positionMs)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Rename button
                                    IconButton(
                                        onClick = {
                                            renameText = bookmark.name
                                            bookmarkToRename = bookmark
                                        },
                                        modifier = Modifier.testTag("rename_bookmark_button_${bookmark.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Rename",
                                            tint = PrimaryPurple.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                app.bookRepository.deleteBookmark(bookmark.id)
                                            }
                                        },
                                        modifier = Modifier.testTag("delete_bookmark_button_${bookmark.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete",
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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                        // Left Back Arrow / Chevron Down
                        IconButton(
                            onClick = { onNavigateBack?.invoke() },
                            modifier = Modifier.align(Alignment.CenterStart).testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Minimize Player",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // Center "Now Playing" Title Text
                        Text(
                            text = "Now Playing",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Right 2x3 Grid / Dots Icon Action
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showMoreSheet = true }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onBackground, CircleShape))
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val book = currentBook

        if (book == null) {
            // Elegant placeholder empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = "No book loaded",
                            tint = PrimaryPurple.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No Audiobook Loaded",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Head over to your Library and tap any audiobook to begin listening.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Book Cover Art Component (Prism Custom Card with shadow)
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(0.85f),
                    contentAlignment = Alignment.Center
                ) {
                    PerfectMinimalCoverCard(
                        title = book.title,
                        author = book.author,
                        coverPath = book.coverPath,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                }

                // Centered Soft Down Chevron
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(vertical = 4.dp)
                )

                // Chapter Typography Stack
                val currentChapterObj = chapters.getOrNull(currentChapterIndex)
                val activeChapterTitle = currentChapterObj?.title ?: book.description ?: "Enjoying your audiobook"
                val chapterHeading = if (currentChapterObj != null) {
                    "Chapter ${currentChapterIndex + 1}"
                } else {
                    "Chapter 1"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .testTag("player_chapter_heading_container")
                ) {
                    Text(
                        text = chapterHeading,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = activeChapterTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Seek Timeline Slider with smooth mechanics
                var draggingPosition by remember { mutableStateOf<Float?>(null) }
                val displayProgress = draggingPosition ?: if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f)
                ) {
                    Slider(
                        value = displayProgress.coerceIn(0f, 1f),
                        onValueChange = { draggingPosition = it },
                        onValueChangeFinished = {
                            draggingPosition?.let {
                                val targetPositionMs = (it * duration).toLong()
                                playbackManager.seekTo(targetPositionMs)
                            }
                            draggingPosition = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryPurple,
                            activeTrackColor = PrimaryPurple,
                            inactiveTrackColor = PrimaryPurple.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_timeline_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val elapsedMs = if (draggingPosition != null) (draggingPosition!! * duration).toLong() else currentPosition
                        Text(
                            text = formatStandardTime(elapsedMs),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatStandardTime(duration),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                // Control Platform Play/Pause Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Bookmark borders button
                    IconButton(
                        onClick = {
                            val position = currentPosition
                            val formattedTime = formatStandardTime(position)
                            val defaultName = "Bookmark at $formattedTime"
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                app.bookRepository.saveBookmark(
                                    com.audiora.domain.model.Bookmark(
                                        bookId = book.id,
                                        positionMs = position,
                                        name = defaultName
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("player_add_bookmark_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = "Quick Bookmark",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Skip Backward
                    IconButton(
                        onClick = { playbackManager.skipBackward() },
                        modifier = Modifier.size(48.dp).testTag("player_skip_backward")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Rewind 15 seconds",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Circular Play/Pause Primary Purple Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = PrimaryPurple.copy(alpha = 0.4f),
                                spotColor = PrimaryPurple
                            )
                            .clip(CircleShape)
                            .background(PrimaryPurple)
                            .clickable { playbackManager.togglePlayPause() }
                            .testTag("player_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Skip Forward 15s using FastForward matching image perfectly
                    IconButton(
                        onClick = { playbackManager.skipForward() },
                        modifier = Modifier.size(48.dp).testTag("player_skip_forward")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Forward 15 seconds",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Playback Speed Toggle
                    TextButton(
                        onClick = { showSpeedDialog = true },
                        modifier = Modifier.testTag("player_speed_button")
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }

                // Bottom Visual Navigation Bar / Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chapters
                    Column(
                        modifier = Modifier
                            .clickable { showChaptersSheet = true }
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatListBulleted,
                            contentDescription = "Chapters",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Chapters",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }

                    // Sleep Timer
                    Column(
                        modifier = Modifier
                            .clickable { showSleepTimerSheet = true }
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerType != SleepTimerType.OFF) PrimaryPurple else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (sleepTimerType == SleepTimerType.OFF) "Sleep Timer" else formatSleepTimeRemaining(sleepTimerRemaining),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sleepTimerType != SleepTimerType.OFF) PrimaryPurple else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }

                    // Bookmark list
                    Column(
                        modifier = Modifier
                            .clickable { showBookmarksSheet = true }
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = "Bookmarks List",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Bookmark",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }

                    // More sheet
                    Column(
                        modifier = Modifier
                            .clickable { showMoreSheet = true }
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "More",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for "More" options
    if (showMoreSheet && currentBook != null) {
        val book = currentBook!!
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Audiobook Options",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Toggle Completion Status Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                app.bookRepository.saveAudiobook(
                                    book.copy(completed = !book.completed)
                                )
                            }
                            showMoreSheet = false
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (book.completed) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (book.completed) "Mark as In-Progress" else "Mark as Completed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (book.completed) "Keep listening to this book" else "Move to your finished list",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Metadata info list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ABOUT THIS AUDIOBOOK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple,
                        letterSpacing = 1.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Genre", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(book.genre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!book.year.isNullOrEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Published Year", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(book.year!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (!book.narrator.isNullOrEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Narrated By", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(book.narrator!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (!book.publisher.isNullOrEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Publisher", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(book.publisher!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (!book.description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Description", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(
                            text = book.description!!,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerfectMinimalCoverCard(
    title: String,
    author: String,
    coverPath: String?,
    modifier: Modifier = Modifier
) {
    val isRealCover = !coverPath.isNullOrEmpty() && (coverPath.startsWith("/") || coverPath.startsWith("content://"))

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(32.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFCFCFD), Color(0xFFEFF1F5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isRealCover) {
            coil.compose.AsyncImage(
                model = coverPath,
                contentDescription = "Cover for $title",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Replicate the stunning "Psychology of Money" style card dynamically!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title area
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val titleParts = title.split(" ")
                    if (titleParts.size > 2) {
                        Text(
                            text = titleParts.take(titleParts.size - 1).joinToString(" "),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E1E24),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "of",
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color(0xFF1E1E24).copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = titleParts.last(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4A5568),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E24),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Center visual graphic (Psychology brain icon stylized dynamically)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE2E8F0).copy(alpha = 0.3f), CircleShape)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF2D3748),
                        modifier = Modifier.size(54.dp)
                    )
                }

                // Author name at bottom
                Text(
                    text = author,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5568),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatStandardTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02dm %02ds", minutes, seconds)
    }
}

private fun formatSleepTimeRemaining(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun RotatingVinylCover(
    title: String,
    author: String,
    genre: String,
    coverPath: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Angle"
    )
    val rotation = if (isPlaying) angle else 0f

    Box(
        modifier = modifier
            .size(240.dp)
            .shadow(24.dp, shape = CircleShape, clip = false, ambientColor = PrimaryPurple, spotColor = PrimaryPurple)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF111115),
                radius = size.minDimension / 2
            )
            for (r in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = (size.minDimension / 2) - (r * 12.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
        }

        AudiobookCoverArt(
            title = title,
            author = author,
            genre = genre,
            coverColorSeed = coverPath ?: "default",
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
        )

        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(0xFF08080B), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        )
    }
}
