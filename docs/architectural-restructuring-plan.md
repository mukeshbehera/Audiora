# Audiora Architectural Restructuring — Comparison with Voice

## 1. The Fundamental Structural Difference

Voice separates playback into **5 distinct classes** with single responsibilities. Audiora has **1 monolithic class** (`PlaybackManager`) doing everything.

### Voice's Separation of Concerns

```
PlaybackService (MediaLibraryService)
  └── Just hosts MediaLibrarySession + VoicePlayer + sets notification provider
  
PlayerController (UI-side controller)
  └── Wraps MediaController with Deferred + awaitConnect()
  └── Every action goes through executeAfterPrepare { controller -> ... }
  └── No book state, no chapters, no sleep timer
  
PlayStateManager (Singleton)
  └── Just one StateFlow<PlayState> — always available
  └── Updated by PlayStateDelegatingListener attached to ExoPlayer
  
PositionUpdater (Scoped to PlaybackService)
  └── Attached to ExoPlayer via Player.Listener
  └── Flushes position to DB on state changes + periodic interval
  
CurrentBookResolver (Singleton)
  └── Resolves current book from DataStore + optional live overlay
  
BookPlayViewModel (Screen-scoped)
  └── Composes view state from Room Flow + PlayStateManager
  └── NO direct player interaction — delegates to PlayerController
  └── Chapters read from Book model directly
```

### Audiora's Monolithic PlaybackManager

```
AudioraApplication
  └── PlaybackManager (ONE CLASS — does everything)
      ├── Controller connection (nullable var, no await)
      ├── Book state (currentBook, isPlaying, chapters, etc.)
      ├── Position tracking (500ms polling)
      ├── Sleep timer
      ├── Player initialization (playBook)
      ├── Chapter management
      ├── Settings management (skip silence, volume gain)
      └── Position persistence
```

### Voice's executeAfterPrepare Pattern (Critical)

```kotlin
// Voice — every action awaits + prepares automatically
fun playPause() = executeAfterPrepare { controller ->
    if (controller.isPlaying) controller.pause() else controller.play()
}

private inline fun executeAfterPrepare(action: (MediaController) -> Unit) {
    scope.launch {
        val controller = awaitConnect()     // ← Waits for connection
        if (maybePrepare(controller)) {      // ← Prepares if needed  
            action(controller)                // ← Executes action
        }
    }
}

private suspend fun maybePrepare(controller: MediaController): Boolean {
    // Checks if current book is loaded; if not, sets MediaItem + prepares
    val bookId = currentBookStoreId.data.first() ?: return false
    if (controller.currentBookId() == bookId &&
        controller.playbackState in listOf(STATE_READY, STATE_BUFFERING)) {
        return true  // ← Already prepared, no-op
    }
    val book = bookRepository.get(bookId) ?: return false
    controller.setMediaItem(mediaItemProvider.mediaItem(book))
    controller.prepare()
    return true
}
```

### Audiora's Broken Pattern

```kotlin
// Audiora — each action silently fails if not connected
fun togglePlayPause() {
    val activeController = controller ?: return  // ← SILENT FAIL
    if (activeController.isPlaying) pause() else play()
}

fun ensureBookLoaded(book: Audiobook) {
    // ...sets book state...
    val activeController = controller
    if (activeController == null) return  // ← GIVES UP, never retries
    playBook(book)
}
```

---

## 2. Required Architectural Changes

### Change 1: Extract PlayerController from PlaybackManager

**Current state:** `PlaybackManager` owns the `MediaController` directly.

**Target state:** A new `PlayerController` class wraps `MediaController` with a `Deferred` and `awaitConnect()` pattern, exactly like Voice.

```kotlin
// New class — mirrors Voice's PlayerController
class PlayerController(private val context: Context) {
    private var _controller: Deferred<MediaController> = newControllerAsync()
    
    private fun newControllerAsync() = MediaController
        .Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
        .buildAsync()
        .asDeferred()
    
    private val controller: Deferred<MediaController>
        get() {
            // Auto-reconnect if disconnected
            if (_controller.isCompleted) {
                val completed = _controller.getCompleted()
                if (!completed.isConnected) {
                    completed.release()
                    _controller = newControllerAsync()
                }
            }
            return _controller
        }
    
    suspend fun awaitConnect(): MediaController? {
        return try { controller.await() } catch (e: Exception) { null }
    }
    
    // All actions use executeAfterPrepare pattern
    fun playPause() = executeAfterPrepare { c ->
        if (c.isPlaying) c.pause() else c.play()
    }
    
    fun seekForward() = executeAfterPrepare { c -> c.seekForward() }
    fun seekBack() = executeAfterPrepare { c -> c.seekBack() }
    fun seekTo(positionMs: Long) = executeAfterPrepare { c -> c.seekTo(positionMs) }
    fun setSpeed(speed: Float) = executeAfterPrepare { c -> c.setPlaybackSpeed(speed) }
    
    private inline fun executeAfterPrepare(crossinline action: suspend (MediaController) -> Unit) {
        scope.launch {
            val c = awaitConnect() ?: return@launch
            if (maybePrepare(c)) { action(c) }
        }
    }
    
    private suspend fun maybePrepare(controller: MediaController): Boolean {
        val book = pendingBook?.let { ... } ?: return false
        val uri = book.filePath
        if (controller.currentMediaItem?.localConfiguration?.uri?.toString() == uri) return true
        controller.setMediaItem(MediaItem.fromUri(uri))
        controller.prepare()
        return true
    }
}
```

**Impact:** `PlaybackManager` is simplified — it no longer owns the controller. The new `PlayerController` handles ALL controller interactions, using Voice's `Deferred + awaitConnect + executeAfterPrepare` pattern.

### Change 2: Extract PlayStateManager (Already Done)

✅ Already created. It's wired into the controller listener but needs to be updated to work with the new `PlayerController`.

### Change 3: Keep Chapters on the Book Model, Not in StateFlow

**Current:** `PlaybackManager._chapters` + `PlayerScreen reads from PlaybackManager`

**Target:** `PlayerScreen` reads chapters from the `book` parameter (Room data) directly — no StateFlow needed.

```kotlin
// In PlayerScreen:
val chapters = remember(currentBook) {
    currentBook?.let { Chapter.deserializeList(it.chaptersJson) } ?: emptyList()
}
val currentChapterIndex = remember(chapters, displayPositionMs) {
    derivedStateOf { findChapterIndexForPosition(displayPositionMs, chapters) }
}.value
```

### Change 4: Add PositionUpdater (Extract from PlaybackManager)

**Current:** Position persistence is inside `PlaybackManager.startPositionTracker()` — a 500ms polling loop.

**Target:** A dedicated `PositionUpdater` attached to the ExoPlayer's `Player.Listener` (like Voice), not a polling loop.

```kotlin
class PositionUpdater(
    private val bookRepository: BookRepository,
    private val getCurrentBook: () -> Audiobook?
) : Player.Listener {
    override fun onPositionDiscontinuity(...) { flushPosition() }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == STATE_IDLE || state == STATE_ENDED) flushPosition()
    }
    
    private fun flushPosition() {
        val book = getCurrentBook() ?: return
        bookRepository.saveAudiobook(book)
    }
}
```

### Change 5: Simplify PlaybackManager — Keep Only Book State + Orchestration

After extracting the above components, `PlaybackManager` should only:
1. Hold `_currentBook` StateFlow
2. Coordinate between `PlayerController` and `PlayerScreen`
3. Manage sleep timer (it's fine where it is)

---

## 3. New Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AudioraApplication                           │
│                                                                     │
│  ├── PlayStateManager (Singleton)                                   │
│  │   └── StateFlow<PlayState> ← always available                    │
│  │                                                                  │
│  ├── PlayerController (New)                                         │
│  │   ├── Deferred<MediaController> + awaitConnect()                 │
│  │   ├── executeAfterPrepare { } ← every action goes through this  │
│  │   ├── playPause(), seekForward(), seekBack(), seekTo(), setSpeed()│
│  │   └── maybePrepare() ← auto-initializes if needed                │
│  │                                                                  │
│  ├── PlaybackManager (Simplified)                                   │
│  │   ├── currentBook: StateFlow<Audiobook?>                         │
│  │   ├── chapters: parsed from Room data                            │
│  │   ├── sleepTimer management                                      │
│  │   └── Orchestrates: PlayerController + PlayerScreen              │
│  │                                                                  │
│  ├── PositionUpdater (New)                                          │
│  │   ├── Attached to ExoPlayer via Player.Listener                  │
│  │   └── Flushes position on state changes, not polling             │
│  │                                                                  │
│  └── PlaybackService (MediaLibraryService)                          │
│      ├── Creates ExoPlayer + AudioraPlayer                          │
│      ├── Creates MediaLibrarySession                                │
│      ├── Attaches PlayStateDelegatingListener to ExoPlayer          │
│      ├── Attaches PositionUpdater to ExoPlayer                      │
│      └── Sets MediaNotificationProvider                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Component Comparison

| Component | Voice | Audiora (Current) | Audiora (Target) |
|-----------|-------|--------------------|--------------------|
| Controller wrapper | `PlayerController` with `Deferred` + `awaitConnect()` | `PlaybackManager` with nullable `var controller` | New `PlayerController` with `Deferred` + `awaitConnect()` |
| Play state | `PlayStateManager` singleton | `PlayStateManager` (created, needs wiring) | Keep as-is |
| Position persistence | `PositionUpdater` attached to Player.Listener | `startPositionTracker()` polling loop | New `PositionUpdater` attached to Player.Listener |
| Book state | `BookPlayViewModel` reads Room Flow | `PlaybackManager._currentBook` StateFlow | `PlaybackManager._currentBook` (simplified) |
| Chapters | Read from `Book` model directly | Read from `PlaybackManager._chapters` StateFlow | Read from `Book` model directly |
| Action retry | `executeAfterPrepare` → awaits + auto-prepares | `controller ?: return` → SILENT FAIL | `executeAfterPrepare` → awaits + auto-prepares |
| Screen state | `BookPlayViewState` from Room + PlayStateManager | 6 separate StateFlows + book parameter | Book parameter + PlayStateManager |
| DI | Metro scoped graphs | Manual constructor injection | Keep as-is |

---

## 5. Implementation Sequence

```
Step 1: Create PlayerController with Deferred<MediaController> + executeAfterPrepare
    ├── Extracts controller management from PlaybackManager
    ├── Mirrors Voice's awaitConnect() + maybePrepare() patterns
    └── All playback actions go through executeAfterPrepare
    
Step 2: Wire PlayStateManager to ExoPlayer directly
    ├── Attach PlayStateDelegatingListener to ExoPlayer in PlaybackService
    └── Remove _isPlaying from PlaybackManager
    
Step 3: Create PositionUpdater
    ├── Attach to ExoPlayer via Player.Listener
    ├── Save position on position discontinuity, state change, media item transition
    └── Remove 500ms polling loop from PlaybackManager
    
Step 4: Simplify PlaybackManager
    ├── Remove controller management (now in PlayerController)
    ├── Remove _currentPosition / _duration StateFlows (read from book model)
    ├── Remove _chapters StateFlow (read from book parameter)
    └── Keep only _currentBook + sleep timer + orchestration
    
Step 5: Update PlayerScreen
    ├── Read chapters from book parameter
    ├── Read play state from PlayStateManager
    └── Use PlayerController for all actions
```

---

## 6. Edge Cases and Safety

1. **Controller never connects**: `awaitConnect()` returns null → action is skipped gracefully.
2. **Controller disconnects mid-session**: `Deferred` auto-recreates → next action reconnects.
3. **Rapid button taps**: `executeAfterPrepare` uses coroutine scope → sequential execution.
4. **Book switch**: `maybePrepare()` detects URI mismatch → calls `setMediaItem` + `prepare`.
5. **App killed and reopened**: Fresh `Deferred<MediaController>` is created. `awaitConnect()` waits for service.
6. **Position lost**: `PositionUpdater` flushes on EVERY state change + periodic interval (not just polling).
