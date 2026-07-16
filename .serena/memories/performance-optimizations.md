# Performance Optimizations Applied

## Startup & Navigation Performance
1. **Activity-scoped ViewModels** — LibraryScreen, SearchScreen, SettingsScreen, EditScreen ViewModels scoped to ComponentActivity. Survive tab switches. No DataStore/Room re-reads on repeat visits.
2. **Localized playback state to MiniPlayer** — isPlaying, currentPosition, duration collected inside MiniPlayer. 500ms position updates don't recompose NavHost.
3. **Tab-switch animations**: Instant (`tween(0)`) for tab-to-tab, animated only for push navigation.
4. **Splash outside NavHost**: Eliminates bottom-bar flash during splash→library transition.
5. **EditScreen timeline deferred**: Canvas drawing (up to 80 drawLine calls) delayed by one frame via withFrameNanos.
6. **FolderRepositoryImpl in-memory cache**: Eager Room subscription mirrors BookRepositoryImpl pattern.
7. **StateFlow distinctUntilChanged removed**: Already guaranteed by StateFlow operator fusion.
8. **Room caching**: BookRepositoryImpl eagerly subscribes on appScope.

## Playback Performance
1. **OnlyAudioRenderersFactory**: Disables video/text/metadata/camera renderers.
2. **Conditional position tracker**: collectLatest on playStateFlow — only polls when playing.
3. **Reduced DB saves**: Position saved every ~2min (240 × 500ms) instead of ~10s.
4. **Per-chapter MediaItems**: Native ExoPlayer chapter transitions.

## Battery Optimization
1. **Position tracker stops when paused**: collectLatest auto-cancels inner loop.
2. **EOC sleep via callback**: STATE_ENDED callback replaces 1200ms polling hack.
