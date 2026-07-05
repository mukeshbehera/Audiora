package com.audiora.feature.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiora.AudioraApplication
import com.audiora.core.design.ClickableGlassmorphicCard
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.GlassmorphicEmptyState
import com.audiora.core.design.GlassmorphicTextField
import com.audiora.core.design.ScreenTitle
import com.audiora.core.design.SectionHeader
import com.audiora.feature.library.AudiobookCoverArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetails: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AudioraApplication
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.provideFactory(app, app.bookRepository)
    )

    val query by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val recentSearches by searchViewModel.recentSearches.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.searchResults.collectAsStateWithLifecycle()
    
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ScreenTitle(text = "Search Library")
                        Text(
                            text = "Locate books by title, author, or narrator tagline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Elegant search input field utilizing design system
            GlassmorphicTextField(
                value = query,
                onValueChange = { searchViewModel.updateQuery(it) },
                label = "Search",
                placeholder = "e.g., Psychology of Money or Aria Thorne...",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { searchViewModel.updateQuery("") },
                            modifier = Modifier.testTag("clear_search_query_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search query",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_text_input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        searchViewModel.onSearchAction(query)
                        focusManager.clearFocus()
                    }
                )
            )

            AnimatedContent(
                targetState = query.isBlank(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "SearchContentToggle"
            ) { isBlankQuery ->
                if (isBlankQuery) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(text = "Recent Searches")
                            if (recentSearches.isNotEmpty()) {
                                TextButton(
                                    onClick = { searchViewModel.clearRecentSearches() },
                                    modifier = Modifier.testTag("clear_all_recent_searches_btn")
                                ) {
                                    Text(
                                        text = "Clear All",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (recentSearches.isEmpty()) {
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                GlassmorphicEmptyState(
                                    title = "No Recent Searches",
                                    description = "Search or scan files in Library to build your list. Type to discover titles, authors, and narrators.",
                                    icon = Icons.Rounded.Search,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Search history cards list styled in frosted glass
                            GlassmorphicCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 16.dp
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    recentSearches.forEachIndexed { index, term ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    searchViewModel.updateQuery(term)
                                                    searchViewModel.onSearchAction(term)
                                                }
                                                .padding(vertical = 4.dp)
                                                .testTag("recent_search_item_$index"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.History,
                                                    contentDescription = "Search history icon",
                                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = term,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            IconButton(
                                                onClick = { searchViewModel.removeRecentSearch(term) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("delete_recent_search_item_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Delete specific search history entry",
                                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        if (index < recentSearches.size - 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (searchResults.isEmpty()) {
                        GlassmorphicCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp
                        ) {
                            GlassmorphicEmptyState(
                                title = "No Results Found",
                                description = "We couldn't find any audiobooks matching \"$query\". Check spelling or search a different name.",
                                icon = Icons.Rounded.SearchOff,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_results_list")
                        ) {
                            items(searchResults, key = { it.id }) { book ->
                                ClickableGlassmorphicCard(
                                    onClick = {
                                        searchViewModel.onSearchAction(query)
                                        onNavigateToDetails(book.id)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_result_item_${book.id}"),
                                    cornerRadius = 20.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AudiobookCoverArt(
                                            title = book.title,
                                            author = book.author,
                                            genre = book.genre,
                                            coverColorSeed = book.coverPath ?: "default",
                                            modifier = Modifier
                                                .width(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = book.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "By ${book.author}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (book.narrator.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Narrated by ${book.narrator}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Navigate to Details",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                            modifier = Modifier.size(24.dp)
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
