# Voice Media Notification Architecture — Analysis

## Overview
Voice uses Media3's `MediaLibraryService` with `DefaultMediaNotificationProvider` for media notifications. The notification is handled entirely by Media3 — Voice only extends it minimally.

## Key Components
- **PlaybackService** (MediaLibraryService) — service host, creates MediaLibrarySession
- **MediaLibrarySession** — holds player, handles controller connections
- **LibrarySessionCallback** — handles onAddMediaItems, onSetMediaItems, onGetLibraryRoot, onConnect, onCustomCommand
- **VoiceMediaNotificationProvider** — extends DefaultMediaNotificationProvider, sets compact view indices for Android <13
- **VoicePlayer** (ForwardingPlayer) — wraps ExoPlayer, remaps seekToNext/Previous → fastForward/rewind
- **PlayerController** — UI-side MediaController wrapper for play/pause/seek/skip
- **PlaybackModule** — DI module creating ExoPlayer, MediaLibrarySession, audio config
- **MediaItemProvider** — Creates MediaItems with rich metadata (title, artist, artworkUri, mediaType)
- **ImageFileProvider** — FileProvider-based cover art URIs with grantUriPermissions

## Key Insight
Voice relies entirely on Media3's `DefaultMediaNotificationProvider` for notification creation. The notification is **not custom-built** — Media3 handles everything: channel creation, notification building, foreground service lifecycle, action buttons, artwork display, and state updates. Voice only overrides `getMediaButtons()` to fix compact view indices on Android <13.

## Audiora's Current Gap
Audiora uses `MediaSessionService` (not `MediaLibraryService`) and never calls `setMediaNotificationProvider()`. Media3's `MediaSessionService` does NOT automatically show a notification — only `MediaLibraryService` with `DefaultMediaNotificationProvider` does. This is why no notification appears.

## Implementation Plan Summary
1. Upgrade media3 from 1.5.1 → 1.10.1
2. Create notification channel in Application.onCreate()
3. Create `VoiceMediaNotificationProvider` extending `DefaultMediaNotificationProvider`
4. Migrate `PlaybackService` from `MediaSessionService` → `MediaLibraryService`
5. Use `MediaLibrarySession` instead of `MediaSession`
6. Set rich MediaMetadata on MediaItems (title, artist, artworkUri)
7. Configure FileProvider for cover art
8. Update AndroidManifest (exported=true, MediaLibraryService intent filter)
9. Add ForwardingPlayer wrapper for seek command remapping
10. Wire `setMediaNotificationProvider()` and `setMediaButtonPreferences()`

The full analysis document is at `docs/voice-notification-architecture-analysis.md`.
