# Development History & Decision Log

## Phase 0 — Stability (commits b9e4ca9 + 0b530b4)
- **Goal**: Fix timing races and visual glitches without changing behavior
- **P0.1**: Position dual-source — PlayerScreen now uses `livePosition` (StateFlow) not Room-backed `currentBook?.currentPositionMs`. Eliminates slider snap-back.
- **P0.2**: STATE_BUFFERING→STATE_READY override in AudioraPlayer. Prevents seek flicker.
- **P0.3**: Removed redundant playStateManager updates from onIsPlayingChanged. Consolidated to onPlaybackStateChanged only.
- **P0.4**: STATE_ENDED and STATE_IDLE → Paused. Matches Voice's PlayStateDelegatingListener.

## Phase 1 — Efficiency (commit b9e4ca9)
- **P1.1**: Position tracker rewritten with collectLatest on playStateFlow. Loop auto-cancels on pause, restarts on resume.
- **P1.2**: DB save interval increased from 10s to ~2min (240 × 500ms).
- **P1.3**: OnlyAudioRenderersFactory — disables video/text/metadata/camera renderers.

## Phase 2 — Per-Chapter Architecture (commit 956ea00)
- **Core change**: One MediaItem per chapter with ClippingConfiguration instead of single MediaItem per book.
- playBook() uses setMediaItems(items, startIndex, positionInChapter).
- seekToChapter() → controller.seekTo(index, 0L). skipToNext/Previous → native navigation.
- EOC sleep timer moved to STATE_ENDED callback. Removed 1200ms polling hack.
- Position tracker reads currentMediaItemIndex. Absolute position = chapter.startMs + clippedPosition.

## Phase 3 — Feature Parity (commit c540fc4)
- **CustomCommand**: PlaybackCommand sealed interface with Bundle encoding. Replaces ExoPlayerInstance cross-process singleton.
- **Controller reconnection**: safeController getter detects dead controller, rebuilds.
- **Auto-rewind on pause**: togglePlayPause() seeks back by configured amount within current chapter.
- **Per-book speed**: playbackSpeed field on Audiobook. playBook() uses per-book value, falls back to global default.
- **lastPlayedAt tracking**: new field on Audiobook/BookEntity. Updated on every play().
- **Fade-out + shake-to-reset**: Volume fades over last 30s. After expiry, 30s accelerometer wait for shake to resume+restart timer.

## Post-Phase Bug Fixes
1. **LaunchedEffect null book**: LaunchedEffect captured `book` at composition when null. Fixed with `.filterNotNull().first()`.
2. **Play button icon**: onPlaybackStateChanged doesn't fire when prepare()→STATE_READY before play(). Restored playState update in onIsPlayingChanged.
3. **Library progress**: activeController.duration returns chapter duration. Removed _duration overwrites from controller reads.
4. **Listen Now navigation**: Added onNavigateToPlayer callback to AudiobookDetailScreen.
5. **Bottom nav splash flash** (5 attempts): 
   - Attempts 1-4: showBottomNav state gating (null guard, onboardingCompleted, popExitTransition, allowlist) — all failed due to structural overlap
   - Attempt 5 ✅: Moved splash OUTSIDE NavHost into when(phase) block. NavHost created only after onboarding completes.
6. **ExceptionInInitializerError**: Screen.tabRouteStrings accessed inside animation lambda. Fixed with eager local val evaluation.

## Code Review Findings
- lastPlayedAt was only set on first play (0L check) — fixed to update every play
- Auto-rewind position save race — saveCurrentPositionToDb() in onIsPlayingChanged fired before rewind completed. Fixed with explicit save after seek.
- Missing Bundle import in PlaybackService
- showBottomNav inclusion check too restrictive (hid bar on Details/Processing). Switched to exclusion.
- destination.route nullable in transition lambdas — added safe-call operators
