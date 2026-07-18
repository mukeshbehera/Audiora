# FFmpeg Integration — Production-Grade Refinement

**Date:** 2026-07-18
**Status:** Approved
**Supersedes:** docs/superpowers/specs/2026-07-18-ffmpeg-integration-design.md

---

## 1. Overview

This spec refines the existing FFmpeg integration implementation (16 commits already merged) to meet production-grade requirements: version-aware binary management, recovery from corruption, proper asset directory layout, structured exception hierarchy, BuildConfig-based versioning, and a CI pipeline for building FFmpeg from source.

### Two-Phase Strategy

- **Phase 1 (Development)**: Refine existing code to use new directory layout, version tracking, integrity recovery, and proper exception hierarchy. Provide a download script for pre-built dev binaries.
- **Phase 2 (Production CI)**: GitHub Actions workflow that builds FFmpeg from source, strips symbols, and produces the APK.

The same runtime code works for both phases — no hardcoded development-specific logic.

---

## 2. Asset Directory Layout

```
app/src/main/assets/ffmpeg/
    version.txt                         ← contains version string e.g. "7.1"
    arm64-v8a/
        ffmpeg                          ← binary
        ffprobe                         ← binary
    armeabi-v7a/
        ffmpeg
        ffprobe
    x86_64/
        ffmpeg
        ffprobe
```

The `version.txt` file is read at runtime by `FfmpegBinaryManager` to know which version is bundled. This avoids needing a BuildConfig change for every binary update during development.

**Dev binary download script** (`scripts/download-ffmpeg-dev.sh`):
- Fetches pre-built static binaries for the host architecture only
- Places them in the correct asset directory structure
- Writes `version.txt`

---

## 3. Version-Aware Binary Management

### Version Tracking Flow

```
FfmpegBinaryManager.ensureInitialized()
    ↓
Read version.txt from assets → bundledVersion
    ↓
Read SharedPreferences("ffmpeg_version") → installedVersion
    ↓
if installedVersion == bundledVersion AND binaries exist on disk
    → skip extraction, verify integrity
else
    → delete files/bin/ffmpeg files/bin/ffprobe
    → extract from assets/ffmpeg/{abi}/
    → set executable
    → verify via --version
    → if fail: delete, re-extract, verify once more
    → if success: save bundledVersion to SharedPreferences
    → return paths
```

### SharedPreferences Keys

| Key | Purpose |
|-----|---------|
| `ffmpeg_installed_version` | Version string of last successful extraction |
| `ffmpeg_install_timestamp` | Epoch millis of last successful extraction |

---

## 4. FfmpegBinaryManager (Refined)

### Changes from Current Implementation

| Aspect | Current | Refined |
|--------|---------|---------|
| Asset path | `{name}-{abi}` | `ffmpeg/{abi}/{name}` |
| Version check | File existence only | `version.txt` + SharedPreferences |
| Integrity recovery | None | Delete + re-extract + retry cycle |
| ProcessBuilder usage | Direct in `verifyBinary()` | Via `ProcessExecutor` |
| Error exceptions | 3 flat classes | Sealed hierarchy |
| Context usage | `Context` for assets + files | `Context` for assets + files + prefs |

### Signature

```kotlin
class FfmpegBinaryManager(
    private val context: Context,
    private val processExecutor: ProcessExecutor,
) {
    data class BinaryPaths(val ffmpegPath: String, val ffprobePath: String)

    suspend fun ensureInitialized(): BinaryPaths
    fun isInitialized(): Boolean
    fun getVersion(): String?
    fun getInstalledVersion(): String?  // from SharedPreferences
    suspend fun reinitialize(): BinaryPaths  // force re-extract
}
```

### Integrity Verification

```kotlin
private suspend fun verifyBinary(binaryPath: String, name: String, expectedVersion: String) {
    val result = processExecutor.execute(listOf(binaryPath, "-version"))
    if (result.isError) {
        throw BinaryVerificationException("$name verification failed: ${(result as FFmpegResult.Error).message}")
    }
    val output = result.getOrNull() ?: throw BinaryVerificationException("$name produced no output")
    val firstLine = output.lines().firstOrNull() ?: ""
    if (name !in firstLine.lowercase()) {
        throw BinaryVerificationException("$name binary does not appear to be valid: $firstLine")
    }
    cachedVersion = firstLine.trim()
}
```

---

## 5. Exception Hierarchy

```kotlin
sealed class FfmpegException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UnsupportedAbiException(message: String) : FfmpegException(message)
class BinaryInitException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)
class BinaryVerificationException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)
class BinaryNotFoundException(message: String) : FfmpegException(message)
class BinaryCorruptedException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)
class CommandExecutionException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)
class FfprobeParseException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)
class FfmpegTimeoutException(message: String) : FfmpegException(message)
class FfmpegCancellationException(message: String) : FfmpegException(message)
```

---

## 6. FfmpegBinaryManager — No Direct ProcessBuilder

The current `verifyBinary()` creates `ProcessBuilder` directly. This violates the architecture rule. Fix:

```kotlin
// Current (violation):
private fun verifyBinary(binaryPath: String, name: String) {
    val process = ProcessBuilder(listOf(binaryPath, "-version"))  // ← direct usage
    ...
}

// Refined (compliant):
private suspend fun verifyBinary(binaryPath: String, name: String) {
    val result = processExecutor.execute(listOf(binaryPath, "-version"))
    ...
}
```

---

## 7. ProcessExecutor — Zombie Process Prevention

Already well-implemented. One addition: ensure `process.destroyForcibly()` is called in all exit paths, which it already is. Add explicit null check for `progressCallback`:

```kotlin
// Add to execute():
} finally {
    if (process.isAlive) {
        process.destroyForcibly()
        process.waitFor(1, TimeUnit.SECONDS)  // ensure reaped
    }
}
```

---

## 8. CI Pipeline — Phase 2

### GitHub Actions Workflow

**File:** `.github/workflows/build-ffmpeg.yml`

```yaml
name: Build FFmpeg and Android App
on:
  push:
    branches: [main, fix/**, feat/**]
  workflow_dispatch:

jobs:
  build-ffmpeg:
    strategy:
      matrix:
        abi: [arm64-v8a, armeabi-v7a, x86_64]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - name: Build FFmpeg for ${{ matrix.abi }}
        run: bash scripts/build-ffmpeg.sh ${{ matrix.abi }}
      - name: Upload binaries
        uses: actions/upload-artifact@v4
        with:
          name: ffmpeg-${{ matrix.abi }}
          path: assets/ffmpeg/${{ matrix.abi }}/

  build-android:
    needs: build-ffmpeg
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Download all binaries
        uses: actions/download-artifact@v4
      - name: Build APK
        run: ./gradlew assembleDebug
```

### FFmpeg Build Script

**File:** `scripts/build-ffmpeg.sh`

- Downloads FFmpeg source (official tarball)
- Configures with minimal flags for audiobook-only support
- Cross-compiles using Android NDK
- Strips symbols with `llvm-strip`
- Copies to `assets/ffmpeg/{abi}/`

### Minimal FFmpeg Configure Flags

```
--disable-all
--enable-ffmpeg
--enable-ffprobe
--enable-encoder=aac
--enable-decoder=aac,mp3,mpeg4
--enable-muxer=mp4
--enable-demuxer=aac,mp3,mov
--enable-protocol=file
--enable-parser=aac,mpegaudio
--enable-filter=concat
--enable-libfdk-aac          (if available, for better AAC encoding)
--enable-cross-compile
--arch={arch}
--target-os=android
--enable-small               (size optimization)
--optflags=-Os
--strip={toolchain}-strip
```

---

## 9. BuildConfig Version Integration

### gradle.properties
```properties
ffmpegVersion=7.1
```

### app/build.gradle.kts
```kotlin
android {
    defaultConfig {
        buildConfigField("String", "FFMPEG_VERSION", "\"${project.findProperty("ffmpegVersion") ?: "0.0"}\"")
    }
}
```

### FfmpegBinaryManager reads:
```kotlin
context.assets.open("ffmpeg/version.txt").bufferedReader().readText().trim()
```

At runtime, `BuildConfig.FFMPEG_VERSION` provides the expected version string.

---

## 10. Logging Strategy

| Build Type | FFmpeg stderr | FFmpeg stdout | Progress Lines |
|-----------|--------------|---------------|----------------|
| Debug     | Full (Timber.d) | Full | Silenced |
| Release   | Errors only (Timber.w) | Silenced | Silenced |

Implementation: `ProcessExecutor` checks `BuildConfig.DEBUG` before logging stderr lines.

---

## 11. Files to Modify

| File | Change |
|------|--------|
| `data/processing/FfmpegBinaryManager.kt` | Asset path layout, version tracking, integrity recovery, ProcessExecutor injection, exception hierarchy |
| `data/processing/dto/FFmpegResult.kt` | Add correlation ID to Error, add FfmpegException sealed class |
| `data/processing/executor/ProcessExecutor.kt` | Add `process.waitFor()` in cleanup, guard against double-destroy |
| `data/processing/FFmpegService.kt` | Remove `Context` dependency (binary manager already has it) |
| `AudioraApplication.kt` | Update FfmpegBinaryManager constructor call, remove passed Context if applicable |
| `gradle.properties` | Add `ffmpegVersion=7.1` |
| `app/build.gradle.kts` | Add `buildConfigField` for FFMPEG_VERSION |
| `scripts/download-ffmpeg-dev.sh` | **New** — download dev binaries into correct asset layout |
| `scripts/build-ffmpeg.sh` | **New** — CI FFmpeg source build script |
| `.github/workflows/build-ffmpeg.yml` | **New** — GitHub Actions workflow |
| `app/src/main/assets/ffmpeg/version.txt` | **New** — version file |

---

## 12. Non-Goals (Unchanged)

- JAudiotagger metadata operations: untouched
- `M4bChapterExtractor`: kept for compatibility
- `M4BTranscoder`: kept as fallback
- ProcessingScreen FFmpeg-first logic: already implemented
- BookRepository interface: already extended
- Existing DTOs, parsers, command builders: remain as-is
