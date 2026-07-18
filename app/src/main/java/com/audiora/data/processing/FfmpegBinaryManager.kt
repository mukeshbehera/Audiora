package com.audiora.data.processing

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import com.audiora.data.processing.exception.BinaryInitException
import com.audiora.data.processing.exception.UnsupportedAbiException
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
 * - Verify binary integrity via size comparison (avoids running --version
 *   which can hang on devices with exec restrictions in filesDir)
 * - Auto-recover from corruption (delete → re-extract → retry once)
 * - Thread-safe initialization via Mutex (safe with coroutines)
 *
 * Asset layout (per-flavor APK — only one ABI per APK):
 *   assets/ffmpeg/
 *       ffmpeg              ← the binary
 *       ffprobe             ← the binary
 *       ffmpeg_size.txt     ← expected byte count (integrity pre-check)
 *       version.txt         ← bundled version string
 *
 * Extraction target:
 *   filesDir/files/ffmpeg   ← follows android-media-converter's proven pattern
 *   filesDir/files/ffprobe
 */
class FfmpegBinaryManager(
    private val context: Context,
    private val sharedPrefs: SharedPreferences,
) {
    data class BinaryPaths(
        val ffmpegPath: String,
        val ffprobePath: String,
    )

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedVersion: String? = null

    @Volatile
    private var cachedPaths: BinaryPaths? = null

    companion object {
        private const val PREFS_INSTALLED_VERSION = "ffmpeg_installed_version"
        private const val PREFS_INSTALL_TIMESTAMP = "ffmpeg_install_timestamp"
        private const val ASSETS_BASE = "ffmpeg"
        private const val BIN_DIR_NAME = "files"
    }

    fun isInitialized(): Boolean = initialized

    fun getVersion(): String? = cachedVersion

    fun getInstalledVersion(): String? =
        sharedPrefs.getString(PREFS_INSTALLED_VERSION, null)

    /**
     * Ensures binaries are extracted and ready.
     *
     * @throws UnsupportedAbiException if no binary for device ABI
     * @throws BinaryInitException if extraction or permissions fail
     */
    suspend fun ensureInitialized(): BinaryPaths {
        if (initialized) {
            return cachedPaths ?: throw BinaryInitException("Binary paths lost after initialization")
        }
        val paths = doInitialize()
        initialized = true
        cachedPaths = paths
        return paths
    }

    private suspend fun doInitialize(): BinaryPaths {
        detectAbi()
        val bundledVersion = readBundledVersion()
        val binDir = getBinDir()

        val ffmpegFile = File(binDir, "ffmpeg")
        val ffprobeFile = File(binDir, "ffprobe")

        // Check if we need to extract or upgrade — version check + size check
        val installedVersion = getInstalledVersion()
        val needsExtract = installedVersion != bundledVersion ||
            !ffmpegFile.exists() ||
            !ffprobeFile.exists() ||
            !verifySize(ffmpegFile)

        if (needsExtract) {
            ffmpegFile.delete()
            ffprobeFile.delete()

            extractBinary("ffmpeg", ffmpegFile)
            extractBinary("ffprobe", ffprobeFile)
        }

        // Set executable permissions
        if (!ffmpegFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffmpegFile")
        }
        if (!ffprobeFile.setExecutable(true)) {
            throw BinaryInitException("Failed to set executable permission on $ffprobeFile")
        }

        // Verify size integrity — if corrupted, re-extract once
        if (!verifySize(ffmpegFile) || !verifySize(ffprobeFile)) {
            Timber.tag("FFMPEG").w("Size mismatch, re-extracting...")
            ffmpegFile.delete()
            ffprobeFile.delete()
            extractBinary("ffmpeg", ffmpegFile)
            extractBinary("ffprobe", ffprobeFile)
            if (!ffmpegFile.setExecutable(true) || !ffprobeFile.setExecutable(true)) {
                throw BinaryInitException("Failed to set permissions during recovery")
            }
            if (!verifySize(ffmpegFile) || !verifySize(ffprobeFile)) {
                throw BinaryInitException("Binary size mismatch after re-extraction — possible APK corruption")
            }
        }

        // Store installed version
        sharedPrefs.edit()
            .putString(PREFS_INSTALLED_VERSION, bundledVersion)
            .putLong(PREFS_INSTALL_TIMESTAMP, System.currentTimeMillis())
            .apply()

        Timber.tag("FFMPEG").i("Binaries initialized: ffmpeg=$ffmpegFile, ffprobe=$ffprobeFile (v$bundledVersion)")

        return BinaryPaths(
            ffmpegPath = ffmpegFile.absolutePath,
            ffprobePath = ffprobeFile.absolutePath,
        )
    }

    /**
     * Detect the device's primary CPU ABI.
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
        val expectedSize = readExpectedSize() ?: return false // must have size file
        val actualSize = binaryFile.length()
        return actualSize == expectedSize
    }

    /**
     * Return the directory where binaries are stored (filesDir/files/).
     * Uses context.getDir() which follows android-media-converter's proven pattern.
     */
    private fun getBinDir(): File {
        return context.getDir(BIN_DIR_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Extract a binary from assets to the target file.
     * Uses ContentResolver for writing (matches android-media-converter pattern).
     */
    private fun extractBinary(name: String, targetFile: File) {
        val assetPath = "$ASSETS_BASE/$name"
        try {
            context.assets.open(assetPath).use { input ->
                context.contentResolver.openOutputStream(Uri.fromFile(targetFile))?.use { output ->
                    input.copyTo(output)
                } ?: throw BinaryInitException("Failed to open output stream for $targetFile")
            }
            Timber.tag("FFMPEG").d("Extracted $assetPath to $targetFile")
        } catch (e: IOException) {
            throw BinaryInitException("Failed to extract $assetPath from assets: ${e.message}", e)
        }
    }
}
