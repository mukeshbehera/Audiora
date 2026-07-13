# Audiora — Top-Level Source Map

## What It Is
Single-module Android audiobook player app. Jetpack Compose UI, Room DB, Media3 ExoPlayer, manual DI.

## Source Layout

```
com.audiora/
├── AudioraApplication.kt          # Manual DI container (no Hilt/Dagger)
├── MainActivity.kt                 # NavHost + MiniPlayer + bottom bar
├── core/
│   ├── design/GlassmorphismSystem.kt   # All glassmorphic UI components
│   └── navigation/Screen.kt            # Sealed class route definitions
├── data/
│   ├── local/                          # Room entities, DAOs, M4B extractor, storage import
│   └── repository/                     # Repository implementations
├── domain/
│   ├── model/                          # Audiobook, Chapter, Bookmark, PlaybackSettings, AudiobookFolder
│   ├── repository/                     # Repository interfaces
│   ├── usecase/                        # 11 use cases (folders, settings, theme)
│   └── util/PathUtils.kt              # SAF URI → display path
├── feature/
│   ├── converter/                      # M4B transcoding (merge files into M4B)
│   ├── detail/                         # Audiobook detail screen
│   ├── editor/                         # Metadata editor screen
│   ├── library/                        # Main library grid/list
│   ├── player/                         # PlaybackManager, PlayerScreen, MiniPlayer, PlaybackService, VolumeGain
│   ├── search/                         # Search screen
│   ├── settings/                       # Settings + folder management
│   └── welcome/                        # Splash, welcome, onboarding
└── ui/theme/                           # Color, Theme, Typography
```

## Key Invariants

- **No DI framework**: Manual constructor injection via `AudioraApplication` singletons. ViewModels use `ViewModelProvider.Factory` companions.
- **Single module**: `:app` only. No multi-module split despite clean architecture packages.
- **Room DB version 6**: `fallbackToDestructiveMigration()` — no migration scripts, prototyping mode.
- **Media3 ExoPlayer** via `MediaSessionService` (`PlaybackService`). `ExoPlayerInstance` object holds the player reference.
- **M4B chapter extraction** ported from Voice (PaulWoitaschek/Voice). See `mem:m4b_chapter_extraction`.
- **Metadata editing** via `jaudiotagger` library — writes directly to file tags (ID3/MP4).
- **SAF (Storage Access Framework)**: Content URIs used throughout. `StorageImportManager` handles persisted permissions.
- **Glassmorphism design system**: All UI components in `core/design/GlassmorphismSystem.kt` with backward-compat wrappers.
- **5 color schemes**: AUDIORA_PURPLE (default), DYNAMIC, CRIMSON_RED, OCEAN_BLUE, EMERALD_GREEN, SUNSET_ORANGE.
- **Theme modes**: SYSTEM (default), LIGHT, DARK.

## Key Architectural Decisions

- **Single-module**: `:app` only. Clean architecture via packages, not Gradle modules.
- **No DI framework**: `AudioraApplication` holds all singletons (database, repositories, playbackManager). ViewModels use `ViewModelProvider.Factory` companions.
- **In-memory cache**: `BookRepositoryImpl` eagerly subscribes to Room on `appScope` and mirrors data to a `StateFlow` — subscribers get instant values without DB delay.
- **M4B chapter extraction** ported from Voice. See `mem:m4b_chapter_extraction`.
- **Playback**: Media3 ExoPlayer via `MediaSessionService`. `ExoPlayerInstance` singleton holds the player reference. `PlaybackManager` wraps all player operations.
- **Metadata editing**: Uses `jaudiotagger` to write directly to file tags. Supports both content:// URIs (via temp file) and direct file paths.
- **SAF URIs**: Content URIs used throughout. `StorageImportManager` handles persisted permissions. `PathUtils.toDisplayPath()` converts URIs to human-readable paths.

## Package Map

- `core/design/` — GlassmorphismSystem.kt (all reusable UI components)
- `core/navigation/` — Screen sealed class (route definitions)
- `data/local/` — Room DB (AppDatabase, BookEntity, BookmarkEntity, FolderEntity, DAOs), M4bChapterExtractor, StorageImportManager
- `data/repository/` — BookRepositoryImpl, FolderRepositoryImpl, SettingsRepositoryImpl
- `domain/model/` — Audiobook, Chapter, Bookmark, PlaybackSettings, AudiobookFolder
- `domain/repository/` — BookRepository, FolderRepository, SettingsRepository interfaces
- `domain/usecase/` — 11 use cases (folders, settings, theme)
- `domain/util/PathUtils.kt` — SAF URI → display path conversion
- `feature/converter/` — M4B transcoding (merge audio files into M4B)
- `feature/detail/` — Audiobook detail screen + ViewModel
- `feature/editor/` — Metadata editor screen + ViewModel
- `feature/library/` — Library grid/list + ViewModel
- `feature/player/` — PlaybackManager, PlaybackService, PlayerScreen, MiniPlayer, VolumeGain
- `feature/search/` — Search screen + ViewModel
- `feature/settings/` — Settings screen, folder management, ViewModels
- `feature/welcome/` — Splash, welcome, onboarding folder selection
- `ui/theme/` — Color, Theme, Typography

## Key Invariants

- **Manual DI**: `AudioraApplication` holds all singletons. ViewModels use `ViewModelProvider.Factory` companions.
- **Single module**: Clean architecture via packages only.
- **Room v6**: `fallbackToDestructiveMigration()` — no migration scripts.
- **M4B chapter extraction** ported from Voice. See `mem:m4b_chapter_extraction`.
- **Glassmorphism design system**: All reusable components in `core/design/GlassmorphismSystem.kt`. Backward-compat wrappers (`GlassmorphicCard`, etc.) exist for old callers.
- **5 color schemes + dynamic**: AUDIORA_PURPLE (default), CRIMSON_RED, OCEAN_BLUE, EMERALD_GREEN, SUNSET_ORANGE, DYNAMIC (Android 12+).
- **SAF content URIs** used for file access. `StorageImportManager` persists URI permissions. `PathUtils.toDisplayPath()` converts URIs for display.
- **CI**: GitHub Actions — debug APK on push, signed release on GitHub release publish.
