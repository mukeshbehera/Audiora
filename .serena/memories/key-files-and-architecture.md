# Key Files Reference

## Core Files
- `AudioraApplication.kt` — Manual DI: creates database, repositories, PlaybackManager, PlayStateManager
- `MainActivity.kt` — Entry point. `MainAppContainer` handles onboarding (when block), `MainAppShell` handles NavHost + bottom bar

## Domain Layer
- `Audiobook.kt` — Data class with id, filePath, title, author, durationMs, currentPositionMs, chaptersJson, skipSilence, volumeGain, playbackSpeed (per-book), lastPlayedAt
- `Chapter.kt` — title, startMs, endMs, durationMs, index. Has JSON serialization (serializeList/deserializeList)
- `Bookmark.kt` — id, bookId, positionMs, name, createdAt
- `BookRepository.kt` — Interface: getAudiobooks(), getAudiobook(id), saveAudiobook(), getBookmarks(), etc.

## Data Layer
- `BookEntity.kt` — Room entity mapping to `audiobooks` table. Has `toDomain()`/`fromDomain()` mappers
- `BookRepositoryImpl.kt` — Eagerly subscribes to Room on appScope, caches in `_audiobooks` StateFlow
- `M4bChapterExtractor.kt` — Extracts M4B chapter markers from MP4 atoms (ported from Voice)

## Player Feature (feature/player)
- `PlaybackManager.kt` — Central playback controller. All play/pause/seek/chapter/sleep/volume logic
- `PlaybackService.kt` — MediaLibraryService with ExoPlayer, AudioraPlayer wrapper, OnlyAudioRenderersFactory
- `AudioraPlayer.kt` — ForwardingPlayer: remaps seekToNext→seekForward, STATE_BUFFERING→STATE_READY
- `PlayStateManager.kt` — Singleton Playing/Paused state, independent of controller connection
- `PlayerScreen.kt` — Now Playing UI (1400 lines): cover, per-chapter slider, chapters, bookmarks, sleep timer, speed, volume
- `MediaItemsBuilder.kt` — Builds per-chapter MediaItems with ClippingConfiguration
- `PlaybackCommand.kt` — CustomCommand system for cross-process skipSilence/gain
- `ShakeDetector.kt` — Accelerometer shake detection for sleep timer reset
- `VolumeGain.kt` — LoudnessEnhancer wrapper
- `PlaybackItem.kt` — Chapter→index mapping
- `MiniPlayer.kt` — Mini player in bottom bar
- `PlaybackCommand.kt` — Bundle-based command encoding

## Tech Stack
- Kotlin, Jetpack Compose, Material3
- Media3 ExoPlayer (session + library service)
- Room (SQLite)
- Gradle 9.3.1, AGP 9.1.1
- Manual DI (no Hilt/Koin)
- Target SDK: latest, Min SDK: 26
