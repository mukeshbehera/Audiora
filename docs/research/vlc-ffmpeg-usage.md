# VLC Android FFmpeg Usage Analysis

## Source Analyzed

- Repository: https://github.com/videolan/vlc-android (cloned to `/tmp/vlc-android`)
- Commit: latest `master` branch (shallow clone, depth=1)
- libvlcjni: https://code.videolan.org/videolan/libvlcjni (cloned by compile.sh, not in-tree)

## Core Question: Does VLC exec FFmpeg as a subprocess?

**No. VLC does NOT execute FFmpeg as a subprocess.**

VLC Android uses the **libvlc native shared library** approach. FFmpeg is compiled as part of libvlc and loaded via JNI. There are zero `ProcessBuilder` or `Runtime.exec` calls for FFmpeg anywhere in the VLC Android codebase.

The only `ProcessBuilder`/`Runtime.exec` usages in the entire repository are for:
- `Logcat.kt` — running `logcat` for debug logging
- `BenchActivity.kt` — running benchmark shell commands
- `DemoModeEnabler.kt` — running `am broadcast` for UI testing

None involve FFmpeg.

---

## How VLC Android Uses FFmpeg

### Architecture

```
┌─────────────────────────────────────────┐
│            VLC Android (Kotlin/Java)     │
│  ┌───────────────────────────────────┐  │
│  │  VLCInstance.kt                   │  │
│  │  └─ ILibVLCFactory.getFromOptions()│  │
│  │     └─ calls into libvlcjni .jar  │  │
│  └──────────────┬────────────────────┘  │
├─────────────────┼────────────────────────┤
│  libvlcjni      │  JNI bridge            │
│  (prebuilt .jar │                        │
│   from Maven)   │                        │
│  ┌──────────────▼─────────────────────┐  │
│  │  libvlc.so (native C library)       │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │  libavcodec.so (FFmpeg)      │  │  │
│  │  │  libavformat.so (FFmpeg)     │  │  │
│  │  │  libavutil.so (FFmpeg)       │  │  │
│  │  │  libswscale.so (FFmpeg)       │  │  │
│  │  │  libswresample.so (FFmpeg)    │  │  │
│  │  │  libpostproc.so (FFmpeg)      │  │  │
│  │  └──────────────────────────────┘  │  │
│  └─────────────────────────────────────┘  │
│                                            │
│  System.loadLibrary("mla")                 │
│  └─ mla.so (medialibrary JNI)             │
│     └─ links against libvlc.so            │
└─────────────────────────────────────────┘
```

### Key Code Paths

1. **App startup** (`VLCInstance.kt`):
   - Calls `ILibVLCFactory.getFromOptions()` which internally calls `LibVLC.loadLibraries()`
   - This is a JNI call into the libvlcjni library

2. **Native library loading** (`MedialibraryImpl.java`):
   ```java
   LibVLC.loadLibraries();          // loads libvlc.so
   System.loadLibrary("c++_shared");
   System.loadLibrary("mla");        // medialibrary JNI
   ```

3. **Build system** (`compile.sh`):
   - `./buildsystem/compile.sh` clones `libvlcjni` from `code.videolan.org/videolan/libvlcjni`
   - Calls `compile-libvlc.sh` which runs VLC's `configure` + `make`
   - VLC's configure links FFmpeg's libav* as **shared libraries** (.so)
   - Produces `libvlc.so`, `libavcodec.so`, `libavformat.so`, etc.
   - All .so files are placed in `jni/libs/<abi>/` and bundled in the APK's `lib/` directory

4. **Gradle dependency** (`application/vlc-android/build.gradle`):
   ```groovy
   debugApi "org.videolan.android:libvlc-all:$libvlcVersion"
   releaseApi "org.videolan.android:libvlc-all:$libvlcVersion"
   ```
   libvlc is consumed as a **Maven artifact** — prebuilt .aar containing:
   - `jni/arm64-v8a/libvlc.so`
   - `jni/arm64-v8a/libavcodec.so`
   - `jni/arm64-v8a/libavformat.so`
   - etc.

### Build Details (from compile.sh)

| Step | Script | Output |
|------|--------|--------|
| Build FFmpeg + libvlc | `compile-libvlc.sh -a arm64` | `libvlc/jni/libs/arm64-v8a/*.so` |
| Build medialibrary | `compile-medialibrary.sh -a arm64-v8a` | `medialibrary/jni/libs/arm64-v8a/*.so` |
| Gradle assemble | `./gradlew assembleRelease` | APK with .so files in `lib/arm64-v8a/` |

### Key Insight

FFmpeg's `libavcodec.so`, `libavformat.so`, etc. are **shared libraries (.so)** that are loaded at runtime by libvlc when needed. They are NOT standalone executables. VLC never invokes `ffmpeg` or `ffprobe` as shell commands.

---

## Comparison: VLC Approach vs. Audiora Approach

| Aspect | VLC Android | Audiora |
|--------|-------------|---------|
| FFmpeg integration | Native .so library via JNI | Standalone binary via ProcessBuilder |
| Binary location | APK `lib/<abi>/` (system native lib path) | APK `assets/ffmpeg/` → extracted to `filesDir` |
| Loading mechanism | `System.loadLibrary()` (Android linker) | `ProcessBuilder(List<String>)` |
| exec permission | Not needed (loaded by linker) | `setExecutable(true)` on extracted binary |
| Library dependencies | libavcodec.so, libavformat.so, etc. | libc.so, libm.so (standard system libs) |
| Memory model | In-process (same address space) | Separate process (fork+exec) |
| Error handling | Returns error codes via JNI | Exit codes via `process.waitFor()` |
| Android version compatibility | Works on all versions | **May fail on API 29+ due to exec restrictions** |

---

## Audiora FFmpeg Binary Analysis

### Binary Properties

The CI-built FFmpeg binary (latest artifact `ffmpeg-assets-arm64v8a`, ID 8432333121) was analyzed:

| Property | Value |
|----------|-------|
| Architecture | AArch64 (arm64-v8a) |
| ELF type | ET_DYN (PIE — Position Independent Executable) |
| Interpreter | `/system/bin/linker64` |
| Required shared libs | `libc.so`, `libm.so` (both standard Android system libs) |
| Size | 14,188,480 bytes (~13.5 MB after stripping) |
| Stripped? | Sections present, but most symbols stripped |
| Build NDK | r27c |
| Min API | 24 |
| FFmpeg version | 7.1 |

### Build Configuration

From `scripts/build-ffmpeg.sh`:
```
--enable-static --disable-shared   # FFmpeg code is statically linked
--enable-pic                       # Position-independent code
--enable-small                     # Optimize for size
--optflags="-Os"                   # Size optimization
--target-os=android
--arch=aarch64
```

**Important caveat**: Despite `--enable-static`, the binary is still **dynamically linked** against the system C library (`libc.so`, `libm.so`). This is normal for Linux/Android binaries — `--enable-static` only affects FFmpeg's own libraries, not the system libraries.

### Execution Path in Audiora

1. Binary arrives in APK at `assets/ffmpeg/ffmpeg` (per-flavor)
2. `FfmpegBinaryManager` extracts it via `context.assets.open()` → `contentResolver.openOutputStream()`
3. Destination: `context.getDir("files", MODE_PRIVATE)` → `/data/data/<pkg>/app_files/ffmpeg`
4. `ffmpegFile.setExecutable(true)` is called
5. `ProcessBuilder(listOf(ffmpegPath, ...))` starts the process

---

## Why the Binary Might Fail Silently

### Hypothesis 1: Android W^X Enforcement (Most Likely)

On Android 10 (API 29) and later, SELinux policies restrict executing binaries from app-private directories. The `setExecutable(true)` call may succeed, but when the kernel attempts to load the ELF via `execve()`, SELinux may block it if the file resides in a no-exec directory.

**Evidence:**
- `context.getDir("files", MODE_PRIVATE)` returns `/data/data/<pkg>/app_files/` or similar
- On some Android versions/vendors, `/data/data/<pkg>/` directories are mounted `noexec`
- The binary would fail with `EACCES` or silently crash before producing any output

**Test**: Try extracting to `context.getFilesDir()` (`/data/data/<pkg>/files/`) instead, or better yet, to `context.getCodeCacheDir()` which is explicitly designed for executable content.

### Hypothesis 2: linker64 Not Found

The binary specifies `/system/bin/linker64` as its ELF interpreter. On some devices (especially older or non-standard ROMs), the dynamic linker might be at a different path or the binary ABI doesn't match the device's linker.

### Hypothesis 3: Seccomp Filter Blocking Syscalls

Android's seccomp-bpf filter blocks certain syscalls used by FFmpeg. This is less likely since FFmpeg primarily uses standard I/O and memory syscalls, but architecture-specific optimizations (like arm64 NEON instructions) could trigger seccomp violations on some devices.

### Hypothesis 4: Missing /dev/urandom or Other Device Nodes

FFmpeg may attempt to access `/dev/urandom` or other device files that are restricted by SELinux in an app's process context.

### Hypothesis 5: Simple Argument Error

The FFmpeg command line might have a syntax error that causes FFmpeg to exit with code 1 before producing stdout/stderr output. This would manifest as a "silent failure" if the ProcessExecutor only captures output on success.

---

## Recommended Solutions

### Best: Switch to JNI-based FFmpeg (Like VLC)

Use the FFmpeg libraries as native .so files loaded via JNI, rather than executing a standalone binary. This avoids all exec-related restrictions entirely.

Options:
1. **mobile-ffmpeg** (now ffmpeg-kit) — Pre-built FFmpeg as Android .so libraries with Java/Kotlin wrappers
2. **Build FFmpeg as shared libraries** and create a custom JNI wrapper
3. **Use Media3 Transformer** (built into ExoPlayer) for pure-Java media processing

### Workaround: Better Binary Placement

If the subprocess approach must be retained:
1. Extract to `context.getFilesDir()` instead of `context.getDir()`
2. Or extract to the native library directory (`context.getApplicationInfo().nativeLibraryDir`)
3. Or use `context.getCodeCacheDir()` which is explicitly for executable code
4. Set `--enable-pic` (already done) for PIE support
5. Target API level 24+ for PIE-only execution

### Workaround: Check Binary Health

- Verify the binary runs with `ffmpeg -version` or `ffmpeg -buildconf` before using it for actual work
- Add more detailed error reporting on process failure (capture stderr even on non-zero exit)
- Log the full command line and working directory for debugging

---

## References

- VLC Android source: `/tmp/vlc-android`
- VLC compile script: `/tmp/vlc-android/buildsystem/compile.sh`
- libvlcjni repository: https://code.videolan.org/videolan/libvlcjni
- Audiora FFmpeg build: `/root/Audiora/scripts/build-ffmpeg.sh`
- Audiora binary manager: `/root/Audiora/app/src/main/java/com/audiora/data/processing/FfmpegBinaryManager.kt`
- Audiora process executor: `/root/Audiora/app/src/main/java/com/audiora/data/processing/executor/ProcessExecutor.kt`
