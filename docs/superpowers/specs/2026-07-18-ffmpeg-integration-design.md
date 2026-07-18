# FFmpeg Integration for Audiobook Processing — Design Spec

**Date:** 2026-07-18
**Status:** Approved for implementation planning
**Author:** Superpowers Brainstorming → Design

---

## 1. Objective

Implement a robust, production-ready audiobook processing layer for Audiora. The app continues using `org.jaudiotagger` for lightweight metadata operations while integrating FFmpeg/FFprobe for advanced container-level processing such as chapter management and M4B creation.

### Guiding Principles

- JAudiotagger handles **metadata/cover** (fast, no container rewrite needed)
- FFmpeg handles **container operations** (chapter editing, format conversion, merging)
- FFprobe handles **container inspection** (reading chapters, streams, format info)
- **Never mix responsibilities** between the two implementations
- The repository layer decides which tool to use — UI never knows

---

## 2. Architecture

```
                     BookRepository
                           │
            ┌──────────────┴──────────────┐
            │                             │
            ▼                             ▼
      JAudiotagger                  FFmpegService
      (existing)                    (new)
                                        │
                          ┌─────────────┼──────────────┐
                          │             │              │
                          ▼             ▼              ▼
                 FfmpegBinary    FFmpegCommand      ProcessExecutor
                 Manager         Builder/
                                 FFprobeCommand
                                 Builder
                          │             │              │
                          ▼             ▼              ▼
                     Asset files    FFprobeJson     ProcessBuilder
                     /files/bin/    Parser/
                                    ProgressParser
```

### Separation of Concerns

| Layer | Responsibility |
|-------|---------------|
| **BookRepository** | Orchestrates. Routes to JAudiotagger or FFmpegService based on operation type. |
| **JAudiotagger (existing)** | Metadata read/write, cover read/write/remove. Never invoked for container ops. |
| **FFmpegService** | Coordinates FFmpeg/FFprobe workflows. Does not construct commands or execute processes directly. |
| **FfmpegBinaryManager** | ABI detection, binary extraction from assets, permissions, version check. Initialized once. |
| **FFmpegCommandBuilder** | Builds `List<String>` commands for FFmpeg operations. No direct process execution. |
| **FFprobeCommandBuilder** | Builds `List<String>` commands for FFprobe queries. |
| **ProcessExecutor** | Single point for `ProcessBuilder` execution. Handles timeout, cancellation, stdout/stderr capture. |
| **FFprobeJsonParser** | Parses FFprobe JSON output into typed DTOs. |
| **ProgressParser** | Parses FFmpeg stderr progress lines into structured progress events. |
| **TempFileManager** | Creates and cleans up temporary files (metadata.txt, chapters.txt, intermediate output). |

---

## 3. Package Structure

```
domain/
    model/
        Audiobook.kt                        (existing)
        Chapter.kt                          (existing)
        Bookmark.kt                         (existing)
        AudiobookFolder.kt                  (existing)
        PlaybackSettings.kt                 (existing)
        ConversionOptions.kt                [NEW] bitrate, sampleRate, channelCount, chapter strategy, metadata
        ExportOptions.kt                    [NEW] output format, codec options, metadata handling

    repository/
        BookRepository.kt                   (existing — extended with processing methods)

data/
    local/
        M4bChapterExtractor.kt              (existing — keep)
        ChapterExtractor.kt                 [NEW] interface: extract(filePath): List<Chapter>

    repository/
        BookRepositoryImpl.kt               (existing — extended, FFmpegService injected)

    processing/
        FFmpegService.kt                    [NEW] coordinates FFmpeg/FFprobe workflows
        FfmpegBinaryManager.kt              [NEW] ABI detection, asset extraction, version, permissions
        TempFileManager.kt                  [NEW] temp file lifecycle management

        command/
            FFmpegCommandBuilder.kt         [NEW] builds FFmpeg commands as List<String>
            FFprobeCommandBuilder.kt        [NEW] builds FFprobe commands as List<String>

        executor/
            ProcessExecutor.kt              [NEW] ProcessBuilder wrapper with timeout/cancellation

        parser/
            FFprobeJsonParser.kt            [NEW] parses FFprobe JSON → DTOs
            ProgressParser.kt               [NEW] parses FFmpeg stderr progress lines

        dto/
            FFprobeChapter.kt               [NEW] DTO: id, timeBase, start, end, startTime, endTime, title
            FFprobeFormat.kt                [NEW] DTO: filename, duration, size, bitRate, formatName
            FFprobeStream.kt                [NEW] DTO: index, codecName, codecType, sampleRate, channels, bitRate
            FFmpegResult.kt                 [NEW] sealed: Success | Error
            FfprobeResult.kt                [NEW] sealed: Success<T> | Error

feature/
    converter/                              (existing — refactor to use FFmpegService)
```

---

## 4. Component Specifications

### 4.1 FfmpegBinaryManager

**Location:** `data/processing/FfmpegBinaryManager.kt`

**Responsibilities:**
- Detect device ABI via `Build.SUPPORTED_ABIS`
- Extract correct `ffmpeg` and `ffprobe` binaries from `assets/` to `files/bin/`
- Set executable permissions (`setExecutable(true)`)
- Verify binary works by parsing `--version` output
- Run once per app lifetime; subsequent calls are no-ops

**Asset naming convention:**
```
assets/
    ffmpeg-arm64-v8a
    ffprobe-arm64-v8a
    ffmpeg-armeabi-v7a
    ffprobe-armeabi-v7a
    ffmpeg-x86_64
    ffprobe-x86_64
```

**Error states:**
- No matching ABI binaries in assets → `UnsupportedAbiException`
- Copy fails → `BinaryInitException`
- Version check fails → `BinaryVerificationException`

**Thread safety:**
```kotlin
class FfmpegBinaryManager @Inject constructor(
    private val context: Context
) {
    @Volatile private var initialized = false
    private val lock = Any()

    suspend fun ensureInitialized(): PathResult  // returns paths to binaries
    fun isInitialized(): Boolean
    fun getVersion(): String?
}
```

---

### 4.2 ProcessExecutor

**Location:** `data/processing/executor/ProcessExecutor.kt`

**API:**
```kotlin
class ProcessExecutor {
    suspend fun execute(
        command: List<String>,
        timeout: Duration = Duration.ofMinutes(5),
        progressCallback: ((String) -> Unit)? = null,
    ): FFmpegResult
}
```

**Behavior:**
1. Creates `ProcessBuilder(command)`
2. Merges stderr into process stream or captures separately (FFmpeg outputs progress on stderr)
3. Reads streams on `Dispatchers.IO` via coroutine channels
4. If `progressCallback` provided, each stderr line is forwarded to it
5. `withTimeout` from kotlinx.coroutines for timeout enforcement
6. On cancellation: `process.destroy()` + ensure cleanup
7. Returns `FFmpegResult` with captured stdout, stderr, and exit code

**Key design decisions:**
- Does NOT use shell (`/bin/sh -c`) — commands are a clean `List<String>`
- Timeout default: 5 minutes; configurable per call
- Cancellation: cooperative via `isActive` check + forced `destroy()` fallback

---

### 4.3 FFmpegCommandBuilder

**Location:** `data/processing/command/FFmpegCommandBuilder.kt`

**API:**
```kotlin
class FFmpegCommandBuilder(private val ffmpegPath: String) {

    fun createM4B(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: Map<String, String> = emptyMap(),
        coverPath: String? = null,
        chaptersFilePath: String? = null,
    ): List<String>

    fun addChapters(
        inputPath: String,
        outputPath: String,
        chaptersFilePath: String,
    ): List<String>

    fun removeChapters(
        inputPath: String,
        outputPath: String,
    ): List<String>

    fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions,
    ): List<String>

    fun exportAudio(
        inputPath: String,
        outputPath: String,
        options: ExportOptions,
    ): List<String>

    fun splitAudio(
        inputPath: String,
        outputPathPattern: String,
        splitTimes: List<Long>, // seconds
    ): List<String>
}
```

**Pattern:** Every method returns `List<String>` — first element is `ffmpegPath`, rest are arguments. Never uses string concatenation for arguments.

---

### 4.4 FFprobeCommandBuilder

**Location:** `data/processing/command/FFprobeCommandBuilder.kt`

**API:**
```kotlin
class FFprobeCommandBuilder(private val ffprobePath: String) {
    fun readAll(filePath: String): List<String>   // format + streams + chapters
    fun readFormat(filePath: String): List<String>
    fun readStreams(filePath: String): List<String>
    fun readChapters(filePath: String): List<String>
}
```

All commands use: `ffprobe -v quiet -print_format json -show_...`

---

### 4.5 FFprobeJsonParser

**Location:** `data/processing/parser/FFprobeJsonParser.kt`

**API:**
```kotlin
class FFprobeJsonParser {
    fun parseChapters(json: String): List<FFprobeChapter>
    fun parseFormat(json: String): FFprobeFormat
    fun parseStreams(json: String): List<FFprobeStream>
    fun parseAll(json: String): FfprobeAllResult  // combination of all three
}
```

**Implementation:** Uses `org.json` (already a project dependency). Maps FFprobe's exact JSON structure to Kotlin DTOs.

**Conversion logic for chapters:**
```kotlin
data class FFprobeChapter(
    val id: Int,
    val timeBase: String?,     // e.g. "1/1000"
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val title: String?,
)
```

The parser converts `startTime`/`endTime` strings (seconds) and `timeBase` into ms-based `Chapter` objects for the domain layer.

---

### 4.6 ProgressParser

**Location:** `data/processing/parser/ProgressParser.kt`

**API:**
```kotlin
data class ProgressEvent(
    val percentage: Float?,
    val speed: String?,       // e.g. "1.2x"
    val bitrate: String?,     // e.g. "128.0kbits/s"
    val eta: String?,         // e.g. "00:01:23"
    val timeMs: Long?,        // current timestamp in ms
    val frame: Int?,
)

class ProgressParser {
    fun parse(line: String, totalDurationMs: Long): ProgressEvent?
}
```

Parses FFmpeg's `time=` field from progress lines:
```
frame=  123 fps= 12 q=28.0 size=    1024kB time=00:01:23.45 bitrate= 128.0kbits/s speed=1.2x
```

Returns `null` for non-progress lines (error messages, info output). Computes percentage from `timeMs / totalDurationMs`.

---

### 4.7 TempFileManager

**Location:** `data/processing/TempFileManager.kt`

**API:**
```kotlin
class TempFileManager @Inject constructor(
    private val context: Context
) {
    suspend fun createMetadataFile(metadata: Map<String, String>): File
    suspend fun createChaptersFile(chapters: List<Chapter>): File
    suspend fun createOutputFile(extension: String): File
    suspend fun createCoverFile(coverData: ByteArray, mimeType: String): File
    suspend fun cleanup(files: List<File>)
    fun registerForCleanup(file: File)
    fun cleanupAll()
}
```

**Behavior:**
- All files created in `context.cacheDir` with prefixed names for easy identification
- Metadata/chapters temp files written as text files in FFmpeg-compatible format
- Output files get unique names to avoid collisions
- `cleanupAll()` called on app lifecycle events or when processing completes
- Files are registered for cleanup even if processing fails mid-way

---

### 4.8 FFmpegService

**Location:** `data/processing/FFmpegService.kt`

**API:**
```kotlin
class FFmpegService @Inject constructor(
    private val binaryManager: FfmpegBinaryManager,
    private val processExecutor: ProcessExecutor,
    private val ffmpegCommandBuilder: FFmpegCommandBuilder,
    private val ffprobeCommandBuilder: FFprobeCommandBuilder,
    private val ffprobeJsonParser: FFprobeJsonParser,
    private val tempFileManager: TempFileManager,
    private val progressParser: ProgressParser,
) {
    // FFprobe operations
    suspend fun readChapters(filePath: String): List<Chapter>
    suspend fun readFormat(filePath: String): FFprobeFormat
    suspend fun readStreams(filePath: String): List<FFprobeStream>
    suspend fun readAllInfo(filePath: String): AllInfo  // streams + format + chapters

    // FFmpeg operations
    suspend fun createM4B(
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult

    suspend fun addChapters(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult

    suspend fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult

    suspend fun exportAudiobook(
        inputPath: String,
        outputPath: String,
        options: ExportOptions,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult
}
```

**Method pattern:**
1. `ensureInitialized()` — delegate to `binaryManager`
2. Build temp files if needed (chapters text file, metadata text file, cover image)
3. Build command via appropriate `CommandBuilder`
4. Execute via `ProcessExecutor` with progress callback
5. Parse output if applicable (FFprobe operations)
6. Cleanup temp files via `tempFileManager`
7. Return result

---

### 4.9 BookRepository Extension

**Location:** `domain/repository/BookRepository.kt` and `data/repository/BookRepositoryImpl.kt`

**New methods on the interface:**
```kotlin
interface BookRepository {
    // Existing methods unchanged (JAudiotagger-based)
    // ...

    // NEW: FFprobe-based reading
    suspend fun readChaptersFromFile(filePath: String): List<Chapter>

    // NEW: FFmpeg-based writing
    suspend fun createM4B(
        context: Context,
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: AudiobookMetadata,
        coverPath: String? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun exportAudiobook(
        context: Context,
        bookId: Int,
        outputPath: String,
        options: ExportOptions,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun replaceChaptersInFile(
        context: Context,
        bookId: Int,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun getAudiobookInfo(filePath: String): AudiobookInfo
}
```

**Non-goal:** The existing `updateBookMetadata`, `updateBookCover`, `updateBookChapters` methods remain JAudiotagger-based and are NOT migrated to FFmpeg. This preserves the fast metadata path.

---

### 4.10 Data Models (DTOs)

```kotlin
// data/processing/dto/FFmpegResult.kt
sealed class FFmpegResult {
    data class Success(
        val exitCode: Int,
        val output: String = "",
    ) : FFmpegResult()
    data class Error(
        val exitCode: Int,
        val message: String,
        val logs: List<String> = emptyList(),
    ) : FFmpegResult() {
        val isExitCodeError: Boolean get() = exitCode != 0
    }
}

// data/processing/dto/FfprobeResult.kt
sealed class FfprobeResult<out T> {
    data class Success<T>(val data: T) : FfprobeResult<T>()
    data class Error(val message: String, val logs: List<String> = emptyList()) : FfprobeResult<Nothing>()
}
```

---

## 5. Threading & Cancellation

- All FFmpegService methods are `suspend` functions
- ProcessExecutor reads streams on `Dispatchers.IO`
- `withContext(Dispatchers.IO)` wrapping in FFmpegService
- Cancellation: `coroutineContext.isActive` checks + `process.destroyForcibly()` if unresponsive
- No `runBlocking` anywhere in the processing layer

---

## 6. Error Handling Strategy

| Layer | Error approach |
|-------|---------------|
| **FfmpegBinaryManager** | Throws on failure (fatal — app can't process without binaries) |
| **ProcessExecutor** | Returns `FFmpegResult` — never throws for process failures |
| **FFprobeJsonParser** | Returns `FfprobeResult` — parsing errors are expected for malformed output |
| **FFmpegService** | Returns `FFmpegResult`/`FfprobeResult` — maps exceptions to error results |
| **BookRepositoryImpl** | Converts `FFmpegResult.Error` to domain-appropriate exceptions for ViewModels |
| **ViewModel** | Catches exceptions and surfaces as UI state (Snackbar, error dialog) |

**Critical rule:** Raw FFmpeg stderr/logs are NEVER exposed directly to the UI. Error messages are mapped to user-friendly strings.

---

## 7. Logging

- All FFmpeg stdout and stderr captured
- Stderr logged at `Timber.tag("FFMPEG").d(...)` in debug builds
- Release builds: only `Timber.tag("FFMPEG").w(...)` and `Timber.tag("FFMPEG").e(...)` for errors
- Each execution gets a unique correlation ID for tracing in logs
- Progress lines are NOT logged (too verbose) — only summary at completion

---

## 8. Migration Strategy

### Phase 1 — FFprobe chapter reading
1. Implement `FFprobeCommandBuilder`, `FFprobeJsonParser`, `FFprobeChapter` DTO
2. Implement `ChapterExtractor` interface
3. Wrap existing `M4bChapterExtractor` behind interface
4. Implement `FFprobeChapterExtractor` behind same interface
5. `BookRepositoryImpl` uses the interface — can swap implementations
6. Both implementations exist in parallel; M4bChapterExtractor is the default initially

### Phase 2 — FfmpegBinaryManager + ProcessExecutor
1. Implement binary initialization and extraction
2. Implement process execution with timeout/cancellation
3. Build CI pipeline for FFmpeg source compilation
4. Test on all three target ABIs

### Phase 3 — FFmpeg-based container operations
1. Implement `FFmpegCommandBuilder` for `createM4B`, `mergeAudio`, `addChapters`
2. Implement `TempFileManager` for metadata/chapters temp files
3. Implement `ProgressParser` for progress reporting
4. Add `FFmpegService` methods
5. Extend `BookRepository` interface
6. Refactor `ProcessingScreen` to optionally use FFmpegService (fallback to M4BTranscoder)

### Phase 4 — Stabilization
1. Battery of tests on real M4B, MP3, AAC files
2. Content:// URI handling verification
3. Edge cases: zero-chapter files, large chapter counts (>100), files with special characters in paths
4. Performance benchmarks vs JAudiotagger and MediaCodec approaches

---

## 9. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **FFmpegService (not FFmpegManager)** | "Service" better communicates coordination responsibility; avoids anti-pattern of god-Manager class |
| **ProcessExecutor separate from FFmpegService** | Single execution abstraction that can be tested in isolation; FFmpegService doesn't need to know about ProcessBuilder |
| **Command Builders** | Makes the 100+ possible FFmpeg command permutations testable without actual FFmpeg execution |
| **DTOs in data/processing/dto/** | FFprobe output format is an implementation detail, not a domain concept; mapping happens in FFmpegService |
| **Sealed results, not exceptions** | Process failures are expected outcomes, not exceptional conditions; callers must handle them |
| **BinaryManager as separate class** | Initialization logic is complex enough (ABI, copy, permissions, versioning) to warrant its own class |
| **Keep M4bChapterExtractor initially** | Proven code; FFprobe version needs field validation before becoming default |

---

## 10. Future Extensibility

The architecture supports these features without structural changes:

- **Bookmark export/import** — new methods on FFmpegCommandBuilder + FFmpegService
- **Audiobook trimming** — `ffmpeg -ss -to -i` via command builder
- **Chapter auto-generation** — silence detection via `ffmpeg -af silencedetect`
- **Loudness normalization** — `ffmpeg -af loudnorm` via existing execution pipeline
- **Cover extraction** — `ffmpeg -i -an -vcodec copy cover.jpg`
- **Waveform generation** — `ffmpeg -i -filter_complex showwaves` → PNG
- **Batch operations** — all FFmpegService methods are stateless; batch is just loop + progress aggregation

---

## 11. Non-Goals (Out of Scope for This Design)

- Replacing JAudiotagger for metadata operations
- Removing the existing `M4BTranscoder` (MediaCodec-based) — kept as fallback
- Adding a custom JSON library (org.json is sufficient)
- Creating a new DI module (Hilt auto-binding handles the new classes)
- Build-time FFmpeg compilation scripts (separate CI design)
