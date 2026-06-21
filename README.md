<div align="center">
  <h1 style="font-size: 3em; margin-bottom: 0;">🎧 Audiora</h1>
  <p><em>A modern, elegant audiobook player for Android — built with Jetpack Compose</em></p>
  <br/>
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Compose" src="https://img.shields.io/badge/Compose-BOM%202024.09-4285F4?logo=jetpackcompose&logoColor=white" />
  <img alt="API" src="https://img.shields.io/badge/Min%20API-24-FF6D00?logo=android&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg" />
  <br/><br/>
</div>

## ✨ Overview

**Audiora** is a beautifully crafted Android audiobook player that combines a premium glassmorphic design with powerful features. Import your M4B audiobooks, organize them into a personal library, edit metadata and chapters, and enjoy a seamless listening experience with background playback, chapters navigation, bookmarks, and a sleep timer.

Built entirely with **Kotlin** and **Jetpack Compose** using **Material 3** design language, Audiora delivers a modern, fluid experience across both light and dark themes.

---

## 🚀 Features

### 📚 Library Management
- **Grid & List Views** — Browse your audiobooks in a visual grid or detailed list
- **Search** — Instantly filter by title, author, or narrator
- **Genre Filters** — Narrow down by genre tags
- **Sort Options** — Sort by recently added, title, or duration
- **Continue Listening** — Jump back into your current book from the home screen
- **Progress Tracking** — Automatic position saving with auto-rewind on resume

### 🎵 Playback
- **Media3 ExoPlayer** — High-quality audio playback with background support
- **MediaSession Service** — Full notification controls, lock screen playback, and wearable support
- **Chapter Navigation** — Skip between chapters with prev/next controls
- **Variable Speed** — Adjust playback speed from 0.5x to 3.0x
- **Custom Skip Amounts** — Configurable forward/backward skip duration
- **Sleep Timer** — Set a timer to stop playback after a duration or at chapter end
- **Auto-Rewind** — Configurable rewind seconds when resuming a book

### 🎨 Design
- **Glassmorphic UI** — Frosted glass cards, buttons, text fields, and bottom navigation
- **Material 3** — Modern Material Design with dynamic theming
- **Custom Color Schemes** — Choose from 6 color palettes: Audiora Purple, Dynamic, Crimson Red, Ocean Blue, Emerald Green, and Sunset Orange
- **Light & Dark Themes** — Automatic system-based or manual theme switching
- **Animated Transitions** — Smooth fade and scale transitions between screens
- **Gradient Cover Art** — Auto-generated beautiful gradient covers for books without artwork

### ✏️ Metadata Editor
- **Edit Tags** — Modify title, author, narrator, publisher, genre, year, description, and more
- **Cover Art** — Change or extract embedded cover images
- **Chapter Editor** — Visual timeline with drag handles to create and organize chapters
- **M4B Tagging** — Writes metadata directly to M4B files using jaudiotagger

### 🔄 Audio Converter
- **Create M4B Files** — Merge multiple audio files into a single M4B audiobook
- **Step-by-Step Wizard** — Select source files, enter metadata, choose cover art, configure chapters
- **Smart Transcoding** — Automatic transcoding with fallback to raw copy

### 🔖 Bookmarks
- **Save Positions** — Bookmark any position in a book with a custom name
- **Quick Navigation** — Jump to any saved bookmark instantly

### ⚙️ Settings
- **Appearance** — Theme mode and color scheme selection
- **Playback** — Default speed, skip amount, auto-rewind, sleep timer default
- **Folder Management** — Add, remove, and reorder scan folders
- **Data Persistence** — All settings saved via DataStore Preferences

---

## 🏗️ Architecture

Audiora follows **Clean Architecture** principles with a clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │  Screens  │  │ViewModels│  │   UI Components      │  │
│  │ (Compose) │  │ (State)  │  │ (Glassmorphic Design)│  │
│  └────┬─────┘  └────┬─────┘  └──────────────────────┘  │
├───────┴──────────────┴──────────────────────────────────┤
│                     Domain Layer                         │
│  ┌──────────────┐  ┌────────────┐  ┌─────────────────┐  │
│  │   Models     │  │ Repositories│  │   Use Cases     │  │
│  │ (Audiobook,  │  │(Interfaces) │  │  (Business      │  │
│  │  Chapter,    │  │             │  │   Logic)        │  │
│  │  Bookmark)   │  │             │  │                 │  │
│  └──────┬───────┘  └──────┬──────┘  └─────────────────┘  │
├─────────┴─────────────────┴──────────────────────────────┤
│                      Data Layer                          │
│  ┌──────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │   Room   │  │  DataStore     │  │  SharedPrefs    │  │
│  │  (SQLite)│  │  (Preferences) │  │  (Search cache) │  │
│  │  DAOs    │  │                │  │                 │  │
│  └──────────┘  └────────────────┘  └─────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Choice |
|----------|--------|
| **UI Framework** | Jetpack Compose + Material 3 |
| **Navigation** | Navigation Compose with animated transitions |
| **Database** | Room (SQLite) — Books, Bookmarks, Folders |
| **Settings** | DataStore Preferences — Theme, Playback, Onboarding |
| **Audio Engine** | Media3 ExoPlayer + MediaSession |
| **Background Playback** | MediaSessionService with foreground service |
| **Metadata Tagging** | jaudiotagger for M4B files |
| **DI Pattern** | Manual DI via Application class |
| **Image Loading** | Coil Compose |
| **Testing** | Robolectric + Roborazzi (screenshot tests) |

---

## 🧰 Tech Stack

| Technology | Purpose |
|------------|---------|
| **Kotlin 2.2.10** | Primary language |
| **Jetpack Compose** | Declarative UI |
| **Material 3** | Design system |
| **Room** | Local database (SQLite) |
| **DataStore** | Preferences storage |
| **Media3 ExoPlayer** | Audio playback engine |
| **Media3 Session** | Background playback & media notifications |
| **Navigation Compose** | Screen routing & transitions |
| **Coil** | Async image loading |
| **jaudiotagger** | M4B metadata reading/writing |
| **Timber** | Logging |
| **KSP** | Symbol processing (Room, Moshi, Hilt) |
| **Robolectric** | Unit testing on Android framework |
| **Roborazzi** | Screenshot testing |
| **Retrofit + OkHttp** | HTTP client (available for future features) |
| **Moshi** | JSON serialization |

---

## 📋 Prerequisites

- **Android Studio** (latest stable version recommended)
- **JDK 17** (included with Android Studio)
- An Android device or emulator running **API 24+** (Android 7.0)

## 🛠️ Local Setup & Build

Clone the project and build the APK with these commands:

### Prerequisites

- **Java 17+** — verify with `java -version`
- **Android SDK** — set `ANDROID_HOME` to your SDK path

### Setup

```bash
# Clone
git clone https://github.com/yourusername/audiora.git
cd audiora

# Generate debug keystore if missing
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey \
  -storepass android -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug, O=Android, C=US"
```

### Build Debug APK

```bash
export ANDROID_HOME=$HOME/Android/Sdk   # adjust to your SDK path
./gradlew assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

```bash
# Install on a connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build Release APK

```bash
# Generate a release keystore (one-time)
keytool -genkey -v -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000

# Sign and build
export KEYSTORE_PATH=$PWD/my-upload-key.jks
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password
./gradlew assembleRelease
```

APK location: `app/build/outputs/apk/release/app-release.apk`

> ⚠️ **Keep your keystore and passwords safe** — losing them means you cannot publish updates.

### Troubleshooting

- **Out of memory** — reduce `org.gradle.jvmargs=-Xmx2g` in `gradle.properties`
- **Config cache issues** — add `--no-configuration-cache` to the Gradle command
- **Missing SDK platform** — `sdkmanager "platforms;android-36" "build-tools;36.0.0"`

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/audiora/
│   │   ├── AudioraApplication.kt        # Application class & DI
│   │   ├── MainActivity.kt              # Entry activity & NavHost
│   │   ├── core/
│   │   │   ├── design/                  # Glassmorphic design system
│   │   │   └── navigation/              # Screen routes & navigation
│   │   ├── data/
│   │   │   ├── local/                   # Room DB, DAOs, entities
│   │   │   └── repository/              # Repository implementations
│   │   ├── domain/
│   │   │   ├── model/                   # Domain models
│   │   │   ├── repository/              # Repository interfaces
│   │   │   └── usecase/                 # Business logic use cases
│   │   ├── feature/
│   │   │   ├── converter/               # M4B creation wizard
│   │   │   ├── detail/                  # Book detail screen
│   │   │   ├── editor/                  # Metadata & chapter editor
│   │   │   ├── library/                 # Library grid/list
│   │   │   ├── player/                  # Player UI & PlaybackManager
│   │   │   ├── search/                  # Search screen
│   │   │   ├── settings/                # Settings screen
│   │   │   └── welcome/                 # Splash, welcome, onboarding
│   │   └── ui/theme/                    # Colors, typography, theme
│   └── res/                             # Resources
├── build.gradle.kts                     # App module build config
build.gradle.kts                         # Root build config
gradle/libs.versions.toml                # Version catalog
```

---

## 🔑 Permissions

| Permission | Purpose |
|------------|---------|
| `FOREGROUND_SERVICE` | Background audio playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback foreground service (API 34+) |
| `POST_NOTIFICATIONS` | Media playback notification (API 33+) |

---

## 🧪 Testing

```bash
# Run all unit tests
./gradlew test

# Run screenshot tests (Roborazzi)
./gradlew verifyRoborazziDebug

# Update screenshot references
./gradlew recordRoborazziDebug
```

---

## 🗺️ Roadmap

- [x] Audiobook library with grid/list views
- [x] Media3 playback with background service
- [x] Chapter navigation & bookmarking
- [x] M4B metadata editing
- [x] Audio converter/creator
- [x] Sleep timer & variable speed
- [x] Multiple color schemes & themes
- [ ] **Hilt dependency injection** (migrate from manual DI)
- [ ] **Online audiobook sources** (Internet Archive, LibriVox, etc.)
- [ ] **Cloud sync** for position and bookmarks across devices
- [ ] **Equalizer & audio effects**
- [ ] **Widgets** (home screen & lock screen)
- [ ] **Batch metadata editing**
- [ ] **Audiobook chapter parsing** from embedded CHAP tags
- [ ] **Material You dynamic colors** fully integrated
- [ ] **Tablet & foldable optimized layouts**

---

## 📄 License

```
MIT License

Copyright (c) 2024 Audiora

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">
  <sub>Built with ❤️ using Kotlin & Jetpack Compose</sub>
</div>
