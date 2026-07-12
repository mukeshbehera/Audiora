package com.audiora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audiora.core.navigation.Screen
import com.audiora.feature.converter.CreateScreen
import com.audiora.feature.editor.EditScreen
import com.audiora.feature.library.LibraryScreen
import com.audiora.feature.player.MiniPlayer
import com.audiora.feature.player.PlayerScreen
import com.audiora.feature.search.SearchScreen
import com.audiora.feature.settings.SettingsScreen
import com.audiora.feature.welcome.WelcomeScreen
import com.audiora.feature.welcome.SplashScreen
import com.audiora.feature.welcome.OnboardingFoldersScreen
import com.audiora.ui.theme.LocalDarkTheme
import com.audiora.ui.theme.MyApplicationTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO_PLAYER = "com.audiora.EXTRA_NAVIGATE_TO_PLAYER"
    }

    private val pendingPlayerNavigation = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Core Full Bleed Screen Edge To Edge
        enableEdgeToEdge()

        Timber.d("MainActivity launching Edge-To-Edge studio.")

        val app = application as AudioraApplication
        val settingsRepository = app.settingsRepository

        processIntent(intent)

        setContent {
            val themeMode by settingsRepository.getThemeMode().collectAsState(initial = "SYSTEM")
            val colorSchemeName by settingsRepository.getColorScheme().collectAsState(initial = "AUDIORA_PURPLE")

            MyApplicationTheme(
                themeMode = themeMode,
                colorSchemeName = colorSchemeName
            ) {
                // Main app container coordinating Screens Navigation Graph
                MainAppContainer(settingsRepository, pendingPlayerNavigation)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: android.content.Intent) {
        if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_PLAYER, false)) {
            pendingPlayerNavigation.value = true
        }
    }
}

@Composable
fun MainAppContainer(
    settingsRepository: com.audiora.domain.repository.SettingsRepository,
    pendingPlayerNavigation: MutableState<Boolean>
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val app = context.applicationContext as AudioraApplication

    val playbackManager = app.playbackManager
    val currentBook by playbackManager.currentBook.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val currentPosition by playbackManager.currentPosition.collectAsState()
    val duration by playbackManager.duration.collectAsState()

    val showMiniPlayer = currentBook != null && currentRoute != Screen.Player.route
    val showBottomNav = currentRoute != "splash" && currentRoute != "welcome" && currentRoute != "onboarding_folders" && currentRoute != Screen.Player.route

    // Handle notification tap — navigate to player screen
    LaunchedEffect(pendingPlayerNavigation.value) {
        if (pendingPlayerNavigation.value) {
            val bookId = app.playbackManager.getCurrentBookId()
            if (bookId != null) {
                navController.navigate(Screen.Library.route) {
                    popUpTo(0) { inclusive = true }
                }
                navController.navigate("player/$bookId") {
                    launchSingleTop = true
                }
            }
            pendingPlayerNavigation.value = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            // Screens with their own Scaffold handle insets independently.
            // Only splash/welcome/onboarding need the outer padding since they lack a Scaffold.
            val paddingModifier = if (currentRoute == "splash" || currentRoute == "welcome") {
                Modifier.padding(top = innerPadding.calculateTopPadding())
            } else {
                Modifier
            }

            // NavHost hosting screens with smooth premium transitions
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = paddingModifier,
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 220))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(durationMillis = 180))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(durationMillis = 220))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(durationMillis = 180))
            }
        ) {
            composable("splash") {
                SplashScreen(
                    settingsRepository = settingsRepository,
                    onSplashCompleted = { isFirstTime ->
                        val destination = if (isFirstTime) "welcome" else Screen.Library.route
                        navController.navigate(destination) {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }
            composable("welcome") {
                WelcomeScreen(
                    settingsRepository = settingsRepository,
                    onNavigateToMain = {
                        navController.navigate("onboarding_folders")
                    }
                )
            }
            composable("onboarding_folders") {
                OnboardingFoldersScreen(
                    booksRepository = app.bookRepository,
                    settingsRepository = settingsRepository,
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo("welcome") { inclusive = true }
                            popUpTo("onboarding_folders") { inclusive = true }
                        }
                    },
                    onBack = {
                        navController.navigateUp()
                    }
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onNavigateToCreate = {
                        navController.navigate(Screen.Create.route) {
                            popUpTo(Screen.Library.route)
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPlayer = { bookId ->
                        navController.navigate("player/$bookId")
                    },
                    onNavigateToDetails = { bookId ->
                        navController.navigate("details/$bookId")
                    }
                )
            }
            composable(Screen.Create.route) {
                CreateScreen(
                    onNavigateBack = {
                        navController.navigateUp()
                    },
                    onStartMerge = {
                        navController.navigate(Screen.Processing.route)
                    }
                )
            }
            composable(Screen.Processing.route) {
                com.audiora.feature.converter.ProcessingScreen(
                    onNavigateBack = {
                        navController.navigateUp()
                    },
                    onMergeCompleted = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(Screen.Library.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    androidx.navigation.navArgument("bookId") {
                        type = androidx.navigation.NavType.IntType
                    }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable

                // Use reactive Room Flow so book data (including position) is always up to date.
                // Voice's BookPlayViewModel uses the same pattern: repo.flow(bookId).filterNotNull().
                val book by remember(bookId) {
                    app.bookRepository.getAudiobook(bookId)
                }.collectAsStateWithLifecycle(initialValue = null)

                LaunchedEffect(bookId) {
                    book?.let { app.playbackManager.ensureBookLoaded(it) }
                }

                if (book == null) {
                    // Rare — only visible on cold start before Room cache loads
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    PlayerScreen(
                        onNavigateBack = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Library.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
            composable(
                route = "edit?bookId={bookId}",
                arguments = listOf(
                    androidx.navigation.navArgument("bookId") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val editBookId = backStackEntry.arguments?.getInt("bookId") ?: -1
                EditScreen(
                    bookId = editBookId,
                    onNavigateBack = if (editBookId != -1) {
                        { navController.navigateUp() }
                    } else null
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateToDetails = { bookId ->
                        navController.navigate("details/$bookId")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToFolders = {
                        navController.navigate("settings/folders")
                    }
                )
            }
            composable("settings/folders") {
                com.audiora.feature.settings.FolderManagementView(
                    onBack = { navController.navigateUp() },
                    modifier = Modifier
                )
            }
            composable(
                route = Screen.Details.route,
                arguments = listOf(
                    androidx.navigation.navArgument("bookId") {
                        type = androidx.navigation.NavType.IntType
                    }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: 0
                com.audiora.feature.detail.AudiobookDetailScreen(
                    bookId = bookId,
                    onNavigateBack = {
                        navController.navigateUp()
                    },
                    onNavigateToEdit = { editId ->
                        navController.navigate("edit?bookId=$editId")
                    }
                )
            }
        } // end of NavHost
        } // end of Scaffold content lambda

        // Floating bottom overlay — MiniPlayer + navigation bar
        if (showBottomNav) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                // Mini player above nav bar
                if (showMiniPlayer) {
                    MiniPlayer(
                        book = currentBook!!,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onTogglePlayPause = { playbackManager.togglePlayPause() },
                        onDismiss = { playbackManager.stopPlayback() },
                        onNavigateToPlayer = {
                            navController.navigate(Screen.Player.route.replace("{bookId}", currentBook!!.id.toString())) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // Elegant premium floating glass bottom bar
                com.audiora.core.design.AudioraGlassBottomBar {
                    Screen.items.forEach { screen ->
                        val selected = currentRoute?.substringBefore("?") == screen.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val targetRoute = if (screen == Screen.Edit) "edit?bookId=-1" else screen.route
                                val isInsideLibraryFlow = currentRoute == Screen.Details.route || currentRoute?.startsWith("details") == true || currentRoute?.startsWith("edit") == true

                                if (screen == Screen.Library && isInsideLibraryFlow) {
                                    navController.popBackStack(Screen.Library.route, false)
                                } else if (currentRoute != targetRoute && currentRoute?.substringBefore("?") != screen.route) {
                                    navController.navigate(targetRoute) {
                                        popUpTo(Screen.Library.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    Timber.d("Navigated to destination screen: ${screen.title}")
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}
