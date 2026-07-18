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
