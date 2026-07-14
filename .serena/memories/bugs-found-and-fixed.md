# Bugs Found & Fixed

## Critical Bugs Fixed

### 1. LaunchedEffect captured null book (MainActivity.kt)
**Symptom:** Library→Player screen opened but no chapters loaded, buttons didn't work.
**Root cause:** `LaunchedEffect(bookId) { book?.let { ensureBookLoaded(it) } }` captured `book` at composition time when it was `null` (Room hadn't emitted yet). Never re-fired when `book` updated.
**Fix:** `app.bookRepository.getAudiobook(bookId).filterNotNull().first().let { ensureBookLoaded(it) }` — suspends until Room responds.

### 2. Play button not changing to pause
**Symptom:** Audio plays but UI shows play icon.
**Root cause:** `prepare()` reaches STATE_READY before `play()`. `play()` changes `playWhenReady` without state transition → `onPlaybackStateChanged` doesn't fire → `playStateManager` stays Paused. Phase 0 consolidation removed playState update from `onIsPlayingChanged`.
**Fix:** Restored `playStateManager.playState = if (isPlayingChanged) Playing else Paused` in `onIsPlayingChanged`.

### 3. Library progress showing per-chapter percentage
**Symptom:** Progress bar showed chapter completion instead of book completion.
**Root cause:** `activeController.duration` returns clipped chapter duration with per-chapter MediaItems. Was written to `_duration.value` in 3 locations (setupControllerListener, onPlaybackStateChanged, tracker loop). `saveCurrentPositionToDb()` then saved chapter duration.
**Fix:** Removed all `_duration` overwrites from controller reads. `_duration` only set from `book.durationMs`.

### 4. "Listen Now" opens MiniPlayer not Now Playing
**Root cause:** `AudiobookDetailScreen` had no `onNavigateToPlayer` callback. Called `playBook()` directly without navigating to PlayerScreen.
**Fix:** Added `onNavigateToPlayer: (Int) -> Unit` parameter, wired in NavHost.

### 5. Bottom nav bar flashing during splash
**Root cause (after 4 failed attempts):** Splash was inside NavHost inside Box overlay with bottom bar. During navigation transition, bottom bar overlay rendered on top of still-visible splash.
**Fix (structural):** Moved splash/welcome/onboarding OUTSIDE NavHost into a `when(phase)` block. NavHost created only after onboarding completes.

### 6. ExceptionInInitializerError in NavHost transition
**Root cause:** `Screen.tabRouteStrings` accessed inside animation lambda, causing class loading during animation frame.
**Fix:** Eagerly evaluate into local `val tabRouteSet = Screen.tabRouteStrings` during composition, before NavHost lambdas execute.
