# Audiora App Architecture (as of July 2026)

## Overview
Audiora is an Android audiobook player built with Jetpack Compose, Media3 ExoPlayer, and Room database. Manual DI via Application class.

## Key Architecture Decisions
- **Manual DI**: No Hilt/Koin. `AudioraApplication` creates and holds all singletons (database, repositories, PlaybackManager, PlayStateManager).
- **Room + StateFlow**: `BookRepositoryImpl` eagerly subscribes to Room on `appScope`, caching `audiobooks` in a `StateFlow<List<Audiobook>>` for instant reads.
- **Media3 MediaLibraryService**: Uses `MediaLibraryService` (not `MediaSessionService`) for automatic notification management.
- **Per-chapter MediaItems**: Each chapter gets its own `MediaItem` with `ClippingConfiguration(startMs, endMs)` for native chapter navigation.
- **PlayStateManager**: Singleton tracking `Playing`/`Paused` state independently of MediaController connection, enabling instant play/pause icon updates.
- **Voice Reference**: Architecture is ported from PaulWoitaschek/Voice audiobook player. Key patterns: `PositionUpdater`, `PlayStateDelegatingListener`, `CustomCommand`, per-chapter `MediaItem`.

## Navigation (as of latest fix)
- Splash/Welcome/Onboarding render OUTSIDE the NavHost via a `when(phase)` block in `MainAppContainer`
- `MainAppShell` contains NavHost + bottom bar overlay, created only after onboarding completes
- NavHost startDestination = `Screen.Library.route`
- Bottom nav uses exclusion list: hidden on splash/welcome/onboarding_folders/player routes

## Files to Know
- `PlaybackManager.kt` — Central hub: controller management, play/pause, chapter navigation, position tracking, sleep timer, position persistence
- `PlaybackService.kt` — MediaLibraryService with ExoPlayer, only audio renderers, custom commands
- `PlayerScreen.kt` — Now Playing UI with per-chapter slider, bookmarks, speed, sleep timer, volume boost
- `MainActivity.kt` — Entry point, `MainAppContainer` (onboarding) and `MainAppShell` (app shell)
- `MediaItemsBuilder.kt` — Builds per-chapter MediaItems with ClippingConfiguration
- `PlaybackCommand.kt` — Cross-process custom commands (SetSkipSilence, SetGain)
- `ShakeDetector.kt` — Accelerometer shake detection for sleep timer reset
- `Screen.kt` — Navigation routes, tab items, tabRouteStrings
