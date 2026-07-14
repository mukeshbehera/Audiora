# Audiobook Loading Performance — Analysis

## Key Insight
Voice achieves instant loading because navigating to the playback screen does NOT re-initialize the player. The player is a singleton running in `PlaybackService`. The screen just READS state from reactive Room Flows + a singleton `PlayStateManager`.

## Voice's Architecture
- `BookPlayViewModel` reads `bookRepository.flow(bookId)` → Room Flow emits cached book instantly
- Position comes from `book.content.positionInChapter` (DB data), not from player state
- `PlayStateManager` is a singleton `@SingleIn(AppScope::class)` StateFlow — always available
- No `play()`, `prepare()`, `setMediaItem()` on navigation — player is already running
- `retain()` keeps ViewModel alive across recompositions

## Audiora's Problems
- `playBook()` in `LaunchedEffect` calls `controller.stop()`, `setMediaItem()`, `prepare()`, `play()` on every navigation
- `_currentPosition` starts at 0 and corrects when position tracker fires → progress bar jumps
- Book fetched via one-shot `first()` suspend query, not reactive Room Flow
- Separate `_isPlaying`, `_currentPosition`, `_duration` StateFlows cause multiple recompositions

## Implementation Plan (4 phases)
1. Stop re-initializing player on nav — add `ensureBookLoaded()` that's a no-op if player is already set
2. Create singleton `PlayStateManager` for instant play/pause state
3. Read position from Room Flow directly — not from position tracker StateFlow
4. Single `PlayerViewState` object to reduce recompositions

Full analysis at `docs/audiobook-loading-performance-analysis.md`