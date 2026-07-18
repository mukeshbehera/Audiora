package com.audiora.data.processing

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.audiora.data.processing.executor.ProcessExecutor
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Manages FFmpeg and FFprobe native binary lifecycle.
 *
 * Responsibilities:
 * - Detect device CPU ABI (validates against supported list)
 * - Read bundled version from assets/ffmpeg/version.txt
 * - Compare with installed version in SharedPreferences
 * - Extract binaries from per-flavor assets to app private storage
 * - Set executable permissions
 * - Verify binary integrity (size check + --version)
 * - Auto-recover from corruption (delete → re-extract → retry once)
 * - Thread-safe initialization (double-checked locking)
 *
 * Asset layout (per-flavor APK — only one ABI per APK):
 *   assets/ffmpeg/
 *       ffmpeg              ← the binary
 *       ffprobe             ← the binary
 *       ffmpeg_size.txt     ← expected byte count (integrity pre-check)
 *       version.txt         ← bundled version string
 */
class FfmpegBinaryManager(
    private val context: Context,
    private val processExecutor: ProcessExecutor,
    private val sharedPrefs: SharedPreferences,
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

    companion object {
        private const val PREFS_INSTALLED_VERSION = "ffmpeg_installed_version"
        private const val PREF_INSTALL_TIMESTAMP = "ffmpeg_install_timestamp"
        private const val ASSETS_BASE = "ffmpeg"
    }

    fun isInitialized(): Boolean = initialized

    fun getVersion(): String? = cachedVersion

    fun getInstalledVersion(): String? =
        sharedPrefs.getString(PREFS_INSTALLED_VERSION, null)

    /**
     * Ensures binaries are extracted and ready.
     * Thread-safe — only runs initialization once.
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

    /**
     * Force re-initialization — deletes existing binaries and re-extracts.
     */
    suspend fun reinitialize(): BinaryPaths {
        synchronized(lock) {
            initialized = false
            cachedPaths = null
            cachedVersion = null
            val binDir = getBinDir()
            File(binDir, "ffmpeg").delete()
            File(binDir, "ffprobe").delete()
            sharedPrefs.edit().remove(PREFS_INSTALLED_VERSION).apply()
        }
        return ensureInitialized()
    }

    private suspend fun initialize(): BinaryPaths {
        val abi = detectAbi()
        val bundledVersion = readBundledVersion()
        val binDir = getBinDir()

        val ffmpegFile = File(binDir, "ffmpeg")
        val ffprobeFile = File(binDir, "ffprobe")

        // Check if we need to extract or upgrade
        val installedVersion = getInstalledVersion()
        val needsExtract = installedVersion != bundledVersion ||
            !ffmpegFile.exists() ||
            !ffprobeFile.exists() ||
            !verifySize(ffmpegFile)

        if (needsExtract) {
            // Clear old binaries
            ffmpegFile.delete()
            ffprobeFile.delete()

            extractBinary(abi, "ffmpeg", ffmpegFile)
            extractBinary(abi, "ffprobe", ffprobeFile)
        }

        // Set executable permissions
        if (!ffmpegFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffmpegFile")
        }
        if (!ffprobeFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffprobeFile")
        }

        // Verify integrity — recover if corrupted
        try {
            verifyBinary(ffmpegFile.absolutePath, "ffmpeg")
            verifyBinary(ffprobeFile.absolutePath, "ffprobe")
        } catch (e: BinaryVerificationException) {
            Timber.tag("FFMPEG").w("Binary verification failed, attempting recovery: ${e.message}")
            // Re-extract and retry once
            ffmpegFile.delete()
            ffprobeFile.delete()
            extractBinary(abi, "ffmpeg", ffmpegFile)
            extractBinary(abi, "ffprobe", ffprobeFile)
            if (!ffmpegFile.setExecutable(true) || !ffprobeFile.setExecutable(true)) {
                throw BinaryInitException("Failed to set permissions during recovery")
            }
            verifyBinary(ffmpegFile.absolutePath, "ffmpeg")
            verifyBinary(ffprobeFile.absolutePath, "ffprobe")
        }

        // Persist version
        sharedPrefs.edit()
            .putString(PREFS_INSTALLED_VERSION, bundledVersion)
            .putLong(PREF_INSTALL_TIMESTAMP, System.currentTimeMillis())
            .apply()

        Timber.tag("FFMPEG").i("Binaries initialized: ffmpeg=$ffmpegFile, ffprobe=$ffprobeFile (v$bundledVersion)")

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
     * Read the bundled version from assets/ffmpeg/version.txt.
     */
    private fun readBundledVersion(): String {
        return try {
            context.assets.open("$ASSETS_BASE/version.txt")
                .bufferedReader()
                .readText()
                .trim()
        } catch (e: IOException) {
            throw BinaryInitException("Failed to read version.txt from assets: ${e.message}", e)
        }
    }

    /**
     * Read the expected binary size from assets/ffmpeg/ffmpeg_size.txt.
     */
    private fun readExpectedSize(): Long? {
        return try {
            context.assets.open("$ASSETS_BASE/ffmpeg_size.txt")
                .bufferedReader()
                .readText()
                .trim()
                .toLongOrNull()
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Verify file size matches expected size from ffmpeg_size.txt.
     */
    private fun verifySize(binaryFile: File): Boolean {
        if (!binaryFile.exists()) return false
        val expectedSize = readExpectedSize() ?: return true // skip if no size file
        val actualSize = binaryFile.length()
        return actualSize == expectedSize
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
     * Asset path (per-flavor APK): assets/ffmpeg/{name}
     * e.g. assets/ffmpeg/ffmpeg, assets/ffmpeg/ffprobe
     */
    private fun extractBinary(abi: String, name: String, targetFile: File) {
        val assetPath = "$ASSETS_BASE/$name"
        try {
            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Timber.tag("FFMPEG").d("Extracted $assetPath to $targetFile")
        } catch (e: IOException) {
            throw BinaryInitException("Failed to extract $assetPath from assets: ${e.message}", e)
        }
    }

    /**
     * Verify a binary works by running --version and checking the exit code.
     * Uses ProcessExecutor — never creates ProcessBuilder directly.
     */
    private suspend fun verifyBinary(binaryPath: String, name: String) {
        val result = processExecutor.execute(
            command = listOf(binaryPath, "-version"),
        )
        if (result.isError) {
            val errMsg = (result as? com.audiora.data.processing.dto.FFmpegResult.Error)?.message ?: "unknown error"
            throw BinaryVerificationException("$name --version failed: $errMsg")
        }
        val output = result.getOrNull()
        if (output.isNullOrBlank()) {
            throw BinaryVerificationException("$name produced no output")
        }
        val firstLine = output.lines().firstOrNull() ?: ""
        if (name !in firstLine.lowercase()) {
            throw BinaryVerificationException("$name binary does not appear to be valid: $firstLine")
        }
        if (cachedVersion == null) {
            cachedVersion = firstLine.trim()
        }
        Timber.tag("FFMPEG").d("$name verified: ${firstLine.trim()}")
    }
}
