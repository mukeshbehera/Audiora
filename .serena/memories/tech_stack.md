# Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material3, BOM-managed)
- **Build**: Gradle 9.3.1, AGP 9.1.1, Kotlin Compose plugin, KSP
- **Min SDK**: 24, **Target SDK**: 36, **Compile SDK**: 36
- **Database**: Room (with KSP annotation processing)
- **DI**: Manual constructor injection (no Hilt/Dagger)
- **Media**: Media3 ExoPlayer + MediaSession (playback), MediaExtractor/MediaMuxer (transcoding)
- **Metadata**: jaudiotagger (ID3/MP4 tag reading/writing)
- **Networking**: OkHttp + Retrofit + Moshi (for potential future API use)
- **Image loading**: Coil (Compose)
- **Testing**: JUnit, Robolectric, Roborazzi (Compose screenshot tests)
- **CI**: GitHub Actions (debug APK build, signed release build)
- **Gradle**: 9.3.1, AGP 9.1.1, Kotlin 2.x (Compose plugin)
- **Min SDK**: 24, Target/Compile SDK: 36
- **Java**: 11 source/target compatibility
