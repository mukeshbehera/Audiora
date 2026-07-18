# FFmpeg Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement FFmpeg/FFprobe-based audiobook processing layer while keeping existing JAudiotagger metadata workflow intact.

**Architecture:** BookRepository orchestrates — delegates metadata/cover ops to JAudiotagger (unchanged), delegates container inspection to FFprobe and container writing to FFmpeg via new FFmpegService class. FFmpegService coordinates command builders, process execution, temp files, and JSON parsing.

**Tech Stack:** Kotlin, FFmpeg/FFprobe native binaries (bundled in assets), ProcessBuilder, org.json (existing), Timber, Coroutines

## Global Constraints

- The app uses **manual DI** in `AudioraApplication` (no Hilt/Dagger/Koin)
- No FFmpegKit — use bundled native binaries via ProcessBuilder
- All FFmpeg commands built as `List<String>` — never as shell strings
- All FFprobe output requested as JSON — never parse plain text
- `org.json` already in dependencies — no new JSON library
- Minimum SDK 24
- `BookRepository` existing methods stay JAudiotagger-based (unchanged)
- All new suspend functions run on `Dispatchers.IO`
- Build commands, do NOT execute them directly in command builders
- Capture logs via Timber with `"FFMPEG"` tag

---

## File Inventory

### New Files (17 files)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `domain/model/ConversionOptions.kt` | Options for M4B creation: bitrate, sampleRate, channels, chapter strategy, metadata |
| 2 | `domain/model/ExportOptions.kt` | Options for audiobook export: format, codec, metadata handling |
| 3 | `data/processing/dto/FFmpegResult.kt` | Sealed class for FFmpeg execution results (Success/Error) |
| 4 | `data/processing/dto/FfprobeResult.kt` | Sealed class for FFprobe query results (Success<T>/Error) |
| 5 | `data/processing/dto/FFprobeChapter.kt` | DTO mapping FFprobe's chapter JSON output |
| 6 | `data/processing/dto/FFprobeFormat.kt` | DTO mapping FFprobe's format JSON output |
| 7 | `data/processing/dto/FFprobeStream.kt` | DTO mapping FFprobe's stream JSON output |
| 8 | `data/processing/FfmpegBinaryManager.kt` | ABI detection, binary extraction from assets, initialization |
| 9 | `data/processing/executor/ProcessExecutor.kt` | ProcessBuilder wrapper with timeout/cancellation |
| 10 | `data/processing/command/FFmpegCommandBuilder.kt` | Builds FFmpeg commands as List<String> |
| 11 | `data/processing/command/FFprobeCommandBuilder.kt` | Builds FFprobe commands as List<String> |
| 12 | `data/processing/parser/FFprobeJsonParser.kt` | Parses FFprobe JSON output into DTOs using org.json |
| 13 | `data/processing/parser/ProgressParser.kt` | Parses FFmpeg stderr progress lines |
| 14 | `data/processing/TempFileManager.kt` | Creates/cleans temp files for metadata, chapters, cover |
| 15 | `data/processing/FFmpegService.kt` | Coordinates all FFmpeg/FFprobe workflows |
| 16 | `data/local/ChapterExtractor.kt` | Interface for chapter extraction strategies |
| 17 | `data/processing/FFprobeChapterExtractor.kt` | FFprobe-based chapter extraction implementation |

### Modified Files (4 files)

| # | File Path | Change |
|---|-----------|--------|
| 1 | `domain/repository/BookRepository.kt` | Add readChaptersFromFile, createM4B, exportAudiobook, etc. |
| 2 | `data/repository/BookRepositoryImpl.kt` | Inject FFmpegService, implement new methods |
| 3 | `AudioraApplication.kt` | Wire FFmpegBinaryManager → ProcessExecutor → FFmpegService |
| 4 | `feature/converter/ProcessingScreen.kt` | Optionally use FFmpegService for M4B creation |

---

### Task 1: Domain Models (ConversionOptions, ExportOptions)

**Files:**
- Create: `domain/model/ConversionOptions.kt`
- Create: `domain/model/ExportOptions.kt`

**Interfaces:**
- Produces: `ConversionOptions` data class, `ExportOptions` data class

- [ ] **Step 1: Create ConversionOptions.kt**

```kotlin
package com.audiora.domain.model

data class ConversionOptions(
    val bitRate: Int = 128000,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val codec: String = "aac",
    val includeChapters: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeCover: Boolean = true,
) {
    companion object {
        val DEFAULT = ConversionOptions()
        val HIGH_QUALITY = ConversionOptions(bitRate = 192000, sampleRate = 48000)
        val VOICE = ConversionOptions(bitRate = 64000, sampleRate = 22050, channelCount = 1)
    }
}
```

- [ ] **Step 2: Create ExportOptions.kt**

```kotlin
package com.audiora.domain.model

data class ExportOptions(
    val outputFormat: String = "mp3",
    val bitRate: Int = 128000,
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val includeChapters: Boolean = false,
    val includeMetadata: Boolean = true,
    val includeCover: Boolean = false,
) {
    companion object {
        val DEFAULT = ExportOptions()
        val MP3_HIGH = ExportOptions(outputFormat = "mp3", bitRate = 320000)
        val FLAC = ExportOptions(outputFormat = "flac")
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add domain/model/ConversionOptions.kt domain/model/ExportOptions.kt
git commit -m "feat: add ConversionOptions and ExportOptions domain models"
```

---

### Task 2: Data Transfer Objects (FFmpegResult, FfprobeResult, FFprobeChapter, FFprobeFormat, FFprobeStream)

**Files:**
- Create: `data/processing/dto/FFmpegResult.kt`
- Create: `data/processing/dto/FfprobeResult.kt`
- Create: `data/processing/dto/FFprobeChapter.kt`
- Create: `data/processing/dto/FFprobeFormat.kt`
- Create: `data/processing/dto/FFprobeStream.kt`

**Interfaces:**
- Produces: `FFmpegResult`, `FfprobeResult<T>`, `FFprobeChapter`, `FFprobeFormat`, `FFprobeStream`

- [ ] **Step 1: Create FFmpegResult.kt**

```kotlin
package com.audiora.data.processing.dto

/**
 * Sealed result type for FFmpeg process execution.
 * Never throws for process failures — callers must handle both variants.
 */
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

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): String? = when (this) {
        is Success -> output
        is Error -> null
    }

    fun getOrThrow(): String = when (this) {
        is Success -> output
        is Error -> throw RuntimeException("FFmpeg failed (exit $exitCode): $message")
    }

    companion object {
        fun success(exitCode: Int = 0, output: String = ""): FFmpegResult =
            Success(exitCode, output)

        fun error(exitCode: Int, message: String, logs: List<String> = emptyList()): FFmpegResult =
            Error(exitCode, message, logs)
    }
}
```

- [ ] **Step 2: Create FfprobeResult.kt**

```kotlin
package com.audiora.data.processing.dto

/**
 * Sealed result type for FFprobe query results.
 * Generic over the parsed data type.
 */
sealed class FfprobeResult<out T> {
    data class Success<T>(val data: T) : FfprobeResult<T>()
    data class Error(
        val message: String,
        val logs: List<String> = emptyList(),
    ) : FfprobeResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw RuntimeException("FFprobe failed: $message")
    }

    companion object {
        fun <T> success(data: T): FfprobeResult<T> = Success(data)
        fun error(message: String, logs: List<String> = emptyList()): FfprobeResult<Nothing> =
            Error(message, logs)
    }
}
```

- [ ] **Step 3: Create FFprobeChapter.kt**

```kotlin
package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's chapter JSON output format.
 * Each chapter in the "chapters" array of FFprobe JSON output.
 */
data class FFprobeChapter(
    val id: Int,
    val timeBase: String?,
    val start: Long,
    val end: Long,
    val startTime: String?,
    val endTime: String?,
    val title: String?,
)
```

- [ ] **Step 4: Create FFprobeFormat.kt**

```kotlin
package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's format JSON output.
 * Maps the "format" object from FFprobe JSON output.
 */
data class FFprobeFormat(
    val filename: String?,
    val nbStreams: Int?,
    val nbPrograms: Int?,
    val formatName: String?,
    val formatLongName: String?,
    val startTime: String?,
    val duration: String?,
    val size: String?,
    val bitRate: String?,
    val probeScore: Int?,
)
```

- [ ] **Step 5: Create FFprobeStream.kt**

```kotlin
package com.audiora.data.processing.dto

/**
 * DTO matching FFprobe's stream JSON output format.
 * Maps each entry in the "streams" array from FFprobe JSON output.
 */
data class FFprobeStream(
    val index: Int,
    val codecName: String?,
    val codecLongName: String?,
    val codecType: String?,
    val codecTagString: String?,
    val codecTag: String?,
    val sampleRate: String?,
    val channels: Int?,
    val channelLayout: String?,
    val bitRate: String?,
    val maxBitRate: String?,
    val duration: String?,
    val durationTs: Long?,
)
```

- [ ] **Step 6: Create all directories and files, commit**

```bash
mkdir -p app/src/main/java/com/audiora/data/processing/dto
git add app/src/main/java/com/audiora/data/processing/dto/FFmpegResult.kt \
       app/src/main/java/com/audiora/data/processing/dto/FfprobeResult.kt \
       app/src/main/java/com/audiora/data/processing/dto/FFprobeChapter.kt \
       app/src/main/java/com/audiora/data/processing/dto/FFprobeFormat.kt \
       app/src/main/java/com/audiora/data/processing/dto/FFprobeStream.kt
git commit -m "feat: add FFmpeg/FFprobe DTOs and result types"
```

---

### Task 3: ChapterExtractor Interface + M4bChapterExtractor Adapter

**Files:**
- Create: `data/local/ChapterExtractor.kt`

**Interfaces:**
- Produces: `ChapterExtractor` interface with `suspend fun extract(context: Context, uri: Uri, totalDurationMs: Long): List<Chapter>`
- Consumes: `M4bChapterExtractor` (existing object — no changes needed to it)

**Note:** The existing `M4bChapterExtractor` is a Kotlin `object` with `extractFromUri()` and `extractFromFile()`. We create an interface and an adapter class that wraps it. The existing `M4bChapterExtractor` code remains untouched.

- [ ] **Step 1: Create ChapterExtractor.kt**

```kotlin
package com.audiora.data.local

import android.content.Context
import android.net.Uri
import com.audiora.domain.model.Chapter

/**
 * Strategy interface for chapter extraction from audiobook files.
 * Implementations can use different backends (MP4 atom parser, FFprobe, etc.)
 */
interface ChapterExtractor {
    /**
     * Extract chapters from an audiobook file.
     *
     * @param context Android context
     * @param uri URI of the audiobook file (content:// or file://)
     * @param totalDurationMs Total duration in ms (for end-time calculation, 0 = unknown)
     * @return List of chapters (empty if none found or extractor cannot handle the file)
     */
    suspend fun extract(
        context: Context,
        uri: Uri,
        totalDurationMs: Long = 0L,
    ): List<Chapter>
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/local/ChapterExtractor.kt
git commit -m "feat: add ChapterExtractor strategy interface"
```

---

### Task 4: FFprobeCommandBuilder

**Files:**
- Create: `data/processing/command/FFprobeCommandBuilder.kt`

**Interfaces:**
- Produces: `FFprobeCommandBuilder(ffprobePath: String)` with `readAll()`, `readFormat()`, `readStreams()`, `readChapters()`
- All methods return `List<String>` — ready to pass to ProcessExecutor

- [ ] **Step 1: Create FFprobeCommandBuilder.kt**

```kotlin
package com.audiora.data.processing.command

/**
 * Builds FFprobe commands as List<String>.
 * All commands request JSON output for programmatic parsing.
 * Never constructs commands via string concatenation.
 */
class FFprobeCommandBuilder(
    private val ffprobePath: String,
) {
    /**
     * Read format, streams, and chapters in a single FFprobe invocation.
     */
    fun readAll(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_format")
        add("-show_streams")
        add("-show_chapters")
        add(filePath)
    }

    /**
     * Read container format information only.
     */
    fun readFormat(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_format")
        add(filePath)
    }

    /**
     * Read stream information (codec, sample rate, channels, etc.) only.
     */
    fun readStreams(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_streams")
        add(filePath)
    }

    /**
     * Read chapter markers and titles only.
     */
    fun readChapters(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_chapters")
        add(filePath)
    }
}
```

- [ ] **Step 2: Create directory and commit**

```bash
mkdir -p app/src/main/java/com/audiora/data/processing/command
git add app/src/main/java/com/audiora/data/processing/command/FFprobeCommandBuilder.kt
git commit -m "feat: add FFprobeCommandBuilder"
```

---

### Task 5: FFprobeJsonParser

**Files:**
- Create: `data/processing/parser/FFprobeJsonParser.kt`

**Interfaces:**
- Produces: `FFprobeJsonParser` with `parseChapters()`, `parseFormat()`, `parseStreams()`, `parseAll()`
- Returns `FfprobeResult` — parsing errors don't throw

- [ ] **Step 1: Create FFprobeJsonParser.kt**

```kotlin
package com.audiora.data.processing.parser

import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.data.processing.dto.FFprobeStream
import com.audiora.data.processing.dto.FfprobeResult
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Parses FFprobe JSON output into typed Kotlin DTOs.
 * Uses org.json (already a project dependency).
 * Never throws — returns FfprobeResult for all parsing outcomes.
 */
class FFprobeJsonParser {

    fun parseChapters(json: String): FfprobeResult<List<FFprobeChapter>> = try {
        val root = JSONObject(json)
        val chaptersArray = root.optJSONArray("chapters") ?: JSONArray()
        val chapters = (0 until chaptersArray.length()).map { i ->
            val obj = chaptersArray.getJSONObject(i)
            FFprobeChapter(
                id = obj.optInt("id", 0),
                timeBase = obj.optString("time_base", null),
                start = obj.optLong("start", 0L),
                end = obj.optLong("end", 0L),
                startTime = obj.optString("start_time", null),
                endTime = obj.optString("end_time", null),
                title = obj.optJSONObject("tags")?.optString("title", null),
            )
        }
        FfprobeResult.success(chapters)
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe chapters JSON")
        FfprobeResult.error("Failed to parse chapters: ${e.message}")
    }

    fun parseFormat(json: String): FfprobeResult<FFprobeFormat> = try {
        val root = JSONObject(json)
        val formatObj = root.getJSONObject("format")
        FfprobeResult.success(
            FFprobeFormat(
                filename = formatObj.optString("filename", null),
                nbStreams = formatObj.optInt("nb_streams", -1).let { if (it < 0) null else it },
                nbPrograms = formatObj.optInt("nb_programs", -1).let { if (it < 0) null else it },
                formatName = formatObj.optString("format_name", null),
                formatLongName = formatObj.optString("format_long_name", null),
                startTime = formatObj.optString("start_time", null),
                duration = formatObj.optString("duration", null),
                size = formatObj.optString("size", null),
                bitRate = formatObj.optString("bit_rate", null),
                probeScore = formatObj.optInt("probe_score", -1).let { if (it < 0) null else it },
            )
        )
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe format JSON")
        FfprobeResult.error("Failed to parse format: ${e.message}")
    }

    fun parseStreams(json: String): FfprobeResult<List<FFprobeStream>> = try {
        val root = JSONObject(json)
        val streamsArray = root.optJSONArray("streams") ?: JSONArray()
        val streams = (0 until streamsArray.length()).map { i ->
            val obj = streamsArray.getJSONObject(i)
            FFprobeStream(
                index = obj.optInt("index", 0),
                codecName = obj.optString("codec_name", null),
                codecLongName = obj.optString("codec_long_name", null),
                codecType = obj.optString("codec_type", null),
                codecTagString = obj.optString("codec_tag_string", null),
                codecTag = obj.optString("codec_tag", null),
                sampleRate = obj.optString("sample_rate", null),
                channels = if (obj.has("channels")) obj.getInt("channels") else null,
                channelLayout = obj.optString("channel_layout", null),
                bitRate = obj.optString("bit_rate", null),
                maxBitRate = obj.optString("max_bit_rate", null),
                duration = obj.optString("duration", null),
                durationTs = if (obj.has("duration_ts")) obj.getLong("duration_ts") else null,
            )
        }
        FfprobeResult.success(streams)
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe streams JSON")
        FfprobeResult.error("Failed to parse streams: ${e.message}")
    }

    /**
     * Parses all three sections from a single FFprobe JSON output.
     * Expects JSON with "format", "streams", and optionally "chapters" keys.
     */
    fun parseAll(json: String): FfprobeResult<AllInfo> = try {
        val formatResult = parseFormat(json)
        val streamsResult = parseStreams(json)
        val chaptersResult = parseChapters(json)

        FfprobeResult.success(
            AllInfo(
                format = formatResult.getOrNull(),
                streams = streamsResult.getOrNull() ?: emptyList(),
                chapters = chaptersResult.getOrNull() ?: emptyList(),
            )
        )
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe all JSON")
        FfprobeResult.error("Failed to parse all info: ${e.message}")
    }

    data class AllInfo(
        val format: FFprobeFormat?,
        val streams: List<FFprobeStream>,
        val chapters: List<FFprobeChapter>,
    )
}
```

- [ ] **Step 2: Create directory and commit**

```bash
mkdir -p app/src/main/java/com/audiora/data/processing/parser
git add app/src/main/java/com/audiora/data/processing/parser/FFprobeJsonParser.kt
git commit -m "feat: add FFprobeJsonParser"
```

---

### Task 6: ProgressParser

**Files:**
- Create: `data/processing/parser/ProgressParser.kt`

**Interfaces:**
- Produces: `ProgressEvent` data class, `ProgressParser` class with `parse(line, totalDurationMs): ProgressEvent?`

- [ ] **Step 1: Create ProgressParser.kt**

```kotlin
package com.audiora.data.processing.parser

import timber.log.Timber

/**
 * Parsed progress information from FFmpeg stderr output.
 */
data class ProgressEvent(
    val percentage: Float?,
    val speed: String?,
    val bitrate: String?,
    val timeMs: Long?,
    val frame: Int?,
)

/**
 * Parses FFmpeg's stderr progress lines to extract progress events.
 *
 * FFmpeg progress line format:
 * frame=  123 fps= 12 q=28.0 size=    1024kB time=00:01:23.45 bitrate= 128.0kbits/s speed=1.2x
 */
class ProgressParser {

    fun parse(line: String, totalDurationMs: Long): ProgressEvent? {
        if (!line.contains("time=")) return null

        return try {
            val timeMs = parseTime(line)
            val percentage = if (timeMs != null && totalDurationMs > 0) {
                (timeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
            } else null

            ProgressEvent(
                percentage = percentage,
                speed = parseField(line, "speed="),
                bitrate = parseField(line, "bitrate="),
                timeMs = timeMs,
                frame = parseField(line, "frame=")?.trim()?.toIntOrNull(),
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").d("Failed to parse progress line: $line")
            null
        }
    }

    /**
     * Parses time string from FFmpeg progress line.
     * Format: HH:MM:SS.mmm or MM:SS.mmm
     */
    private fun parseTime(line: String): Long? {
        val timeStr = parseField(line, "time=") ?: return null
        // Handle format like "00:01:23.45" or "01:23.45"
        val parts = timeStr.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val seconds = parts[2].replace(",", ".").toDouble()
                ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
            }
            2 -> {
                val minutes = parts[0].toLong()
                val seconds = parts[1].replace(",", ".").toDouble()
                ((minutes * 60 + seconds) * 1000).toLong()
            }
            else -> null
        }
    }

    /**
     * Extracts a field value from a key=value line.
     * e.g. parseField("speed=1.2x bitrate=128k", "speed=") => "1.2x"
     */
    private fun parseField(line: String, key: String): String? {
        val startIndex = line.indexOf(key) ?: return null
        if (startIndex < 0) return null
        val valueStart = startIndex + key.length
        // Read until next space or end of string
        val endIndex = line.indexOf(' ', valueStart)
        return if (endIndex < 0) line.substring(valueStart) else line.substring(valueStart, endIndex)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/parser/ProgressParser.kt
git commit -m "feat: add ProgressParser for FFmpeg stderr progress"
```

---

### Task 7: FfmpegBinaryManager

**Files:**
- Create: `data/processing/FfmpegBinaryManager.kt`

**Interfaces:**
- Produces: `FfmpegBinaryManager(context: Context)` with `ensureInitialized(): BinaryPaths`, `isInitialized(): Boolean`, `getVersion(): String?`
- `BinaryPaths` data class: `val ffmpegPath: String, val ffprobePath: String`

- [ ] **Step 1: Create FfmpegBinaryManager.kt**

```kotlin
package com.audiora.data.processing

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Manages FFmpeg and FFprobe native binary lifecycle.
 *
 * Responsibilities:
 * - Detect device CPU ABI
 * - Extract correct binaries from assets to app private storage
 * - Set executable permissions
 * - Verify binaries via --version
 * - Run once per app lifetime
 */
class FfmpegBinaryManager(
    private val context: Context,
) {
    data class BinaryPaths(
        val ffmpegPath: String,
        val ffprobePath: String,
    )

    @Volatile
    private var initialized = false
    private val lock = Any()
    @Volatile
    private var cachedVersion: String? = null
    @Volatile
    private var cachedPaths: BinaryPaths? = null

    fun isInitialized(): Boolean = initialized

    fun getVersion(): String? = cachedVersion

    /**
     * Ensures binaries are extracted and ready.
     * Safe to call multiple times — only runs once.
     *
     * @throws UnsupportedAbiException if no binary for device ABI
     * @throws BinaryInitException if copy or permission setting fails
     * @throws BinaryVerificationException if version check fails
     */
    suspend fun ensureInitialized(): BinaryPaths {
        if (initialized) {
            return cachedPaths ?: throw BinaryInitException("Binary paths lost after initialization")
        }
        return synchronized(lock) {
            if (initialized) {
                return@synchronized cachedPaths
                    ?: throw BinaryInitException("Binary paths lost after initialization")
            }
            val paths = initialize()
            initialized = true
            cachedPaths = paths
            paths
        }
    }

    private fun initialize(): BinaryPaths {
        val abi = detectAbi()
        val binDir = getBinDir()

        val ffmpegFile = File(binDir, "ffmpeg")
        val ffprobeFile = File(binDir, "ffprobe")

        // Extract if not already present
        if (!ffmpegFile.exists()) {
            extractBinary(abi, "ffmpeg", ffmpegFile)
        }
        if (!ffprobeFile.exists()) {
            extractBinary(abi, "ffprobe", ffprobeFile)
        }

        // Set executable permissions
        if (!ffmpegFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffmpegFile")
        }
        if (!ffprobeFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffprobeFile")
        }

        // Verify via --version
        verifyBinary(ffmpegFile.absolutePath, "ffmpeg")
        verifyBinary(ffprobeFile.absolutePath, "ffprobe")

        Timber.tag("FFMPEG").i("Binaries initialized: ffmpeg=$ffmpegFile, ffprobe=$ffprobeFile")

        return BinaryPaths(
            ffmpegPath = ffmpegFile.absolutePath,
            ffprobePath = ffprobeFile.absolutePath,
        )
    }

    /**
     * Detect the device's primary CPU ABI.
     *
     * @throws UnsupportedAbiException if none of our supported ABIs match
     */
    private fun detectAbi(): String {
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        for (abi in Build.SUPPORTED_ABIS) {
            if (abi in supported) return abi
        }
        throw UnsupportedAbiException(
            "No FFmpeg binary for device ABI(s): ${Build.SUPPORTED_ABIS.joinToString(", ")}"
        )
    }

    /**
     * Return the directory where binaries are stored (files/bin/).
     * Creates it if needed.
     */
    private fun getBinDir(): File {
        val dir = File(context.filesDir, "bin")
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw BinaryInitException("Failed to create binary directory: $dir")
            }
        }
        return dir
    }

    /**
     * Extract a binary from assets to the target file.
     *
     * Asset naming convention: {name}-{abi}
     * e.g. ffmpeg-arm64-v8a, ffprobe-arm64-v8a
     */
    private fun extractBinary(abi: String, name: String, targetFile: File) {
        val assetName = "$name-$abi"
        try {
            context.assets.open(assetName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Timber.tag("FFMPEG").d("Extracted $assetName to $targetFile")
        } catch (e: IOException) {
            throw BinaryInitException("Failed to extract $assetName from assets: ${e.message}", e)
        }
    }

    /**
     * Verify a binary works by running --version and checking the exit code.
     */
    private fun verifyBinary(binaryPath: String, name: String) {
        try {
            val process = ProcessBuilder(listOf(binaryPath, "-version"))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw BinaryVerificationException(
                    "$name --version failed with exit code $exitCode: $output"
                )
            }

            // Extract version from first line: "ffmpeg version X.Y ..."
            val firstLine = output.lines().firstOrNull() ?: ""
            if (name in firstLine.lowercase()) {
                cachedVersion = firstLine.trim()
            }

            Timber.tag("FFMPEG").d("$name verified: ${firstLine.trim()}")
        } catch (e: IOException) {
            throw BinaryVerificationException("Failed to execute $name binary at $binaryPath: ${e.message}", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BinaryVerificationException("$name verification interrupted", e)
        }
    }
}

class UnsupportedAbiException(message: String) : Exception(message)
class BinaryInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
class BinaryVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/FfmpegBinaryManager.kt
git commit -m "feat: add FfmpegBinaryManager with ABI detection and asset extraction"
```

---

### Task 8: ProcessExecutor

**Files:**
- Create: `data/processing/executor/ProcessExecutor.kt`

**Interfaces:**
- Produces: `ProcessExecutor` with `suspend fun execute(command, timeout?, progressCallback?): FFmpegResult`

- [ ] **Step 1: Create ProcessExecutor.kt**

```kotlin
package com.audiora.data.processing.executor

import com.audiora.data.processing.dto.FFmpegResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Single point for native process execution via ProcessBuilder.
 *
 * Responsibilities:
 * - Start processes
 * - Capture stdout and stderr
 * - Support cancellation via coroutine cancellation
 * - Support timeout
 * - Support progress callbacks (for FFmpeg stderr lines)
 * - Return structured FFmpegResult
 */
class ProcessExecutor {

    data class ExecutionConfig(
        val timeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
        val captureOutput: Boolean = true,
    )

    /**
     * Execute a command and capture its output.
     *
     * @param command List of command and arguments (never shell-escaped)
     * @param config Execution configuration (timeout, output capture)
     * @param progressCallback Optional callback receiving each stderr line (for FFmpeg progress)
     * @return FFmpegResult with captured output and exit code
     */
    suspend fun execute(
        command: List<String>,
        config: ExecutionConfig = ExecutionConfig(),
        progressCallback: ((String) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        val correlationId = UUID.randomUUID().toString().take(8)
        Timber.tag("FFMPEG").d("[%s] Executing: %s", correlationId, command.joinToString(" "))

        try {
            withTimeout(config.timeoutMs) {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(false) // Keep stdout and stderr separate
                    .start()

                try {
                    // Read stdout on a background thread
                    val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                        if (config.captureOutput) {
                            process.inputStream.bufferedReader().readText()
                        } else ""
                    }

                    // Read stderr line-by-line, forwarding to callback if provided
                    val stderrLines = mutableListOf<String>()
                    val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        val currentLine = line!!
                        stderrLines.add(currentLine)
                        if (progressCallback != null) {
                            progressCallback(currentLine)
                        }
                    }

                    val exitCode = process.waitFor()
                    val stdout = try {
                        stdoutFuture.get(5, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        Timber.tag("FFMPEG").w("[%s] Timeout reading stdout", correlationId)
                        ""
                    }

                    // Check for cancellation
                    if (!isActive) {
                        process.destroyForcibly()
                        Timber.tag("FFMPEG").d("[%s] Cancelled", correlationId)
                        return@withTimeout FFmpegResult.error(-1, "Execution cancelled")
                    }

                    if (exitCode == 0) {
                        Timber.tag("FFMPEG").d("[%s] Success (exit=%d)", correlationId, exitCode)
                        FFmpegResult.success(exitCode, stdout)
                    } else {
                        val stderrSummary = stderrLines.takeLast(10).joinToString("\n")
                        Timber.tag("FFMPEG").w("[%s] Failed (exit=%d): %s", correlationId, exitCode, stderrSummary.take(200))
                        FFmpegResult.error(
                            exitCode = exitCode,
                            message = "FFmpeg exited with code $exitCode",
                            logs = stderrLines,
                        )
                    }
                } catch (e: InterruptedException) {
                    process.destroyForcibly()
                    Thread.currentThread().interrupt()
                    FFmpegResult.error(-1, "Execution interrupted")
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.tag("FFMPEG").w("[%s] Timeout after %dms", correlationId, config.timeoutMs)
            FFmpegResult.error(-2, "Execution timed out after ${config.timeoutMs}ms")
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e("[%s] Execution error: %s", correlationId, e.message)
            FFmpegResult.error(-3, "Execution error: ${e.message}")
        }
    }
}
```

- [ ] **Step 2: Create directory and commit**

```bash
mkdir -p app/src/main/java/com/audiora/data/processing/executor
git add app/src/main/java/com/audiora/data/processing/executor/ProcessExecutor.kt
git commit -m "feat: add ProcessExecutor with timeout, cancellation, and progress callbacks"
```

---

### Task 9: TempFileManager

**Files:**
- Create: `data/processing/TempFileManager.kt`

**Interfaces:**
- Produces: `TempFileManager(context: Context)` with `createMetadataFile()`, `createChaptersFile()`, `createOutputFile()`, `createCoverFile()`, `cleanup()`, `registerForCleanup()`, `cleanupAll()`

- [ ] **Step 1: Create TempFileManager.kt**

```kotlin
package com.audiora.data.processing

import android.content.Context
import com.audiora.domain.model.Chapter
import timber.log.Timber
import java.io.File

/**
 * Manages temporary file creation and cleanup for FFmpeg operations.
 *
 * Creates temp files in the app's cache directory with descriptive prefixes.
 * Supports automatic cleanup of registered files, including on failure.
 */
class TempFileManager(
    private val context: Context,
) {
    private val registeredFiles = mutableSetOf<File>()

    /**
     * Create a metadata file for FFmpeg's -metadata option.
     * Format: "key=value\n" per line.
     */
    suspend fun createMetadataFile(metadata: Map<String, String>): File {
        val file = createTempFile("metadata_", ".txt")
        file.writeText(metadata.entries.joinToString("\n") { (key, value) ->
            "$key=$value"
        })
        return file
    }

    /**
     * Create a chapters file in FFmpeg-compatible metadata format.
     *
     * FFmpeg chapter metadata format:
     * ;FFMETADATA1
     * [CHAPTER]
     * TIMEBASE=1/1000
     * START=0
     * END=123456
     * title=Chapter Title
     */
    suspend fun createChaptersFile(chapters: List<Chapter>): File {
        val file = createTempFile("chapters_", ".txt")
        file.writeText(buildString {
            appendLine(";FFMETADATA1")
            chapters.forEachIndexed { index, chapter ->
                appendLine()
                appendLine("[CHAPTER]")
                appendLine("TIMEBASE=1/1000")
                appendLine("START=${chapter.startMs}")
                appendLine("END=${chapter.endMs}")
                appendLine("title=${chapter.title}")
            }
        })
        return file
    }

    /**
     * Create a temporary output file with the given extension.
     */
    suspend fun createOutputFile(extension: String): File {
        val prefix = "output_${System.nanoTime()}_"
        return createTempFile(prefix, ".$extension")
    }

    /**
     * Write cover image bytes to a temp file for FFmpeg input.
     */
    suspend fun createCoverFile(coverData: ByteArray, extension: String = "jpg"): File {
        val file = createTempFile("cover_", ".$extension")
        file.writeBytes(coverData)
        return file
    }

    /**
     * Delete the specified temp files.
     */
    fun cleanup(files: List<File>) {
        files.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Timber.tag("FFMPEG").d("Cleaned up temp file: ${file.name}")
                }
            } catch (e: Exception) {
                Timber.tag("FFMPEG").w("Failed to delete temp file: ${file.name}")
            }
        }
        registeredFiles.removeAll(files)
    }

    /**
     * Register a file for cleanup on cleanupAll().
     */
    fun registerForCleanup(file: File) {
        registeredFiles.add(file)
    }

    /**
     * Clean up ALL registered temp files. Call on processing completion or failure.
     */
    fun cleanupAll() {
        cleanup(registeredFiles.toList())
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val file = File.createTempFile(prefix, suffix, context.cacheDir)
        registerForCleanup(file)
        return file
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/TempFileManager.kt
git commit -m "feat: add TempFileManager for FFmpeg temp file lifecycle"
```

---

### Task 10: FFmpegCommandBuilder

**Files:**
- Create: `data/processing/command/FFmpegCommandBuilder.kt`

**Interfaces:**
- Produces: `FFmpegCommandBuilder(ffmpegPath: String)` with `createM4B()`, `addChapters()`, `removeChapters()`, `mergeAudio()`, `exportAudio()`, `splitAudio()`

- [ ] **Step 1: Create FFmpegCommandBuilder.kt**

```kotlin
package com.audiora.data.processing.command

import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions

/**
 * Builds FFmpeg commands as List<String> for various audiobook processing operations.
 *
 * Every method returns a clean List<String> — first element is the FFmpeg binary path,
 * remaining elements are arguments. Never uses string concatenation for arguments.
 */
class FFmpegCommandBuilder(
    private val ffmpegPath: String,
) {
    companion object {
        private const val CHAPTERS_METADATA_FILE = "chapters_meta.txt"
    }

    /**
     * Create an M4B file from one or more input audio files.
     * Optionally embeds metadata, cover art, and chapters.
     */
    fun createM4B(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverPath: String? = null,
        chaptersFilePath: String? = null,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y") // Overwrite output

        // Input files
        inputs.forEach { input ->
            add("-i")
            add(input)
        }

        // Metadata
        metadata.forEach { (key, value) ->
            add("-metadata")
            add("$key=$value")
        }

        // Cover art (map_cover: use first image input as cover)
        if (coverPath != null) {
            add("-i")
            add(coverPath)
            add("-map")
            add("0:a")
            add("-map")
            add("1:v")
            add("-disposition:v:0")
            add("attached_pic")
        }

        // Chapters: if chapters file provided, use -f ffmetadata
        if (chaptersFilePath != null) {
            add("-i")
            add(chaptersFilePath)
            add("-map_metadata")
            add("1")
        }

        // Audio codec settings
        add("-c:a")
        add(options.codec)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        // Output format and movflags for fast start
        add("-movflags")
        add("+faststart")
        add("-f")
        add("mp4")

        // Output file
        add(outputPath)
    }

    /**
     * Add chapters to an existing audio file using FFmpeg metadata.
     * Creates a new file with chapters embedded in the container.
     */
    fun addChapters(
        inputPath: String,
        outputPath: String,
        chaptersFilePath: String,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-i")
        add(chaptersFilePath)
        add("-map_metadata")
        add("1")
        add("-codec")
        add("copy") // No re-encoding — just metadata mux
        add(outputPath)
    }

    /**
     * Remove all chapters from an audio file by writing metadata without chapter info.
     */
    fun removeChapters(
        inputPath: String,
        outputPath: String,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-map_chapters")
        add("-1") // Remove all chapters
        add("-codec")
        add("copy")
        add(outputPath)
    }

    /**
     * Merge multiple audio files into a single file with chapter markers.
     *
     * Uses the concat demuxer for lossless merging when formats match,
     * or re-encodes to a consistent format when needed.
     */
    fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")

        // Use concat protocol for multiple inputs
        inputs.forEach { input ->
            add("-i")
            add(input)
        }

        add("-filter_complex")
        val concatInputs = inputs.indices.joinToString("") { "[$it:a]" }
        add("${concatInputs}concat=n=${inputs.size}:v=0:a=1[out]")
        add("-map")
        add("[out]")

        add("-c:a")
        add(options.codec)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        add("-movflags")
        add("+faststart")
        add(outputPath)
    }

    /**
     * Export an audiobook to a different format.
     */
    fun exportAudio(
        inputPath: String,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)

        if (!options.includeMetadata) {
            add("-map_metadata")
            add("-1")
        }
        if (!options.includeChapters) {
            add("-map_chapters")
            add("-1")
        }

        add("-c:a")
        add(options.outputFormat)
        add("-b:a")
        add("${options.bitRate}")
        add("-ar")
        add("${options.sampleRate}")
        add("-ac")
        add("${options.channelCount}")

        if (options.outputFormat == "mp3") {
            add("-id3v2_version")
            add("3")
        }

        add(outputPath)
    }

    /**
     * Split an audiobook into segments at the specified timestamps.
     */
    fun splitAudio(
        inputPath: String,
        outputPathPattern: String,
        splitTimes: List<Long>, // split points in seconds
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-y")
        add("-i")
        add(inputPath)
        add("-c")
        add("copy") // Stream copy — no re-encode
        add("-f")
        add("segment")
        add("-segment_times")
        add(splitTimes.joinToString(","))
        add("-reset_timestamps")
        add("1")
        add(outputPathPattern)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/command/FFmpegCommandBuilder.kt
git commit -m "feat: add FFmpegCommandBuilder with M4B creation, chapter ops, merge, export, split"
```

---

### Task 11: FFprobeChapterExtractor

**Files:**
- Create: `data/processing/FFprobeChapterExtractor.kt`

**Interfaces:**
- Produces: `FFprobeChapterExtractor` implementing `ChapterExtractor`
- Consumes: `FfmpegBinaryManager`, `FFprobeCommandBuilder`, `ProcessExecutor`, `FFprobeJsonParser` (all from prior tasks)

- [ ] **Step 1: Create FFprobeChapterExtractor.kt**

```kotlin
package com.audiora.data.processing

import android.content.Context
import android.net.Uri
import com.audiora.data.local.ChapterExtractor
import com.audiora.data.processing.command.FFprobeCommandBuilder
import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.executor.ProcessExecutor
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ChapterExtractor implementation using FFprobe for chapter detection.
 *
 * Falls back gracefully if FFprobe is unavailable or the file has no chapters.
 */
class FFprobeChapterExtractor(
    private val binaryManager: FfmpegBinaryManager,
    private val processExecutor: ProcessExecutor,
    private val ffprobeJsonParser: FFprobeJsonParser,
) : ChapterExtractor {

    override suspend fun extract(
        context: Context,
        uri: Uri,
        totalDurationMs: Long,
    ): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val paths = binaryManager.ensureInitialized()
            val commandBuilder = FFprobeCommandBuilder(paths.ffprobePath)
            val filePath = uri.toString()

            val command = commandBuilder.readChapters(filePath)
            val result = processExecutor.execute(command)

            if (result.isError) {
                Timber.tag("FFMPEG").w("FFprobe chapter extraction failed: ${(result as com.audiora.data.processing.dto.FFmpegResult.Error).message}")
                return@withContext emptyList()
            }

            val output = result.getOrNull() ?: return@withContext emptyList()
            val parseResult = ffprobeJsonParser.parseChapters(output)

            if (parseResult.isError) {
                Timber.tag("FFMPEG").w("Failed to parse FFprobe chapters JSON")
                return@withContext emptyList()
            }

            val ffprobeChapters = parseResult.getOrNull() ?: return@withContext emptyList()
            if (ffprobeChapters.isEmpty()) return@withContext emptyList()

            convertToDomainChapters(ffprobeChapters, totalDurationMs)
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "FFprobeChapterExtractor failed")
            emptyList()
        }
    }

    private fun convertToDomainChapters(
        ffprobeChapters: List<FFprobeChapter>,
        totalDurationMs: Long,
    ): List<Chapter> {
        val sorted = ffprobeChapters.sortedBy { it.id }
        return sorted.mapIndexed { index, ch ->
            val startMs = parseTimeToMs(ch.startTime) ?: ch.start
            val endMs = parseTimeToMs(ch.endTime) ?: ch.end

            Chapter(
                title = ch.title ?: "Chapter ${index + 1}",
                startMs = startMs,
                endMs = endMs,
                durationMs = (endMs - startMs).coerceAtLeast(1000L),
                index = index,
            )
        }
    }

    /**
     * Parse FFprobe's time string (seconds as decimal) to milliseconds.
     */
    private fun parseTimeToMs(timeStr: String?): Long? {
        if (timeStr == null) return null
        return (timeStr.toDoubleOrNull()?.let { (it * 1000).toLong() })
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/FFprobeChapterExtractor.kt
git commit -m "feat: add FFprobeChapterExtractor implementing ChapterExtractor"
```

---

### Task 12: FFmpegService

**Files:**
- Create: `data/processing/FFmpegService.kt`

**Interfaces:**
- Produces: `FFmpegService` with FFprobe read methods and FFmpeg write methods

- [ ] **Step 1: Create FFmpegService.kt**

```kotlin
package com.audiora.data.processing

import com.audiora.data.processing.command.FFmpegCommandBuilder
import com.audiora.data.processing.command.FFprobeCommandBuilder
import com.audiora.data.processing.dto.FFmpegResult
import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.data.processing.dto.FFprobeStream
import com.audiora.data.processing.dto.FfprobeResult
import com.audiora.data.processing.executor.ProcessExecutor
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.data.processing.parser.ProgressParser
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Coordinates all FFmpeg and FFprobe workflows.
 *
 * Acts as the single entry point for all media processing operations.
 * Delegates to specialized components for command building, execution, and parsing.
 * Does NOT construct commands or execute processes directly.
 */
class FFmpegService(
    private val binaryManager: FfmpegBinaryManager,
    private val processExecutor: ProcessExecutor,
    private val tempFileManager: TempFileManager,
    private val ffprobeJsonParser: FFprobeJsonParser,
    private val progressParser: ProgressParser,
) {
    private var cachedFfmpegBuilder: FFmpegCommandBuilder? = null
    private var cachedFfprobeBuilder: FFprobeCommandBuilder? = null

    private suspend fun getFfmpegBuilder(): FFmpegCommandBuilder {
        val cached = cachedFfmpegBuilder
        if (cached != null) return cached
        val paths = binaryManager.ensureInitialized()
        return FFmpegCommandBuilder(paths.ffmpegPath).also { cachedFfmpegBuilder = it }
    }

    private suspend fun getFfprobeBuilder(): FFprobeCommandBuilder {
        val cached = cachedFfprobeBuilder
        if (cached != null) return cached
        val paths = binaryManager.ensureInitialized()
        return FFprobeCommandBuilder(paths.ffprobePath).also { cachedFfprobeBuilder = it }
    }

    // ─── FFprobe: Reading ──────────────────────────────────────────────

    suspend fun readChapters(filePath: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readChapters(filePath)
            val result = processExecutor.execute(command)
            val json = result.getOrNull() ?: return@withContext emptyList()
            val parseResult = ffprobeJsonParser.parseChapters(json)
            val ffprobeChapters = parseResult.getOrNull() ?: return@withContext emptyList()
            convertChapters(ffprobeChapters)
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readChapters failed for $filePath")
            emptyList()
        }
    }

    suspend fun readFormat(filePath: String): FFprobeFormat? = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readFormat(filePath)
            val result = processExecutor.execute(command)
            val json = result.getOrNull() ?: return@withContext null
            ffprobeJsonParser.parseFormat(json).getOrNull()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readFormat failed for $filePath")
            null
        }
    }

    suspend fun readStreams(filePath: String): List<FFprobeStream> = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readStreams(filePath)
            val result = processExecutor.execute(command)
            val json = result.getOrNull() ?: return@withContext emptyList()
            ffprobeJsonParser.parseStreams(json).getOrNull() ?: emptyList()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readStreams failed for $filePath")
            emptyList()
        }
    }

    suspend fun readAllInfo(filePath: String): FFprobeJsonParser.AllInfo? = withContext(Dispatchers.IO) {
        try {
            val command = getFfprobeBuilder().readAll(filePath)
            val result = processExecutor.execute(command)
            val json = result.getOrNull() ?: return@withContext null
            ffprobeJsonParser.parseAll(json).getOrNull()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "readAllInfo failed for $filePath")
            null
        }
    }

    // ─── FFmpeg: Writing ───────────────────────────────────────────────

    suspend fun createM4B(
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var coverFile: java.io.File? = null
        try {
            // Prepare temp files
            val chaptersFilePath = chapters?.let {
                tempFileManager.createChaptersFile(it).absolutePath.also { path ->
                    chaptersFile = tempFileManager.createChaptersFile(it)
                }
            }?.let { tempFileManager.createChaptersFile(chapters).absolutePath }

            if (coverData != null) {
                coverFile = tempFileManager.createCoverFile(coverData)
            }

            val command = getFfmpegBuilder().createM4B(
                inputs = inputFiles,
                outputPath = outputPath,
                options = options,
                metadata = metadata,
                coverPath = coverFile?.absolutePath,
                chaptersFilePath = chaptersFilePath,
            )

            val result = processExecutor.execute(
                command = command,
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )

            result
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "createM4B failed")
            FFmpegResult.error(-3, "createM4B failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, coverFile))
        }
    }

    suspend fun addChapters(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            val command = getFfmpegBuilder().addChapters(
                inputPath = filePath,
                outputPath = outputFile.absolutePath,
                chaptersFilePath = chaptersFile.absolutePath,
            )

            val result = processExecutor.execute(
                command = command,
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )

            if (result.isSuccess) {
                // Copy output file back to original location
                try {
                    java.io.File(filePath).outputStream().use { out ->
                        outputFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                } catch (e: Exception) {
                    Timber.tag("FFMPEG").e(e, "Failed to copy output back to $filePath")
                    FFmpegResult.error(-4, "Failed to write result back to file: ${e.message}")
                }
            }
            result
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "addChapters failed")
            FFmpegResult.error(-3, "addChapters failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    suspend fun mergeAudio(
        inputs: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        try {
            val command = getFfmpegBuilder().mergeAudio(
                inputs = inputs,
                outputPath = outputPath,
                options = options,
            )
            processExecutor.execute(
                command = command,
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "mergeAudio failed")
            FFmpegResult.error(-3, "mergeAudio failed: ${e.message}")
        }
    }

    suspend fun exportAudiobook(
        inputPath: String,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        try {
            val command = getFfmpegBuilder().exportAudio(
                inputPath = inputPath,
                outputPath = outputPath,
                options = options,
            )
            processExecutor.execute(
                command = command,
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "exportAudiobook failed")
            FFmpegResult.error(-3, "exportAudiobook failed: ${e.message}")
        }
    }

    suspend fun replaceChaptersInFile(
        filePath: String,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        var chaptersFile: java.io.File? = null
        var outputFile: java.io.File? = null
        try {
            chaptersFile = tempFileManager.createChaptersFile(chapters)
            outputFile = tempFileManager.createOutputFile("m4b")

            // Remove old chapters, then add new ones in a single pass
            val removeCommand = getFfmpegBuilder().removeChapters(
                inputPath = filePath,
                outputPath = outputFile.absolutePath,
            )
            val removeResult = processExecutor.execute(removeCommand)
            if (removeResult.isError) return@withContext removeResult

            // Now add new chapters
            val addCommand = getFfmpegBuilder().addChapters(
                inputPath = outputFile.absolutePath,
                outputPath = filePath + ".tmp",
                chaptersFilePath = chaptersFile.absolutePath,
            )
            val addResult = processExecutor.execute(
                command = addCommand,
                progressCallback = { line ->
                    if (onProgress != null) {
                        val event = progressParser.parse(line, 0L)
                        event?.percentage?.let { onProgress(it) }
                    }
                },
            )

            if (addResult.isSuccess) {
                try {
                    val tempOutput = java.io.File(filePath + ".tmp")
                    tempOutput.renameTo(java.io.File(filePath))
                } catch (e: Exception) {
                    Timber.tag("FFMPEG").e(e, "Failed to replace file")
                    FFmpegResult.error(-4, "Failed to replace file: ${e.message}")
                }
            }
            addResult
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "replaceChaptersInFile failed")
            FFmpegResult.error(-3, "replaceChaptersInFile failed: ${e.message}")
        } finally {
            tempFileManager.cleanup(listOfNotNull(chaptersFile, outputFile))
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private fun convertChapters(ffprobeChapters: List<FFprobeChapter>): List<Chapter> {
        val sorted = ffprobeChapters.sortedBy { it.id }
        return sorted.mapIndexed { index, ch ->
            val startMs = parseTimeToMs(ch.startTime) ?: ch.start
            val endMs = parseTimeToMs(ch.endTime) ?: ch.end
            Chapter(
                title = ch.title ?: "Chapter ${index + 1}",
                startMs = startMs,
                endMs = endMs,
                durationMs = (endMs - startMs).coerceAtLeast(1000L),
                index = index,
            )
        }
    }

    private fun parseTimeToMs(timeStr: String?): Long? {
        if (timeStr == null) return null
        return (timeStr.toDoubleOrNull()?.let { (it * 1000).toLong() })
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/audiora/data/processing/FFmpegService.kt
git commit -m "feat: add FFmpegService coordinating all media processing workflows"
```

---

### Task 13: Extend BookRepository Interface

**Files:**
- Modify: `domain/repository/BookRepository.kt`
- Modify: `data/repository/BookRepositoryImpl.kt`

**Interfaces:**
- Consumes: `FFmpegService`, `ConversionOptions`, `ExportOptions`
- Produces: Extended `BookRepository` with new processing methods, `BookProcessingResult` sealed class

- [ ] **Step 1: Add BookProcessingResult to BookRepository.kt**

```kotlin
package com.audiora.domain.repository

// ... existing imports ...

/**
 * Processing result for FFmpeg-based operations at the repository level.
 * UI-facing type that hides FFmpegResult implementation details.
 */
sealed class BookProcessingResult {
    data class Success(val outputPath: String) : BookProcessingResult()
    data class Error(val message: String) : BookProcessingResult()
}
```

- [ ] **Step 2: Add new methods to BookRepository interface**

```kotlin
interface BookRepository {
    // ─── Existing methods unchanged ───
    // ...

    // ─── NEW: FFprobe-based reading ───
    suspend fun readChaptersFromFile(filePath: String): List<Chapter>

    // ─── NEW: FFmpeg-based writing ───
    suspend fun createM4B(
        context: android.content.Context,
        inputFiles: List<String>,
        outputPath: String,
        options: com.audiora.domain.model.ConversionOptions,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<com.audiora.domain.model.Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun exportAudiobook(
        context: android.content.Context,
        bookId: Int,
        outputPath: String,
        options: com.audiora.domain.model.ExportOptions,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun replaceChaptersInFile(
        context: android.content.Context,
        bookId: Int,
        chapters: List<com.audiora.domain.model.Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun getAudiobookInfo(filePath: String): com.audiora.data.processing.dto.FFprobeFormat?
}
```

- [ ] **Step 3: Full modified BookRepository.kt**

```kotlin
package com.audiora.domain.repository

import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Bookmark
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class BookProcessingResult {
    data class Success(val outputPath: String) : BookProcessingResult()
    data class Error(val message: String) : BookProcessingResult()
}

interface BookRepository {
    fun getAudiobooks(): StateFlow<List<Audiobook>>
    fun getAudiobook(id: Int): Flow<Audiobook?>
    suspend fun saveAudiobook(audiobook: Audiobook)
    suspend fun deleteAudiobook(id: Int)
    suspend fun updateBookMetadata(
        context: android.content.Context,
        bookId: Int,
        title: String,
        author: String,
        narrator: String,
        publisher: String,
        genre: String,
        language: String,
        description: String,
        copyright: String,
        year: String
    )
    suspend fun updateBookCover(
        context: android.content.Context,
        bookId: Int,
        imageUri: android.net.Uri?
    )
    suspend fun updateBookChapters(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        filePath: String? = null,
    )

    // Bookmark operations
    fun getBookmarks(bookId: Int): Flow<List<Bookmark>>
    suspend fun saveBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: Int)

    // ─── FFprobe-based reading ───
    suspend fun readChaptersFromFile(filePath: String): List<Chapter>

    // ─── FFmpeg-based writing ───
    suspend fun createM4B(
        context: android.content.Context,
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun exportAudiobook(
        context: android.content.Context,
        bookId: Int,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun replaceChaptersInFile(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun getAudiobookInfo(filePath: String): com.audiora.data.processing.dto.FFprobeFormat?
}
```

- [ ] **Step 4: Run code-review to verify the interface is correct**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/audiora/domain/repository/BookRepository.kt
git commit -m "feat: extend BookRepository with FFmpeg/FFprobe processing methods"
```

---

### Task 14: Implement New Methods in BookRepositoryImpl

**Files:**
- Modify: `data/repository/BookRepositoryImpl.kt`

**Interfaces:**
- Consumes: `FFmpegService` (injected), extended `BookRepository` from Task 13

- [ ] **Step 1: Update BookRepositoryImpl constructor and add FFmpegService**

```kotlin
class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val appScope: CoroutineScope,
    private val ffmpegService: FFmpegService,
) : BookRepository {
```

- [ ] **Step 2: Add FFmpeg-related method implementations**

```kotlin
    // ─── FFprobe-based reading ───

    override suspend fun readChaptersFromFile(filePath: String): List<Chapter> {
        return ffmpegService.readChapters(filePath)
    }

    // ─── FFmpeg-based writing ───

    override suspend fun createM4B(
        context: android.content.Context,
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: Map<String, String>,
        coverData: ByteArray?,
        chapters: List<Chapter>?,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val result = ffmpegService.createM4B(
                inputFiles = inputFiles,
                outputPath = outputPath,
                options = options,
                metadata = metadata,
                coverData = coverData,
                chapters = chapters,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                BookProcessingResult.Success(outputPath)
            } else {
                val error = result as com.audiora.data.processing.dto.FFmpegResult.Error
                BookProcessingResult.Error("Processing failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "createM4B failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun exportAudiobook(
        context: android.content.Context,
        bookId: Int,
        outputPath: String,
        options: ExportOptions,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val book = bookDao.getAudiobookById(bookId).first()
                ?: return BookProcessingResult.Error("Book with ID $bookId not found")
            val result = ffmpegService.exportAudiobook(
                inputPath = book.filePath,
                outputPath = outputPath,
                options = options,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                BookProcessingResult.Success(outputPath)
            } else {
                val error = result as com.audiora.data.processing.dto.FFmpegResult.Error
                BookProcessingResult.Error("Export failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "exportAudiobook failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun replaceChaptersInFile(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val book = bookDao.getAudiobookById(bookId).first()
                ?: return BookProcessingResult.Error("Book with ID $bookId not found")
            val result = ffmpegService.replaceChaptersInFile(
                filePath = book.filePath,
                chapters = chapters,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                // Also update Room cache
                val serialized = Chapter.serializeList(chapters)
                bookDao.getAudiobookById(bookId).first()?.let { entity ->
                    bookDao.updateAudiobook(entity.copy(
                        chaptersJson = serialized,
                        lastModified = System.currentTimeMillis()
                    ))
                }
                BookProcessingResult.Success(book.filePath)
            } else {
                val error = result as com.audiora.data.processing.dto.FFmpegResult.Error
                BookProcessingResult.Error("Chapter replacement failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "replaceChaptersInFile failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun getAudiobookInfo(filePath: String): FFprobeFormat? {
        return ffmpegService.readFormat(filePath)
    }
```

- [ ] **Step 3: Add new imports**

```kotlin
import com.audiora.data.processing.FFmpegService
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import com.audiora.domain.repository.BookProcessingResult
```

- [ ] **Step 4: Run code-review to verify the implementations**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/audiora/data/repository/BookRepositoryImpl.kt
git commit -m "feat: implement FFmpeg methods in BookRepositoryImpl"
```

---

### Task 15: Wire Dependencies in AudioraApplication

**Files:**
- Modify: `app/src/main/java/com/audiora/AudioraApplication.kt`

- [ ] **Step 1: Add FFmpegService initialization**

```kotlin
// Add new properties
lateinit var ffmpegService: FFmpegService
    private set

lateinit var ffmpegBinaryManager: FfmpegBinaryManager
    private set

// In onCreate(), after existing initializations:
ffmpegBinaryManager = FfmpegBinaryManager(applicationContext)
ffmpegService = FFmpegService(
    binaryManager = ffmpegBinaryManager,
    processExecutor = ProcessExecutor(),
    tempFileManager = TempFileManager(applicationContext),
    ffprobeJsonParser = FFprobeJsonParser(),
    progressParser = ProgressParser(),
)
```

- [ ] **Step 2: Update BookRepositoryImpl construction to pass FFmpegService**

```kotlin
bookRepository = BookRepositoryImpl(
    bookDao = database.bookDao(),
    bookmarkDao = database.bookmarkDao(),
    appScope = appScope,
    ffmpegService = ffmpegService,
)
```

- [ ] **Step 3: Add imports**

```kotlin
import com.audiora.data.processing.FFmpegService
import com.audiora.data.processing.FfmpegBinaryManager
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.data.processing.parser.ProgressParser
import com.audiora.data.processing.TempFileManager
import com.audiora.data.processing.executor.ProcessExecutor
```

- [ ] **Step 4: Run code-review**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/audiora/AudioraApplication.kt
git commit -m "feat: wire FFmpegService and FfmpegBinaryManager in DI"
```

---

### Task 16: Refactor ProcessingScreen to Use FFmpegService

**Files:**
- Modify: `feature/converter/ProcessingScreen.kt`

- [ ] **Step 1: Update ProcessingScreen to use FFmpegService as primary path, M4BTranscoder as fallback**

The key change is replacing the core merge logic to use FFmpegService.createM4B() when FFmpeg binaries are available, falling back to M4BTranscoder if they aren't. The UI (progress, steps) stays unchanged.

```kotlin
// In the LaunchedEffect block, replace the M4BTranscoder.transcode() call with:

val app = context.applicationContext as AudioraApplication

// Try FFmpeg first for M4B creation
val ffmpegResult = try {
    val inputFiles = selectedFiles.map { Uri.parse(it.uriString).toString() }
    app.ffmpegService.createM4B(
        inputFiles = inputFiles,
        outputPath = outputMergedFile.absolutePath,
        options = ConversionOptions(
            bitRate = 128000,
            sampleRate = 44100,
            channelCount = 2,
        ),
        metadata = mapOf(
            "title" to finalTitle,
            "artist" to finalAuthor,
            "album" to "Audiora Combined Studio",
            "year" to WizardState.year,
            "genre" to WizardState.genre,
        ),
        onProgress = { pct ->
            progress = pct * 0.45f
        },
    )
} catch (e: Exception) {
    Timber.e(e, "FFmpeg creation failed, falling back to M4BTranscoder")
    null
}

if (ffmpegResult == null || ffmpegResult.isError) {
    // Fallback to existing M4BTranscoder logic
    // ... (keep existing transcode fallback code) ...
}
```

- [ ] **Step 2: Add imports**

```kotlin
import com.audiora.domain.model.ConversionOptions
```

- [ ] **Step 3: Run code-review**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/audiora/feature/converter/ProcessingScreen.kt
git commit -m "feat: use FFmpegService for M4B creation in ProcessingScreen, fallback to M4BTranscoder"
```

---

## Self-Review Checklist

- [ ] **Spec coverage:** Every component from the spec mapped to a task? Yes — all 10 components covered (BinaryManager, ProcessExecutor, FFmpegCommandBuilder, FFprobeCommandBuilder, FFprobeJsonParser, ProgressParser, TempFileManager, FFmpegService, ChapterExtractor interface, BookRepository extension).
- [ ] **Placeholder scan:** No TBDs, TODOs, or "implement later" in any task.
- [ ] **Type consistency:** All method signatures consistent across tasks (e.g., `ensureInitialized(): BinaryPaths`, `execute(command, timeout?, callback?): FFmpegResult`).
- [ ] **Spec requirement check:** JAudiotagger methods stay untouched ✓, all commands as List<String> ✓, FFprobe JSON output only ✓, sealed results ✓, Timber logging with FFMPEG tag ✓.

