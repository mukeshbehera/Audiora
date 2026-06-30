package com.audiora.feature.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiora.core.design.ClickableGlassmorphicCard
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.GlassmorphicEmptyState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiora.AudioraApplication
import com.audiora.core.design.GlassmorphicTextField
import com.audiora.domain.model.Audiobook
import com.audiora.ui.theme.PrimaryPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetails: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AudioraApplication
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.provideFactory(app.bookRepository)
    )
    val audiobooks by libraryViewModel.audiobooks.collectAsStateWithLifecycle()

    // Stateful filter and search keys
    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("Recent") } // Recent, Title, Duration

    val focusManager = LocalFocusManager.current
    val genres = remember(audiobooks) {
        val base = listOf("All", "Sci-Fi", "Philosophy", "Fantasy", "Cyberpunk", "Self-Help")
        val custom = audiobooks.map { it.genre }.filter { it.isNotEmpty() && it !in base }.distinct()
        base + custom
    }

    // Filtered and sorted books based on user actions
    val filteredAudiobooks = remember(searchQuery, selectedGenre, sortBy, audiobooks) {
        var result = audiobooks.filter { book ->
            val matchesSearch = book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true) ||
                    book.description.contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenre == "All" || book.genre.equals(selectedGenre, ignoreCase = true)
            matchesSearch && matchesGenre
        }

        result = when (sortBy) {
            "Title" -> result.sortedBy { it.title }
            "Duration" -> result.sortedByDescending { it.durationMs }
            else -> result.sortedByDescending { it.addedAt } // "Recent"
        }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchBarVisible) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search audiobooks...", fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PrimaryPurple,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = ""
                                        } else {
                                            isSearchBarVisible = false
                                        }
                                    },
                                    modifier = Modifier.testTag("close_search")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close Search",
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                    } else {
                        Text(
                            text = "Audiora",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryPurple,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.testTag("app_bar_title")
                        )
                    }
                },
                actions = {
                    if (!isSearchBarVisible) {
                        IconButton(
                            onClick = { isSearchBarVisible = true },
                            modifier = Modifier.testTag("library_search_icon")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = PrimaryPurple
                            )
                        }
                    }

                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("grid_list_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = "Toggle Grid/List View",
                            tint = PrimaryPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Continue Listening section if library has audiobooks
            if (audiobooks.isNotEmpty()) {
                val actionBook = audiobooks.first()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    ClickableGlassmorphicCard(
                        onClick = { onNavigateToDetails(actionBook.id) },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudiobookCoverArt(
                                title = actionBook.title,
                                author = actionBook.author,
                                genre = actionBook.genre,
                                coverColorSeed = actionBook.coverPath ?: "default",
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Continue Listening",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryPurple,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = actionBook.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = actionBook.author,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Chapter 5 • ${(actionBook.progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "12h 45m left",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                LinearProgressIndicator(
                                    progress = { actionBook.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = PrimaryPurple,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Play button
                            val gradient = Brush.horizontalGradient(
                                colors = listOf(com.audiora.ui.theme.BrandGradientStart, com.audiora.ui.theme.BrandGradientEnd)
                            )
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(gradient, CircleShape)
                                    .clickable { onNavigateToDetails(actionBook.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }



            // Genre filter row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGenre = genre },
                        label = { Text(text = genre, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple.copy(alpha = 0.15f),
                            selectedLabelColor = PrimaryPurple,
                            selectedLeadingIconColor = PrimaryPurple
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            selectedBorderColor = PrimaryPurple,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        modifier = Modifier.testTag("filter_chip_$genre")
                    )
                }
            }

            // Sorting bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Sort Icon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Sort by:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Recent", "Title", "Duration").forEach { sortKey ->
                        val isSorted = sortBy == sortKey
                        Text(
                            text = sortKey,
                            fontSize = 12.sp,
                            fontWeight = if (isSorted) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSorted) PrimaryPurple else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { sortBy = sortKey }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("sort_key_$sortKey")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body rendering (Grid, List, or Empty State)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (filteredAudiobooks.isEmpty()) {
                    GlassmorphicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("empty_state_card"),
                        cornerRadius = 24.dp
                    ) {
                        GlassmorphicEmptyState(
                            title = if (searchQuery.isNotEmpty() || selectedGenre != "All") "No Audiobooks Found" else "Library is Empty",
                            description = if (searchQuery.isNotEmpty() || selectedGenre != "All") {
                                "No matches for \"${searchQuery}\" under category \"${selectedGenre}\". Clear query and filter to see records."
                            } else {
                                "Convert text manuscripts, audio recordings, or EPUBs into premium narrated glassmorphic voice audiobooks."
                            },
                            icon = if (searchQuery.isNotEmpty() || selectedGenre != "All") Icons.Rounded.Search else Icons.Rounded.LibraryBooks,
                            actionText = if (searchQuery.isNotEmpty() || selectedGenre != "All") "Clear Filters" else "Open Creation Wizard",
                            onActionClick = {
                                if (searchQuery.isNotEmpty() || selectedGenre != "All") {
                                    searchQuery = ""
                                    isSearchBarVisible = false
                                    selectedGenre = "All"
                                } else {
                                    onNavigateToCreate()
                                }
                            }
                        )
                    }
                } else if (isGridView) {
                    // Responsive Grid Layout
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_grid")
                    ) {
                        items(filteredAudiobooks, key = { it.id }) { book ->
                            AudiobookGridCard(
                                audiobook = book,
                                onSelect = { onNavigateToDetails(book.id) }
                            )
                        }
                    }
                } else {
                    // Responsive List Layout
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_list")
                    ) {
                        items(filteredAudiobooks, key = { it.id }) { book ->
                            AudiobookListCard(
                                audiobook = book,
                                onSelect = { onNavigateToDetails(book.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium native design book jacket cover art rendering.
 * Renders glowing dynamic mesh styled gradients, genre tags, and book summaries.
 */
@Composable
fun AudiobookCoverArt(
    title: String,
    author: String,
    genre: String,
    coverColorSeed: String,
    modifier: Modifier = Modifier
) {
    // Generate beautiful consistent gradient maps based on unique titles and seeds
    val gradient = remember(coverColorSeed) {
        when (coverColorSeed) {
            "nebula" -> Brush.linearGradient(listOf(Color(0xFF3F2B96), Color(0xFF8E2DE2)))
            "horizon" -> Brush.linearGradient(listOf(Color(0xFFE65C00), Color(0xFFF9D423)))
            "eternity" -> Brush.linearGradient(listOf(Color(0xFF1F4037), Color(0xFF99F2C8)))
            "neon" -> Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF)))
            "infinite" -> Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
            else -> Brush.linearGradient(listOf(Color(0xFF512DA8), Color(0xFFD1C4E9)))
        }
    }

    val coverIcon = remember(coverColorSeed) {
        when (coverColorSeed) {
            "nebula" -> Icons.Rounded.Headphones
            "horizon" -> Icons.Rounded.WbSunny
            "eternity" -> Icons.Rounded.Book
            "neon" -> Icons.Rounded.ElectricBolt
            "infinite" -> Icons.Rounded.AutoAwesome
            else -> Icons.Rounded.Headphones
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(0.75f) // Establishes canonical 3:4 physical library aspect ratio
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (coverColorSeed.run { !(startsWith("/") || startsWith("content://")) }) Modifier.background(gradient) else Modifier
            )
    ) {
        val isRealCover = coverColorSeed.run { startsWith("/") || startsWith("content://") }
        if (isRealCover) {
            coil.compose.AsyncImage(
                model = coverColorSeed,
                contentDescription = "Cover Image for $title",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Still draw spine on top!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Outer spine line to resemble physical textbook
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(x = 10.dp.toPx(), y = 0f),
                            end = Offset(x = 10.dp.toPx(), y = size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Outer spine line to resemble physical textbook
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(x = 10.dp.toPx(), y = 0f),
                            end = Offset(x = 10.dp.toPx(), y = size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(8.dp)
            ) {
                // Upper spine notch and small visual tag
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = genre.uppercase(),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Icon(
                        imageVector = coverIcon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Book narrative metadata inside the artistic cover
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = author,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Single item inside the adaptive library layout Grid.
 */
@Composable
fun AudiobookGridCard(
    audiobook: Audiobook,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClickableGlassmorphicCard(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_card_${audiobook.id}"),
        cornerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant Book jacket
            AudiobookCoverArt(
                title = audiobook.title,
                author = audiobook.author,
                genre = audiobook.genre,
                coverColorSeed = audiobook.coverPath ?: "default",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("book_cover_${audiobook.id}")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Text Titles
            Text(
                text = audiobook.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = audiobook.author,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Duration display & completion status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = "Duration icon",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatDuration(audiobook.durationMs),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                if (audiobook.completed) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Finished",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "DONE",
                                color = Color(0xFF2E7D32),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${(audiobook.progress * 100).toInt()}% left",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { audiobook.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .testTag("book_progress_${audiobook.id}"),
                color = if (audiobook.completed) Color(0xFF4CAF50) else PrimaryPurple,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}

/**
 * Single item inside the library List.
 */
@Composable
fun AudiobookListCard(
    audiobook: Audiobook,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClickableGlassmorphicCard(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_card_${audiobook.id}"),
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Cover art on left side of row
            AudiobookCoverArt(
                title = audiobook.title,
                author = audiobook.author,
                genre = audiobook.genre,
                coverColorSeed = audiobook.coverPath ?: "default",
                modifier = Modifier
                    .width(74.dp)
                    .testTag("book_cover_${audiobook.id}")
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Comprehensive details on right side
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Genre and date headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = audiobook.genre.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = PrimaryPurple,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = audiobook.year,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Title and Narrator
                Text(
                    text = audiobook.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Author",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "By ${audiobook.author}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Narrator",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Read by ${audiobook.narrator}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress metrics and bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Time duration icon",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatDuration(audiobook.durationMs),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    if (audiobook.completed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Finished badge",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Completed",
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        val percentage = (audiobook.progress * 100).toInt()
                        Text(
                            text = "Listening: $percentage%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { audiobook.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .testTag("book_progress_${audiobook.id}"),
                    color = if (audiobook.completed) Color(0xFF4CAF50) else PrimaryPurple,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }
        }
    }
}

/**
 * Utility function to convert milliseconds to standard readable duration string.
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
