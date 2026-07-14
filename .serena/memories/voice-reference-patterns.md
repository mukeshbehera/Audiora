# Voice Reference Architecture

Voice audiobook player (PaulWoitaschek/Voice) is the reference architecture. The following patterns were ported:

## Ported Patterns
1. **PlayStateManager** — Singleton play/pause tracker independent of MediaController. Updated via `PlayStateDelegatingListener` (listens to BOTH `onPlaybackStateChanged` AND `onPlayWhenReadyChanged`).
2. **PositionUpdater** — Position tracking via `collectLatest` on `playStateFlow`. Only polls when playing. Saves position on pause/seek/transition. Replaces blind while(true) loop.
3. **Per-Chapter MediaItems** — Each chapter/chapterMark gets a `MediaItem` with `ClippingConfiguration`. Native `onPositionDiscontinuity(AUTO_TRANSITION)` for chapter boundaries.
4. **CustomCommand** — Typed commands via `MediaController.sendCustomCommand()` for cross-process skipSilence/gain. Replaces `ExoPlayerInstance` singleton.
5. **OnlyAudioRenderersFactory** — Disables video/text/metadata renderers for audiobook-only playback.
6. **ForwardingPlayer** — `AudioraPlayer` wraps ExoPlayer, remaps notification seek-to-next/previous to seek-forward/back.
7. **setMediaItems()** — Atomic positioning: `controller.setMediaItems(items, startIndex, positionInChapterMs)` instead of separate `setMediaItem()` + `seekTo()`.
8. **SleepTimerImpl** — Fade-out volume over 30s, shake-to-reset gesture detection.
9. **VolumeGainSetter** — Robust LoudnessEnhancer management with session tracking.

## Key Differences
- Voice uses DI (Metro/Anvil), Audiora uses manual DI
- Voice has Chapter→ChapterMark hierarchy, Audiora has flat Chapter model
- Voice uses kotlinx.serialization for MediaId, Audiora uses Bundle-based encoding for PlaybackCommand
