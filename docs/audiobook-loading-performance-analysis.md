# Voice vs Audiora — Audiobook Loading Performance

## Deep Analysis of All Components Involved

### 1. Navigation Flow Comparison

#### Voice: Instant (3 steps, 0 player interaction)

```
User taps book in library
    │
    ├── BookOverviewViewModel.onBookClick(id)
    │   └── navigator.goTo(Destination.Playback(id))     ← JUST navigates
    │
    ▼
BookPlayScreen(bookId) created
    │
    ├── retain(bookId.value) { viewModelFactory.create(bookId) }
    │   └── BookPlayViewModel.init {
    │       ├── player.pauseIfCurrentBookDifferentFrom(bookId)  ← lightweight check
    │       └── currentBookStoreId.updateData { bookId }        ← updates DataStore
    │   }
    │
    └── BookPlayViewModel.viewState()    ← @Composable, called immediately
        ├── bookRepository.flow(bookId)  ← Room Flow, cached → instant emit
        ├── playStateManager.playStateFlow  ← singleton, always available
        └── → BookPlayViewState with correct position from DB
```

#### Audiora: Slow (7 steps, heavy player interaction)

```
User taps book in library
    │
    ├── LibraryScreen → onNavigateToPlayer(book.id)
    │   └── navController.navigate("player/$bookId")
    │
    ▼
PlayerScreen composable created
    │
    ├── collectAsStateWithLifecycle() on currentBook, isPlaying, etc.
    │   └── currentBook is null  ← NOT LOADED YET → renders Loading spinner
    │
    └── LaunchedEffect(bookId) {
        ├── bookRepository.getAudiobook(bookId).first()    ← Room query (async)
        └── playbackManager.playBook(book) {
            ├── saveCurrentPositionToDb()     ← writes old book position
            ├── controller.stop()              ← STOPS player
            ├── controller.setMediaItem(...)   ← sets new item
            ├── controller.prepare()           ← PREPARES audio
            ├── controller.setPlaybackSpeed()  ← re-applies settings
            ├── apply skipSilence, volumeGain  ← per-book settings
            └── controller.play()              ← starts playback
        }
    }
    │
    ├── _currentPosition starts at 0L
    └── 500ms later → position tracker fires → progress bar jumps
```

---

### 2. Component-by-Component Comparison

#### 2.1 Navigation Action (Book → Player)

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Method** | `navigator.goTo(Destination.Playback(id))` | `navController.navigate("player/$bookId")` |
| **Player interaction?** | **None** — just navigates | Launches `playBook()` which does full re-init |
| **Performance impact** | ✅ Instant | ❌ Creates ~500ms+ delay |

**Root cause in Audiora:** `MainActivity.kt` line 238-242 starts a `LaunchedEffect(bookId)` which calls `app.playbackManager.playBook(loadedBook)`. This is a heavy operation that should NOT happen on navigation.

#### 2.2 ViewModel / State Holder

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Scoping** | `retain()` scoped to nav entry keyed by `bookId` | No ViewModel — state in `PlaybackManager` (Application-scoped) |
| **State creation** | Created lazily via `retain { factory.create(bookId) }` | Pre-created in `AudioraApplication.onCreate()` |
| **Survives recomposition** | ✅ Yes (retain) | ✅ Yes (app-scoped singleton) |

**Analysis:** Audiora's `PlaybackManager` is actually fine as a singleton — no ViewModel needed. The issue is HOW it manages state.

#### 2.3 Book Data Source

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Library screen** | `repo.flow()` → `StateFlow<List<Book>>` (reactive) | `_audiobooks` → `StateFlow<List<Audiobook>>` (reactive, cached) |
| **Player screen** | `repo.flow(bookId).filterNotNull()` → Room Flow | `getAudiobook(bookId).first()` → one-shot suspend |
| **Emit speed** | Room Flow emits from in-memory cache instantly | Room's `.first()` takes a DB round-trip |
| **Reactive?** | ✅ Push-based (Room notifies on change) | ❌ Pull-based (.first() completes and stops) |

**Root cause in Audiora:** `getAudiobook(bookId)` returns `Flow<Audiobook?>`, but the player screen uses `.first()` instead of `.collectAsState()`. This means:
- The composable renders with `null` (shows loading spinner)
- The LaunchedEffect queries the DB (even though data is already in `_audiobooks` cache)
- The screen doesn't react to position updates from `saveAudiobook()`

**What Audiora has that's good:** `_audiobooks` already caches all books in memory. But the player screen doesn't use this cache.

#### 2.4 Play Position

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Primary source** | `book.content.positionInChapter` from DB | `_currentPosition` MutableStateFlow (starts at 0) |
| **Update mechanism** | `PositionUpdater` writes to DB (400ms-5min intervals) | `startPositionTracker()` polls every 500ms |
| **First frame accuracy** | ✅ Correct (from persisted DB data) | ❌ Shows 0%, corrects after ~500ms |
| **Reactive position** | Optional: `livePlaybackStateFlow(bookId)` from MediaController | Manual: polling loop updates `_currentPosition` |

**Root cause in Audiora:** `_currentPosition` initializes to `0L` and is only updated when the position tracker fires (500ms later) or when the controller listener receives events. The screen should read position from the Room-backed `Audiobook.currentPositionMs` directly.

#### 2.5 Player Initialization

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **When player starts** | Once at app launch (in PlaybackService) | On every book selection (playBook() → stop + prepare + play) |
| **Navigation triggers?** | `pauseIfCurrentBookDifferentFrom()` — lightweight check | `playBook()` — full teardown and rebuild |
| **State restoration** | Book data has position from DB, player reads it | PlaybackManager passes position to `setMediaItem(uri, startPos)` |

**The critical insight:** Voice's player is ALREADY playing when you navigate to the playback screen. You're just looking at the state. Audiora's player needs to be initialized first, which causes the delay.

#### 2.6 Play State

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Storage** | `PlayStateManager` (singleton, `StateFlow<PlayState>`) | `_isPlaying` (MutableStateFlow in PlaybackManager) |
| **Initial value** | `PlayState.Paused` | `false` |
| **Update source** | `PlayStateDelegatingListener` attached to ExoPlayer | Controller listener in `setupControllerListener()` |
| **Always available?** | ✅ Yes (singleton with initial value) | ❌ No — depends on controller connection |

**Analysis:** Voice's `PlayStateManager` is a simple singleton class with a `StateFlow`. It's always available because the initial state is `Paused`. Audioira's `_isPlaying` works similarly but has one problem: on first navigation, the controller might not be connected yet, so the initial `false` is stale.

#### 2.7 MediaSession Interaction

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Service type** | `MediaLibraryService` | `MediaLibraryService` (just migrated) |
| **Session type** | `MediaLibrarySession` | `MediaLibrarySession` (just migrated) |
| **Controller connection** | `PlayerController.awaitConnect()` with deferred | `MediaController.Builder.buildAsync()` in init |
| **Live state** | `livePlaybackStateFlow()` returns Flow from controller | `_currentPosition` / `_isPlaying` from controller listener |

**Analysis:** Both use similar Media3 architecture now. Voice has `PlayerController` which wraps `MediaController` with a `Deferred`, while Audiora's `PlaybackManager` manages the controller directly.

---

### 3. Voice's Performance Advantages — Summary

| Advantage | How Voice Achieves It | Impact |
|-----------|----------------------|--------|
| **1. No player re-init on navigation** | `onBookClick()` just navigates — player already running | Eliminates ~500ms delay |
| **2. Position from persisted DB** | `book.content.positionInChapter` read synchronously | Progress bar correct on first frame |
| **3. Reactive Room Flow** | `repo.flow(bookId).filterNotNull()` push-based | No loading state — data already there |
| **4. Singleton PlayStateManager** | `StateFlow` with initial `Paused` | Play state instantly available |
| **5. No LaunchedEffect loading** | `viewState()` is a `@Composable` not a side effect | No flicker or race conditions |
| **6. retaine() scoping** | ViewModel lives as long as nav entry | State preserved across recompositions |

---

### 4. Implementation Plan

#### Phase 1: Stop Re-Initializing the Player on Navigation

**Files to modify:** `PlaybackManager.kt`, `MainActivity.kt`

**Strategy:** Add `ensureBookLoaded()` that is a no-op if the player is already configured for the given book.

```kotlin
// In PlaybackManager.kt
/**
 * Ensures the player is ready for the given book.
 * This is a NO-OP if the player is already configured for this book,
 * eliminating the delay when navigating back to the Now Playing screen.
 */
fun ensureBookLoaded(book: Audiobook) {
    val activeController = controller
    if (activeController == null) {
        // Controller not connected yet — cold start will handle it
        return
    }
    
    // Check if this book is already loaded
    val currentMediaItem = activeController.currentMediaItem
    val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
    val bookFileUri = if (book.filePath.isNotEmpty()) book.filePath else null
    
    // Player is already playing this book — nothing to do
    if (currentUri != null && bookFileUri != null && currentUri == bookFileUri &&
        activeController.playbackState != Player.STATE_IDLE) {
        Timber.d("ensureBookLoaded: book already loaded, skipping player init")
        return
    }
    
    // Cold start — need to prepare the player
    scope.launch {
        // ... existing playBook logic moved here
    }
}
```

**In `MainActivity.kt`:**
```kotlin
composable("player/{bookId}") { backStackEntry ->
    val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
    
    // Use the in-memory cache for instant access (already loaded from library)
    // Falls back to Room query if not cached
    val book by remember(bookId) {
        app.bookRepository.getAudiobook(bookId)
    }.collectAsStateWithLifecycle(initialValue = null)
    
    LaunchedEffect(bookId) {
        book?.let { app.playbackManager.ensureBookLoaded(it) }
    }
    
    if (book == null) {
        // Very brief loading state (rare — only on cold start)
        LoadingSpinner()
    } else {
        PlayerScreen(onNavigateBack = { ... })
    }
}
```

#### Phase 2: Read Position From Room, Not From StateFlow

**Files to modify:** `PlayerScreen.kt`, `PlaybackManager.kt` (minor)

**Strategy:** The progress bar should read `book.currentPositionMs` directly from the Room-backed model, not from `_currentPosition`. This is already the correct saved position.

```kotlin
// In PlayerScreen.kt — replace this:
val currentPosition by playbackManager.currentPosition.collectAsStateWithLifecycle()
val duration by playbackManager.duration.collectAsStateWithLifecycle()

// With this:
// Position comes from the Room-backed book model (correct on first frame)
// Only use playbackManager.currentPosition for drag preview
val displayPosition = draggingPositionMs 
    ?: currentBook?.currentPositionMs 
    ?: 0L
val displayDuration = currentBook?.durationMs ?: 0L
```

#### Phase 3: Create PlayStateManager

**New file:** `app/src/main/java/com/audiora/feature/player/PlayStateManager.kt`

```kotlin
package com.audiora.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton play state tracker providing instant access to play/pause state
 * without depending on MediaController connection.
 *
 * Mirrors Voice's [voice.core.playback.playstate.PlayStateManager].
 */
class PlayStateManager {
    val playStateFlow: StateFlow<PlayState>
        field = MutableStateFlow(PlayState.Paused)

    var playState: PlayState
        set(value) {
            playStateFlow.value = value
        }
        get() = playStateFlow.value

    enum class PlayState {
        Playing,
        Paused,
    }
}
```

```kotlin
// In PlaybackManager.setupControllerListener():
val playStateManager = PlayStateManager()  // wire as needed

activeController.addListener(object : Player.Listener {
    override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
        playStateManager.playState = if (isPlayingChanged) 
            PlayStateManager.PlayState.Playing 
        else 
            PlayStateManager.PlayState.Paused
    }
})
```

#### Phase 4: Simplify Position Tracker

**Files to modify:** `PlaybackManager.kt`

Remove `_currentPosition` state flow updates from the position tracker loop. The tracker should only persist position to DB, not update UI state:

```kotlin
private fun startPositionTracker() {
    scope.launch {
        while (isActive) {
            delay(500)
            val activeController = controller
            if (activeController != null && activeController.isPlaying) {
                val currentPos = activeController.currentPosition
                
                // Update current chapter index (needed for UI)
                updateCurrentChapterIndex(currentPos)
                
                // Sleep Timer logic (unchanged)
                // ...
                
                // Periodically write position to DB (every 10s)
                counter++
                if (counter >= 20) {
                    counter = 0
                    saveCurrentPositionToDb()
                }
            }
        }
    }
}
```

Note: `_currentPosition` is still updated in `seekTo()`, `skipForward()`, `skipBackward()`, and `playBook()` for drag state — just not in the polling loop.

---

### 5. Performance Target

| Metric | Current | Target |
|--------|---------|--------|
| Screen open → content visible | ~200-500ms | ~16ms (1 frame) |
| Progress bar accuracy on first frame | Shows 0%, corrects ~1-2s | ✅ Shows correct position immediately |
| Number of recompositions on load | 5-8 (each StateFlow emits separately) | 1-2 |
| Player initialization on nav | Full stop+prepare+play (~500ms) | ✅ No-op (already running) |
| Loading spinner visible? | Yes (currentBook starts null) | ✅ Rare (only cold start) |

---

### 6. Risks and Edge Cases

1. **Cold start**: First app launch. `MediaController` not connected. `ensureBookLoaded()` returns early because `controller` is null. The user taps a book → sees loading state → controller connects → book loads. Acceptable.

2. **Book switching**: User plays Book A, then navigates to library, taps Book B. `ensureBookLoaded()` detects URI mismatch → falls through to `playBook()`. Takes ~500ms. Acceptable — analogous to first play.

3. **Position not persisted yet**: If the user just added the book and never played it, `currentPositionMs` might be 0. That's correct behavior — should start from the beginning.

4. **App in background for hours**: Controller may disconnect. `ensureBookLoaded()` returns early. When user comes back, book may not load immediately. Mitigation: add retry logic with a 2-second timeout.

5. **Multiple rapid taps**: User taps books quickly. Each navigation creates a new composable. CoRoutine cancellation in LaunchedEffect handles cleanup automatically.
