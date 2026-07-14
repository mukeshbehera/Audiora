# Current Branch Status (July 2026)

## Branch: `fix/functional-fix`
**PR:** #4 — to be merged into `main`
**Status:** All features complete, CI passes, app runs without crashes

## What changed (vs main)
- 23 commits ahead of main
- 6 new files created
- Full architecture transformation from single MediaItem to per-chapter MediaItems
- Voice feature parity achieved

## Completed Phases
- **Phase 0**: Stability fixes (position dual-source, STATE_BUFFERING→READY, play state consolidation)
- **Phase 1**: Efficiency (conditional tracker, reduced DB writes, OnlyAudioRenderersFactory)
- **Phase 2**: Per-chapter MediaItems with ClippingConfiguration
- **Phase 3**: CustomCommands, fade-out + shake timer, per-book speed, auto-rewind on pause, lastPlayedAt tracking, controller reconnection

## Known Issues
- None. All reported bugs fixed and verified.

## New Files (6)
1. `OnlyAudioRenderersFactory.kt` — No video/text decoder allocation
2. `PlaybackItem.kt` — Chapter→index mapping
3. `MediaItemsBuilder.kt` — Per-chapter MediaItem construction
4. `PlaybackCommand.kt` — Cross-process custom commands
5. `ShakeDetector.kt` — Accelerometer-based shake gesture detection
6. `Audiobook.kt`/`BookEntity.kt` — Added playbackSpeed, lastPlayedAt fields
