package com.audiora.data.processing.executor

import android.os.Build
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
 * Supports two execution modes:
 * 1. Direct execution — passes command to execve() directly
 * 2. Linker execution — prefixes command with /system/bin/linker[64]
 *    to bypass noexec mount restrictions on Android 10+ devices.
 *    The linker loads the ELF binary via mmap/read instead of execve(),
 *    which avoids noexec restrictions.
 */
class ProcessExecutor {

    data class ExecutionConfig(
        val timeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
        val captureOutput: Boolean = true,
        /** When true, uses /system/bin/linker[64] to execute binary,
         * bypassing noexec mount restrictions on modern Android */
        val useLinker: Boolean = false,
    )

    private fun is64Bit(): Boolean {
        for (abi in Build.SUPPORTED_ABIS) {
            if (abi.contains("64")) return true
        }
        return false
    }

    private fun buildCommand(command: List<String>, useLinker: Boolean): List<String> {
        if (!useLinker || command.isEmpty()) return command
        val linker = if (is64Bit()) "/system/bin/linker64" else "/system/bin/linker"
        return listOf(linker) + command
    }

    suspend fun execute(
        command: List<String>,
        config: ExecutionConfig = ExecutionConfig(),
        progressCallback: ((String) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        val correlationId = UUID.randomUUID().toString().take(8)
        val finalCommand = buildCommand(command, config.useLinker)
        Timber.tag("FFMPEG").d("[%s] Executing: %s", correlationId, finalCommand.joinToString(" "))

        try {
            withTimeout(config.timeoutMs) {
                val process = ProcessBuilder(finalCommand)
                    .redirectErrorStream(false)
                    .start()

                try {
                    val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                        if (config.captureOutput) {
                            process.inputStream.bufferedReader().readText()
                        } else ""
                    }

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
                        process.waitFor(1, TimeUnit.SECONDS)
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
