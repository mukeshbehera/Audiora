# Voice Media Notification Architecture — Analysis & Implementation Plan for Audiora

## 1. Voice's Media Notification Architecture

### 1.1 Overview

Voice uses **Media3's `MediaLibraryService`** (not plain `MediaSessionService`) to provide a full media notification experience. The notification itself is handled entirely by Media3's `DefaultMediaNotificationProvider`, which Voice extends minimally.

### 1.2 Key Components & Interaction Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        PlaybackService                           │
│  (MediaLibraryService)                                          │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  MediaLibrarySession (MediaSession)                      │    │
│  │  - setSessionActivity(PendingIntent → MainActivity)       │    │
│  │  - setMediaButtonPreferences(rewind, ffwd)               │    │
│  │  - setMediaNotificationProvider(VoiceMediaNotificationProvider)│
│  │  - Callback: LibrarySessionCallback                        │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  VoiceMediaNotificationProvider                          │    │
│  │  extends DefaultMediaNotificationProvider                │    │
│  │  - Overrides getMediaButtons() to set compact view indices│    │
│  │  - All notification creation handled by Media3 internally │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  VoicePlayer (ForwardingPlayer)                           │    │
│  │  - Wraps ExoPlayer                                        │    │
│  │  - Overrides seekToNext/Previous → fastForward/rewind    │    │
│  │  - Advertises all seek commands as available              │    │
│  └──────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 Key Classes and Their Roles

| Class | Role |
|-------|------|
| `PlaybackService` (MediaLibraryService) | Service host. Creates session, sets notification provider, injects dependencies via Metro |
| `MediaLibrarySession` | Media3 session. Holds player reference, handles controller connections |
| `LibrarySessionCallback` | Handles all session callbacks: `onAddMediaItems`, `onSetMediaItems`, `onGetLibraryRoot`, `onConnect`, `onCustomCommand` |
| `VoiceMediaNotificationProvider` | Extends `DefaultMediaNotificationProvider` — only overrides `getMediaButtons()` to set compact view indices for Android <13 |
| `VoicePlayer` (ForwardingPlayer) | Wraps ExoPlayer. Overrides seekToNext/Previous → fastForward/rewind. Advertises all seek commands as available |
| `PlayerController` | UI-side controller. Connects to `MediaController`, provides `playPause()`, `fastForward()`, `rewind()`, `previous()`, `next()` |
| `PlaybackModule` | DI module. Creates ExoPlayer, MediaLibrarySession, configures audio attributes, wake mode, audio offload |
| `MainActivityIntentProvider` | Interface for creating PendingIntent to MainActivity |
| `PlayStateManager` | Simple `StateFlow<PlayState>` (Playing/Paused) |
| `PlayStateDelegatingListener` | Player.Listener that syncs player state → PlayStateManager |
| `PositionUpdater` | Persists playback position periodically and on state changes |
| `MediaItemProvider` | Creates MediaItem objects with metadata (title, artist, artwork URI) |
| `ImageFileProvider` | Provides content:// URIs for cover art via FileProvider |

### 1.4 Notification Flow

```
1. PlaybackService.onCreate()
   ├── Creates ExoPlayer (via PlaybackModule)
   ├── Creates MediaLibrarySession with:
   │   ├── VoicePlayer (ForwardingPlayer wrapping ExoPlayer)
   │   ├── LibrarySessionCallback
   │   ├── setSessionActivity(PendingIntent → MainActivity)
   │   └── setMediaButtonPreferences(rewind, ffwd buttons)
   └── setMediaNotificationProvider(VoiceMediaNotificationProvider)

2. Media3 automatically:
   ├── Creates notification channel (if not exists)
   ├── Builds notification with MediaStyle
   ├── Adds play/pause, prev, next actions
   ├── Shows album artwork from MediaMetadata.artworkUri
   ├── Updates notification on player state changes
   └── Manages foreground/background lifecycle

3. VoiceMediaNotificationProvider.getMediaButtons():
   └── Sets compact view indices for Android <13 (issue #1904)

4. VoicePlayer (ForwardingPlayer):
   ├── Overrides seekToNextMediaItem() → seekForward()
   ├── Overrides seekToPreviousMediaItem() → seekBack()
   ├── Overrides seekToNext() → seekForward()
   ├── Overrides seekToPrevious() → seekBack()
   ├── Advertises all seek commands as available (for Android 13+ notification)
   └── Overrides getPlaybackState() → redirects BUFFERING to READY
```

### 1.5 Key Design Decisions in Voice

1. **MediaLibraryService** (not MediaSessionService) — enables browsable media hierarchy for Android Auto/Wear OS
2. **ForwardingPlayer** pattern — VoicePlayer wraps ExoPlayer to intercept seek commands (prev/next → rewind/ffwd)
3. **No custom notification building** — relies entirely on `DefaultMediaNotificationProvider` from Media3
4. **MediaItem metadata** — artwork URI, title, artist set on MediaItem → automatically shown in notification
5. **Session activity** — PendingIntent to MainActivity with `goToBookIntent()`
6. **Custom commands** — JSON-serialized commands via `sendCustomCommand()` for force seek, skip silence, gain
7. **DI via Metro** — `PlaybackGraph` extension pattern for scoped injection into the service

---

## 2. Audiora's Current Architecture

### 2.1 Current State

| Component | Status |
|-----------|--------|
| `PlaybackService` (MediaSessionService) | ✅ Exists — basic setup with ExoPlayer + MediaSession |
| `PlaybackManager` | ✅ Exists — manages MediaController, state flows, playback actions |
| `ExoPlayerInstance` (object) | ✅ Exists — process-safe singleton holding ExoPlayer reference |
| `VolumeGain` | ✅ Exists — LoudnessEnhancer wrapper |
| `PlayerScreen` (Compose) | ✅ Exists — full player UI |
| `MiniPlayer` (Compose) | ✅ Exists — mini player overlay |
| Media notification | ❌ **Missing** — no notification appears during playback |
| `MediaLibraryService` | ❌ Using `MediaSessionService` instead |
| `MediaLibrarySession` | ❌ Using plain `MediaSession` |
| `VoiceMediaNotificationProvider` | ❌ Not implemented |
| Notification channel | ❌ Not created |
| `setMediaNotificationProvider()` | ❌ Not called |
| Cover art FileProvider | ❌ Not configured |

### 2.2 Key Differences Between Voice and Audiora

| Aspect | Voice | Audiora |
|--------|-------|---------|
| **Service type** | `MediaLibraryService` | `MediaSessionService` |
| **Session type** | `MediaLibrarySession` | `MediaSession` |
| **DI framework** | Metro (Dagger-free) | Manual constructor injection |
| **Notification provider** | Custom `VoiceMediaNotificationProvider` extending `DefaultMediaNotificationProvider` | None (no notification) |
| **Player wrapper** | `VoicePlayer` (ForwardingPlayer) | Direct ExoPlayer access via `ExoPlayerInstance` singleton |
| **Player controller** | `PlayerController` (MediaController wrapper) | `PlaybackManager` (MediaController wrapper) |
| **MediaItem metadata** | Rich: title, artist, artworkUri, mediaType, duration | Minimal: just URI |
| **Artwork** | FileProvider-based URI for cover art | Not set on MediaItem |
| **Session activity** | `MainActivityIntentProvider` → `goToBookIntent()` | PendingIntent to MainActivity with EXTRA_NAVIGATE_TO_PLAYER |
| **Notification provider** | Custom `VoiceMediaNotificationProvider` | None (default) |
| **Notification channel** | Handled by Media3's DefaultMediaNotificationProvider | Not created |
| **Foreground service** | Automatic via Media3 | Not started |
| **Media3 version** | 1.10.1 | 1.5.1 |

---

## 3. Implementation Plan for Audiora

### 3.1 Required Changes Summary

| # | Change | Priority | Dependencies |
|---|--------|----------|-------------|
| 1 | Upgrade media3 to 1.10.1 | High | None |
| 2 | Migrate to `MediaLibraryService` + `MediaLibrarySession` | High | #1 |
| 3 | Add `VoiceMediaNotificationProvider` equivalent | High | #2 |
| 4 | Create notification channel | High | #2 |
| 5 | Add `ForwardingPlayer` wrapper | Medium | #1 |
| 6 | Set MediaItem metadata (title, artist, artwork) | High | #2 |
| 7 | Configure FileProvider for cover art | Medium | #6 |
| 8 | Add `setMediaNotificationProvider()` call | High | #3 |
| 9 | Add `setMediaButtonPreferences()` for rewind/ffwd | Medium | #2 |
| 10 | Handle notification tap → navigate to player | High | #2 |
| 11 | Update AndroidManifest for MediaLibraryService | High | #2 |

---

## 3. Detailed Implementation Plan

### Phase 1: Upgrade media3 (Prerequisite)

**Why:** Audiora uses media3 1.5.1. Voice uses 1.10.1. The `DefaultMediaNotificationProvider` and `MediaLibraryService` APIs are more mature in later versions. The `setMediaNotificationProvider()` method and `MediaLibrarySession.Builder` require 1.6+.

**Action:**
- Update `gradle/libs.versions.toml`: `media3 = "1.10.1"` (match Voice's version)
- Add `media3-session` dependency (already present)
- No other dependency changes needed

**Risk:** API changes between 1.5.1 and 1.10.1 — verify `MediaSessionService` → `MediaLibraryService` migration is compatible.

### Phase 2: Migrate PlaybackService to MediaLibraryService

**Current:** `PlaybackService extends MediaSessionService` with plain `MediaSession`
**Target:** `PlaybackService extends MediaLibraryService` with `MediaLibrarySession`

**Changes to `PlaybackService.kt`:**

```kotlin
// Change parent class
class PlaybackService : MediaLibraryService() {
    // Change session type
    private var mediaSession: MediaLibrarySession? = null
    
    override fun onCreate() {
        super.onCreate()
        // ... ExoPlayer creation (keep) ...
        
        mediaSession = MediaLibrarySession.Builder(this, ExoPlayerInstance.player!!, callback)
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                        .setDisplayName("Rewind")
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setSlots(CommandButton.SLOT_BACK)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                        .setDisplayName("Fast Forward")
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .build(),
                )
            )
            .build()
        
        // Set notification provider
        setMediaNotificationProvider(VoiceMediaNotificationProvider(this))
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }
}
```

### Phase 3: Create VoiceMediaNotificationProvider

**New file:** `app/src/main/java/com/audiora/feature/player/VoiceMediaNotificationProvider.kt`

```kotlin
class VoiceMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
            .apply {
                forEachIndexed { index, commandButton ->
                    commandButton.extras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
                }
            }
    }
}
```

### Phase 3: Create Notification Channel

**Add to `AudioraApplication.kt`:**

```kotlin
class AudioraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // ... existing init ...
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "media_playback",
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
```

### Phase 4: Set MediaItem Metadata

**In `PlaybackManager.kt` — `playBook()` method:**

Currently, Audiora creates a bare `MediaItem.fromUri(uriToPlay)`. It needs to set metadata:

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(uriToPlay)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.author)
            .setArtworkUri(book.coverUri)  // FileProvider URI
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .build()
    )
    .build()
```

### Phase 5: Configure FileProvider for Cover Art

**New XML:** `app/src/main/res/xml/file_paths.xml`
```xml
<paths>
    <cache-path name="covers" path="covers/" />
</paths>
```

**Update AndroidManifest:**
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.coverprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### Phase 6: Update AndroidManifest

```xml
<service
    android:name=".feature.player.PlaybackService"
    android:enabled="true"
    android:exported="true"   <!-- Change from false to true -->
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

### Phase 7: Add ForwardingPlayer Wrapper (Optional Enhancement)

**New file:** `app/src/main/java/com/audiora/feature/player/AudioraPlayer.kt`

```kotlin
class AudioraPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .addAll(
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }
    
    override fun seekToPreviousMediaItem() { seekBack() }
    override fun seekToNextMediaItem() { seekForward() }
    override fun seekToPrevious() { seekBack() }
    override fun seekToNext() { seekForward() }
    
    // ... delegate to PlaybackManager for actual seek logic
}
```

### Phase 8: Wire Everything Together

**Update `PlaybackService.onCreate()`:**
1. Create ExoPlayer (existing)
2. Wrap in `AudioraPlayer` (ForwardingPlayer)
3. Create `MediaLibrarySession` with `AudioraPlayer`
4. Set `setMediaNotificationProvider(VoiceMediaNotificationProvider)`
5. Set `setMediaButtonPreferences()` for rewind/ffwd
6. Set `setSessionActivity()` with PendingIntent

---

## 4. Architectural Differences to Address

| Concern | Voice | Audiora | Impact |
|---------|-------|---------|--------|
| **DI** | Metro (scoped graphs) | Manual constructor injection | Audiora needs to wire dependencies manually in PlaybackService |
| **Player wrapper** | VoicePlayer (ForwardingPlayer) wraps ExoPlayer at creation | ExoPlayerInstance singleton accessed directly | Need to wrap ExoPlayer in ForwardingPlayer before building MediaSession |
| **MediaItem metadata** | Rich metadata set via MediaItemBuilder | Bare URI only | Must add MediaMetadata to MediaItem in playBook() |
| **Cover art** | FileProvider with grantUriPermissions | Not implemented | Need FileProvider config + cover URI logic |
| **Notification channel** | Handled by DefaultMediaNotificationProvider | Not created | Must create channel in Application.onCreate() |
| **Service export** | `exported="true"` | `exported="false"` | Must change for Android Auto/Wear OS |
| **Media3 version** | 1.10.1 | 1.5.1 | Must upgrade (API differences) |

## 5. Components to Reuse, Modify, or Create

### Reuse (no changes needed)
- `PlaybackManager` — state flows, play/pause/seek/speed/skip logic
- `VolumeGain` — LoudnessEnhancer wrapper
- `PlayerScreen` (Compose) — player UI
- `MiniPlayer` (Compose) — mini player overlay
- `ExoPlayerInstance` — singleton pattern

### Modify
- **`PlaybackService.kt`** — Change to `MediaLibraryService`, use `MediaLibrarySession`, add `VoiceMediaNotificationProvider`, add `setMediaButtonPreferences()`
- **`PlaybackManager.kt`** — Add MediaMetadata to MediaItem in `playBook()`, add cover art URI
- **`AudioraApplication.kt`** — Add notification channel creation
- **`AndroidManifest.xml`** — Update service declaration for MediaLibraryService

### New Files to Create
- **`VoiceMediaNotificationProvider.kt`** — Extends `DefaultMediaNotificationProvider`
- **`file_paths.xml`** — FileProvider paths for cover art
- **`AudioraPlayer.kt`** (optional) — ForwardingPlayer wrapper for seek command remapping

---

## 6. Implementation Sequence

```
Phase 1: Upgrade media3 to 1.10.1
    ↓
Phase 2: Create notification channel in AudioraApplication
    ↓
Phase 3: Create VoiceMediaNotificationProvider
    ↓
Phase 4: Update PlaybackService → MediaLibraryService + MediaLibrarySession
    ↓
Phase 5: Add MediaMetadata to MediaItem in PlaybackManager.playBook()
    ↓
Phase 6: Configure FileProvider for cover art
    ↓
Phase 7: Update AndroidManifest (exported=true, MediaLibraryService intent filter)
    ↓
Phase 8: Add AudioraPlayer (ForwardingPlayer) for seek command remapping
    ↓
Phase 9: Test notification appears, actions work, artwork shows
```

### Dependencies Between Components

```
media3 upgrade ──→ PlaybackService migration ──→ VoiceMediaNotificationProvider
                        │                                │
                        ↓                                ↓
              MediaLibrarySession              DefaultMediaNotificationProvider
              setSessionActivity()            (handles notification lifecycle)
              setMediaButtonPreferences()
                        │
                        ↓
              PlaybackManager.update
              (MediaItem metadata)
                        │
                        ↓
              FileProvider config
              (cover art URIs)
```

---

## 6. Validation & Testing Strategy

### Manual Testing Checklist
1. **Notification appears** — Start playback, verify notification shows in status bar
2. **Play/Pause** — Toggle via notification, verify state syncs
3. **Skip forward/backward** — Verify notification buttons trigger correct seek amounts
4. **Cover art** — Verify artwork displays in notification (expandable)
5. **Notification tap** — Tap notification → app opens to player screen
6. **Lock screen** — Verify controls appear on lock screen (Android <13)
7. **Background playback** — Verify notification persists when app is backgrounded
8. **Multiple books** — Switch books, verify notification updates
9. **Android 13+** — Verify compact notification shows 3 buttons (prev, play/pause, next)
10. **Android <13** — Verify compact notification shows prev/next icons (Voice issue #1904 fix)

### Edge Cases
- **No cover art** — Notification should still show with default icon
- **Service killed** — Verify MediaController reconnection
- **Multiple rapid play/pause** — No ANR or duplicate notifications
- **App in background** — Notification persists, controls work
- **Device rotation** — No notification duplication

---

## 7. Risks and Compatibility Considerations

1. **media3 1.5.1 → 1.10.1 jump** — API surface changes. `MediaLibraryService` was introduced in 1.6.0. Verify `MediaSessionService` → `MediaLibraryService` migration is backward-compatible.
2. **`MediaSessionService` vs `MediaLibraryService`** — `MediaLibraryService` requires implementing `onGetSession()` returning `MediaLibrarySession?` (not `MediaSession?`). The `MediaLibrarySession` requires a `MediaLibrarySession.Callback`.
3. **`setMediaNotificationProvider()`** — Available from media3 1.6.0+. Audiora is on 1.5.1, so the upgrade is mandatory.
4. **`DefaultMediaNotificationProvider`** — Handles notification channel creation automatically, but only if `createNotificationChannel()` is called. Verify this works on API 26+.
5. **`ForwardingPlayer`** — Available from media3 1.0.0+. No compatibility issues.
6. **`MediaLibraryService` requires `exported="true"`** — Security implication: the service becomes accessible to other apps. Voice mitigates this with `tools:ignore="ExportedService"`.
7. **Cover art FileProvider** — Must grant URI permissions to system UI and other packages for notification artwork to display (see Voice's `ImageFileProvider`).
8. **media3 1.5.1 → 1.10.1** — Check for breaking API changes in `MediaSession.Builder`, `MediaItem.Builder`, `CommandButton`.
