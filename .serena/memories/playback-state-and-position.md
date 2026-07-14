# Playback State & Position Data Flow

## Play/Pause State (isPlaying for UI)
- **PlayerScreen**: Reads `playStateManager.playStateFlow` → `Playing`/`Paused`
- **MiniPlayer**: Reads `playbackManager.isPlaying` StateFlow
- **Update path**: `setupControllerListener()` → `onPlaybackStateChanged()` and `onIsPlayingChanged()` both update `playStateManager.playState`

## Position Tracking
- **Primary source**: `_currentPosition` StateFlow set by `startPositionTracker()` loop
- **Tracker loop**: Uses `collectLatest` on `playStateFlow` — only runs when `Playing`. Computes `absolutePosition = chapter.startMs + clippedControllerPosition`.
- **Per-chapter slider**: PlayerScreen computes `chapterPosition = livePosition - currentChapter.startMs` for per-chapter progress bar
- **Bookmarks**: Use `livePosition` (absolute) for bookmark creation

## Position Persistence
- `saveCurrentPositionToDb()` saves `_currentPosition.value` + `_duration.value` (full book duration, NOT chapter duration)
- Saves on: pause (`onIsPlayingChanged(false)`), seek, and every ~2min during playback
- `ensureBookLoaded()` seeds `_currentPosition` from `book.currentPositionMs`

## Duration
- `_duration` StateFlow set from `book.durationMs` in `ensureBookLoaded()`/`playBook()`
- NOT synced from `activeController.duration` (returns per-chapter clipped duration)
