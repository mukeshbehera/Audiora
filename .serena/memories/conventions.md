# Code Conventions

## Architecture & DI
- **Manual DI**: `AudioraApplication` holds all singletons (database, repositories, playbackManager). ViewModels use `ViewModelProvider.Factory` companions with `provideFactory()` static method.
- **Clean architecture by package**: `data/` → `domain/` → `feature/` packages, not Gradle modules.
- **Repository pattern**: Interfaces in `domain/repository/`, implementations in `data/repository/`.
- **Use cases**: Thin wrappers in `domain/usecase/` for settings/folder operations.

## Naming
- **Packages**: `com.audiora.*` — flat structure under feature/data/domain/core/ui
- **Room entities**: `*Entity` suffix (BookEntity, BookmarkEntity, FolderEntity)
- **DAOs**: `*Dao` suffix
- **ViewModels**: `*ViewModel` suffix
- **Use cases**: Verb-first (`AddFolderUseCase`, `GetFoldersUseCase`)
- **Screens**: `*Screen` composable suffix

## Code Style
- **Manual DI**: `AudioraApplication` holds all singletons. ViewModels expose `provideFactory()` companion.
- **State management**: `StateFlow` + `collectAsStateWithLifecycle()` in Compose.
- **Repository pattern**: Interfaces in `domain/repository/`, implementations in `data/repository/`.
- **Entity ↔ Domain mapping**: Each Room entity has `toDomain()` and `fromDomain()`.
- **No Hilt/Dagger**: Manual constructor injection throughout.
- **No Jetpack Navigation type-safe args**: Routes are string-based with `navArgument` blocks.
- **Timber logging**: `Timber.d()`/`Timber.e()` throughout. Debug tree in debug builds, no-op in release.
- **Compose**: `collectAsStateWithLifecycle()` for StateFlow collection. `LaunchedEffect` for side effects.
- **No Jetpack Navigation type-safe args**: Routes are string-based with `navArgument` blocks.
- **Timber logging**: `Timber.d()`/`Timber.e()` throughout. Debug tree in debug builds, no-op in release.
- **Compose**: `collectAsStateWithLifecycle()` for StateFlow collection. `LaunchedEffect` for side effects.
