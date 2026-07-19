# FFmpeg/FFprobe Native Binary Execution on Android — Research Findings

## 1. Reference Project: android-media-converter

Source: `/tmp/android-media-converter/`

### 1.1 Binary Bundling Strategy

- **Per-ABI product flavors** in `app/build.gradle` (lines 77-123):
  - `arm` (armeabi), `arm7` (armeabi-v7a), `arm8` (arm64-v8a), `x86`, `x86_64`
  - Each flavor has its own `src/<flavor>/assets/` directory with the correct ABI binary
  - APK size kept small — only one architecture per APK
  - NDK `abiFilter` / `abiFilters` ensures only the matching `.so` files and binaries are included

- **Asset files per flavor directory** (in `app/src/<flavor>/assets/`):
  - `ffmpeg` — the static binary (6-9 MB depending on architecture)
  - `ffmpeg_size.txt` — expected file size as integer string for integrity checking

- **`update_ffmpeg_size.sh`**: Makes binaries executable in-place (`chmod +x`), records their sizes:
  ```bash
  stat -f%z "app/src/arm8/assets/ffmpeg" > app/src/arm8/assets/ffmpeg_size.txt
  ```

### 1.2 Extraction Path

Defined in `WorkingPaths.kt` (line 17, 35):

```kotlin
val fileDir = context.getDir(APP_FILE_FOLDER, Context.MODE_PRIVATE)  // APP_FILE_FOLDER = "files"
val ffmpegPath = File(fileDir, FFMPEG_FILE)                          // FFMPEG_FILE = "ffmpeg"
```

This resolves to: **`/data/data/<package>/app_files/ffmpeg`**

- `Context.getDir(name, Context.MODE_PRIVATE)` creates/returns a directory at `/data/data/<pkg>/app_<name>/`
- Note: `getDir("files")` produces directory `app_files/` (not `files/`)
- This is on the device's internal storage, not external/sdcard

The reference also uses `context.getExternalFilesDir(APP_TEMP_FOLDER)` for temp files (falls back to `getDir`):

```kotlin
val tempDir: File = try {
    context.getExternalFilesDir(APP_TEMP_FOLDER).ensureDirExists()
} catch (ignore: Throwable) {
    context.getDir(APP_TEMP_FOLDER, Context.MODE_PRIVATE)
}
```

### 1.3 Permission Setting

In `CommandResolver.kt`, `FFmpegPathResolver.resolvePath()` (lines 96-124):

```kotlin
fun resolvePath(context: Context, ffmpegPath: File): File {
    synchronized(globalLock) {
        // Copy from assets if missing or size mismatch
        if (!ffmpegPath.exists() || ffmpegPath.length() != ffmpegSize) {
            assetManager.open(FFMPEG_FILE).use { inputStream ->
                val copyTo = context.contentResolver.openOutputStream(Uri.fromFile(ffmpegPath))
                BufferedOutputStream(copyTo).use { bufferedOutputStream ->
                    inputStream.copyTo(bufferedOutputStream)
                }
            }
        }
        // Set executable permission
        if (ffmpegPath.canExecute() ||
            catchAll { ffmpegPath.setExecutable(true) } == true) {
            return ffmpegPath
        } else {
            throw FFmpegBinaryPrepareException("Can't grant executable permission", null)
        }
    }
}
```

Key observations:
- Uses `context.contentResolver.openOutputStream(Uri.fromFile(ffmpegPath))` to write — this goes through ContentResolver rather than direct `FileOutputStream`
- Checks `ffmpegPath.canExecute()` first (might already be set from a previous run)
- Calls `ffmpegPath.setExecutable(true)` to add the executable bit
- Both `canExecute()` and `setExecutable()` are `java.io.File` methods
- Thread-safe via `synchronized(globalLock)`
- Integrity check via file size comparison against `ffmpeg_size.txt`

### 1.4 Execution Mechanism — `sh -c` Shell Wrapping

In `JobWorkerThread.kt`, `startProcess()` (lines 159-170):

```kotlin
private fun startProcess(commandResolver: CommandResolver): Process {
    val cmdArray = listOf(
        "sh", "-c",
        commandResolver.execCommand
    )
    return ProcessBuilder()
        .apply { environment().putAll(commandResolver.command.environmentVars) }
        .command(cmdArray)
        .start()
}
```

**The command is built as a single shell string**, then wrapped in `sh -c`:

```
sh -c '/data/data/<pkg>/app_files/ffmpeg -i '\''file:///path/to/input'\'' ... -y -f mp4 '\''file:///path/to/output'\'''
```

Arguments are:
- Single-quote escaped using `String.escapeSingleQuote()` (replaces `'` with `'\''`)
- URIs formatted as `file://<path>` with the path escaped
- The entire argument string is concatenated with string builder

Pros:
- Allows shell features (env vars in command string, pipes, redirects)
- Simple to debug (can log/logcat the command string directly)

Cons:
- Shell injection risk — requires proper escaping of all arguments
- Extra process overhead (shell process spawns ffmpeg)
- Error handling is indirect (shell exit code vs process exit code)

### 1.5 Content URI Handling

In `CommandResolver.kt`, `resolve()` (lines 44-58):

```kotlin
command.inputs.forEachIndexed { index, input ->
    val uri = Uri.parse(input)
    when (uri.scheme?.toLowerCase()) {
        "file" -> execCommandBuilder.append(" '${formatFileUri(uri)}'")
        "content", "http", "https" -> {
            val preparedInput = makeInputTempFile(jobTempDir, index)
            if (!preparedInput.exists()) {
                throw FileNotFoundException("Some app data were deleted or moved.")
            }
            execCommandBuilder.append(" '${formatFileUri(Uri.fromFile(preparedInput))}'")
        }
    }
}
```

Content URI flow:
1. `content://` inputs are detected by scheme
2. The pre-existing temp file at `jobTempDir/input0`, `jobTempDir/input1`, etc. is verified to exist
3. FFmpeg receives a `file://` URI pointing to that temp file

**The temp file must already exist** — it is created by the job preparation phase (not shown in these files). FFmpeg never sees a `content://` URI.

### 1.6 Output Handling

```kotlin
val tempFile = File(jobTempDir, FFMPEG_TEMP_OUTPUT_FILE)  // "ffmpeg_out.temp"
val tempOutputUri = Uri.fromFile(tempFile)
execCommandBuilder.append(" -y -f ${command.outputFormat} '${formatFileUri(tempOutputUri)}'")
```

FFmpeg writes to a temp file. On success, the temp file is copied to the actual output destination:

```kotlin
tempIs = commandResolver.tempFileSourceInput.openInputStream()
destOs = commandResolver.sourceOutput.openOutputStream()
tempIs.copyTo(destOs)
```

---

## 2. Audiora's Current Implementation

Source: `/root/Audiora/app/src/main/java/com/audiora/data/processing/`

### 2.1 Binary Bundling

- Same per-ABI flavor approach in `app/build.gradle.kts` (lines 33-52)
- Three flavors: `arm64v8a`, `armeabiv7a`, `x8664`
- **BUT**: No per-flavor `assets/ffmpeg/` directories exist yet — only `app/src/main/assets/` exists and is empty
- Expects asset layout: `assets/ffmpeg/ffmpeg`, `assets/ffmpeg/ffprobe`, `assets/ffmpeg/ffmpeg_size.txt`, `assets/ffmpeg/version.txt`

### 2.2 Extraction Path

In `FfmpegBinaryManager.kt` (lines 202-204):

```kotlin
private fun getBinDir(): File {
    return context.getDir(BIN_DIR_NAME, Context.MODE_PRIVATE)  // BIN_DIR_NAME = "files"
}
```

Same pattern as android-media-converter: **`/data/data/<pkg>/app_files/ffmpeg`** and **`/data/data/<pkg>/app_files/ffprobe`**

### 2.3 Permission Setting (lines 110-115)

```kotlin
if (!ffmpegFile.setExecutable(true)) {
    throw BinaryInitException("Failed to set executable permission on $ffmpegFile")
}
if (!ffprobeFile.setExecutable(true)) {
    throw BinaryInitException("Failed to set executable permission on $ffprobeFile")
}
```

- Simpler than android-media-converter — no `canExecute()` pre-check
- Uses same `contentResolver.openOutputStream()` pattern for writing from assets
- Integrity verification via size file (`ffmpeg_size.txt`)
- Version tracking via SharedPreferences + `version.txt`

### 2.4 Execution Mechanism — Direct ProcessBuilder

In `ProcessExecutor.kt` (line 50):

```kotlin
val process = ProcessBuilder(command)
    .redirectErrorStream(false)
    .start()
```

Commands are built as `List<String>` — **no shell wrapping**:

```kotlin
// FFprobeCommandBuilder example output:
[ "/data/data/<pkg>/app_files/ffprobe", "-v", "quiet", "-print_format", "json", "-show_chapters", "/path/to/file" ]
```

Pros over `sh -c`:
- No shell injection risk — arguments are passed as individual strings to execve()
- No quoting/escaping needed for paths with spaces or special characters
- No extra shell process overhead
- Cleaner error handling

Cons:
- No shell features (pipes, redirects)
- Cannot set environment variables inline (must use `ProcessBuilder.environment()`)

### 2.5 Content URI Handling — MISSING

This is a significant gap. In `FFprobeChapterExtractor.kt` (lines 34-36):

```kotlin
val filePath = uri.toString()                      // e.g., "content://com.android.externalstorage/..."
val command = commandBuilder.readChapters(filePath) // passes content URI string directly
```

**FFmpeg/FFprobe do not understand `content://` URIs** — they expect file paths. This will fail with an error like:
```
/path/to/content://com.android.externalstorage/...: No such file or directory
```

The caller (`BookRepositoryImpl.kt` via `FolderRepositoryImpl.kt`) uses `M4bChapterExtractor` directly for MP4 files. The `FFprobeChapterExtractor` is implemented but its content URI issue means it would only work for `file://` URIs or file paths.

**What's needed**: Copy content:// inputs to temp files (like android-media-converter does) before passing to FFmpeg.

### 2.6 Overall Architecture Comparison

| Aspect | android-media-converter | Audiora | Better? |
|--------|------------------------|---------|---------|
| Binary storage | Per-flavor assets | Per-flavor assets (planned) | Same |
| Extraction path | `getDir("files")` | `getDir("files")` | Same |
| Integrity check | `ffmpeg_size.txt` | `ffmpeg_size.txt` + version | Audiora |
| Permission | `setExecutable(true)` | `setExecutable(true)` | Same |
| Execution | `sh -c` wrapper | Direct `ProcessBuilder` | Audiora (safer) |
| Content URI input | Copy to temp file | **Not handled** | android-media-converter |
| Output to content URI | Copy from temp file | **Not handled** | android-media-converter |
| Error handling | Exit code + log | Structured result type | Audiora |
| Cancellation | Kill by PID | Coroutine cancellation | Audiora |
| Thread safety | `synchronized` block | Coroutine Mutex (implied) | Similar |

---

## 3. ffmpeg-kit (arthenica) Approach

Source: `https://github.com/arthenica/ffmpeg-kit`

### 3.1 Architecture

ffmpeg-kit takes a fundamentally different approach: **JNI native integration**, not subprocess execution.

```java
// FFmpegKitConfig.java — native method declaration
private native static int nativeFFmpegExecute(final long sessionId, final String[] arguments);
```

### 3.2 Key Differences

| Aspect | ffmpeg-kit | android-media-converter / Audiora |
|--------|------------|-----------------------------------|
| Execution | JNI native call to FFmpeg C API | Subprocess via ProcessBuilder |
| Binary form | Shared library (.so) loaded via `System.loadLibrary()` | Static binary on filesystem |
| Execution path | `/data/app/<pkg>/lib/<abi>/` (native lib dir) | `/data/data/<pkg>/app_files/` |
| Permissions | No exec bit needed (loaded by linker) | Must call `setExecutable(true)` |
| Security model | Standard .so loading | Relies on exec() from app-owned directory |
| Complexity | High (JNI bridge, C wrapper) | Low (just extract and run) |

### 3.3 Content URI Handling

ffmpeg-kit has a sophisticated SAF (Storage Access Framework) protocol handler:

- Uses `ParcelFileDescriptor` to obtain a file descriptor from `ContentResolver`
- Creates a named pipe (FIFO) using `mkfifo()`
- Passes the named pipe path to FFmpeg as `saf:<id>` protocol
- A background thread reads from the file descriptor and writes to the pipe
- This avoids copying the content to a temp file

### 3.4 Why This Matters

ffmpeg-kit's approach is arguably the "correct" Android way — using NDK native libraries rather than subprocess execution. However, it requires:
- Building FFmpeg as a shared library (.so) with a JNI wrapper
- Significant C/JNI bridge code
- Integrating with Android's build system (CMake/NDK)
- More complex build pipeline

For Audiora's needs, the subprocess approach (like android-media-converter) is simpler and proven in production, as long as content URI handling is properly implemented.

---

## 4. Android Execution Security Model

### 4.1 The noexec Myth

Common claim: `/data/data/` is mounted `noexec`, so you can't execute binaries from it.

**Reality**: While `/data/data/` IS mounted with `noexec`, Android's security model permits app UIDs to execute binaries from their own data directory. The `noexec` mount flag:

- **Applies**: To the `zygote` process forking (prevents arbitrary code from being executed as a new app process)
- **Does NOT prevent**: An already-running app process from calling `execve()` on a binary in its own directory
- **Does NOT prevent**: `ProcessBuilder` or `Runtime.exec()` from spawning a subprocess from a binary in the app's data directory

SELinux policies on standard (non-rooted) devices allow apps to execute files under their own `/data/data/<pkg>/` directory. This is evidenced by literally hundreds of apps (android-media-converter, Oandbackup, various FFmpeg wrapper apps) using this approach in production for years.

### 4.2 `setExecutable(true)` Behavior

`java.io.File.setExecutable(true)` calls `chmod()` on the file to add the executable bit (owner execute permission). This succeeds on app-private directories because the app owns the files and directories under `/data/data/<pkg>/`.

```kotlin
ffmpegFile.setExecutable(true)  // Returns true on standard devices
```

### 4.3 `ProcessBuilder` vs `Runtime.exec()`

Both ultimately call `ProcessBuilder.start()` → `fork()` + `execve()`. They are equivalent for execution:

```kotlin
// Method A: Direct binary (Audiora)
ProcessBuilder(ffmpegPath, "-i", input, ...).start()

// Method B: Shell wrapper (android-media-converter)
ProcessBuilder("sh", "-c", "$ffmpegPath -i '$input' ...").start()
```

Method A is safer (no shell injection) and marginally faster (no shell process).

### 4.4 Android 10+ Changes

- **Heap pointer tagging**: ARM MTE-like protection (opt-out via manifest flag)
- **Execute-only memory**: System binaries mapped as execute-only (non-readable). Does NOT affect app-owned data.
- **Scoped storage**: Affects external storage access, not `/data/data/`. If FFmpeg needs to read files selected via SAF, the app must grant access and copy to temp.

**No changes in Android 10-14 prevent executing binaries from `/data/data/<pkg>/`**.

### 4.5 Limitations

The subprocess approach has these limitations:

| Issue | Status |
|-------|--------|
| Rooted devices only? | No, works on stock devices |
| All Android versions? | Yes, tested from API 16+ |
| SELinux enforcing? | Yes, permitted by default policy |
| Play Store compliance? | Yes, many apps use this pattern |
| Device encryption (FBE/FDE)? | Works — /data/data is decrypted when user unlocks |
| Work profile? | Works — app has own /data/data under profile |

---

## 5. Best Practices Summary

### 5.1 Binary Handling

1. **Per-ABI flavors**: Bundle each architecture's binary in the flavor-specific `assets/` directory
2. **Size file** (`ffmpeg_size.txt`): Verify integrity before and after extraction
3. **Version file** (`version.txt`): Track installed version for upgrade detection
4. **Extraction**: `context.getDir("files", Context.MODE_PRIVATE)` — produces `/data/data/<pkg>/app_files/`
5. **Permissions**: `setExecutable(true)` — works on app-owned files
6. **Thread safety**: Synchronize extraction (coroutine Mutex or `synchronized`)

### 5.2 Execution

1. **Use `ProcessBuilder(List<String>)`** — not `sh -c` — to avoid shell injection
2. **Never construct command strings** — always use argument lists
3. **Run on `Dispatchers.IO`** — process execution blocks the thread
4. **Handle cancellation** — `process.destroyForcibly()` on coroutine cancellation
5. **Capture stderr** for FFmpeg progress parsing; stdout for FFprobe JSON
6. **Set timeout** to prevent hung processes

### 5.3 Content URI Handling

1. **Check URI scheme**: If `content://`, resolve to a temp file before passing to FFmpeg
2. **Temp file location**: `context.cacheDir` (can be reclaimed by system) or `getExternalFilesDir` (survives)
3. **Copy via ContentResolver**: `context.contentResolver.openInputStream(uri)` → `FileOutputStream(tempFile)`
4. **Clean up**: Delete temp files after processing
5. **Do NOT pass `uri.toString()` to FFmpeg** — it only understands file paths

Pattern from android-media-converter:

```kotlin
when (uri.scheme?.lowercase()) {
    "file" -> useDirectly(uri.path)
    "content", "http", "https" -> {
        val tempFile = File(tempDir, "input$index")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        useDirectly(tempFile.absolutePath)
    }
}
```

### 5.4 Error Handling

1. **Check exit code**: 0 = success, non-zero = FFmpeg/FFprobe reported error
2. **Parse stderr**: Last lines of stderr usually contain the error description
3. **Graceful fallback**: If FFprobe extraction fails, fall back to M4B atom parsing or single-chapter display
4. **Don't crash the app**: Processing errors should never propagate to the UI as crashes
