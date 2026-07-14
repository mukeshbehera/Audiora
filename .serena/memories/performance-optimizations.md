# Performance Optimizations Applied

## Startup & Navigation Performance
1. **Tab-switch animations**: Instant (`tween(0)`) for tab-to-tab, animated only for push navigation (Library→Player/Details)
2. **Splash outside NavHost**: Eliminates bottom-bar flash during splash→library transition
3. **StateFlow distinctUntilChanged removed**: StateFlow already guarantees distinct emissions via operator fusion
4. **Room caching**: `BookRepositoryImpl` eagerly subscribes to Room on appScope, caching in StateFlow

## Playback Performance
1. **OnlyAudioRenderersFactory**: Disables video/text/metadata/camera renderers — saves decoder resources
2. **Conditional position tracker**: Uses `collectLatest` on `playStateFlow` — only polls when playing
3. **Reduced DB saves**: Position saved every ~2min (240 × 500ms) instead of every ~10s
4. **Per-chapter MediaItems**: Native ExoPlayer chapter transitions instead of manual calculation + polling

## Battery Optimization
1. **Position tracker stops when paused**: `collectLatest` auto-cancels inner loop when `playState` becomes `Paused`
2. **EOC sleep via callback**: `STATE_ENDED` callback replaces 1200ms polling hack
