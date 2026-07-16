# Current Branch Status (July 14, 2026)

## Branch: `fix/functional-fix`
**Status:** Performance optimization phase complete, CI passes

## Recent Performance Commits (not yet merged to main)

1. **Activity-scoped ViewModels** (`09b34e3`) — Tab ViewModels scoped to Activity instead of NavBackStackEntry. Eliminates DataStore/Room re-reads on every tab switch. Files: LibraryScreen, SearchScreen, SettingsScreen, EditScreen.

2. **Localized playback state to MiniPlayer** (`f22c6d7`) — Moved isPlaying, currentPosition, duration StateFlow collection from MainAppShell into MiniPlayer. Stops 500ms position updates from recomposing entire NavHost.

3. **EditScreen timeline deferred** (`68b3866`) — Heavy Canvas drawing (up to 80 drawLine calls) + chapter timeline deferred by one frame via withFrameNanos. Form fields render instantly, timeline appears next frame.

## Files Changed (performance optimization)
- `MainActivity.kt` — removed 3 StateFlow collections from shell level
- `MiniPlayer.kt` — accepts PlaybackManager, collects state internally
- `LibraryScreen.kt` — Activity-scoped ViewModel
- `SearchScreen.kt` — Activity-scoped ViewModel
- `SettingsScreen.kt` — Activity-scoped ViewModel, added LocalContext import
- `EditScreen.kt` — Activity-scoped ViewModel, deferred timeline rendering
- `FolderRepositoryImpl.kt` — in-memory cache (from earlier commit)

## All Phase Features (merged to main)
Phases 0-3, all bug fixes, and splash structural rearchitecture are in main.
