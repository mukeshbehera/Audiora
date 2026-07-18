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
 * - Start processes via direct execution or sh -c wrapper
 * - Capture stdout and stderr
 * - Support cancellation via coroutine cancellation
 * - Support timeout
 * - Support progress callbacks (for FFmpeg stderr lines)
 * - Return structured FFmpegResult
 *
 * On Android, executing binaries directly from filesDir can fail due to
 * noexec mount flags. The useShell option wraps commands in sh -c to
 * match the approach used by android-media-converter and other apps.
 */
class ProcessExecutor {

    data class ExecutionConfig(
        val timeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
        val captureOutput: Boolean = true,
        /** When true, wraps command in sh -c for Android compatibility */
        val useShell: Boolean = false,
    )

    /**
     * Execute a command and capture its output.
     *
     * @param command List of command and arguments (never shell-escaped when useShell=false)
     * @param config Execution configuration (timeout, output capture, shell wrapper)
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
                // Build process — wrap in sh -c when useShell=true (Android compatibility)
                val process = if (config.useShell) {
                    val fullCommand = command.joinToString(" ") { arg ->
                        if (arg.contains(" ") || arg.contains("'") || arg.contains('"')) {
                            "'${arg.replace("'", "'\\''")}'"
                        } else arg
                    }
                    Timber.tag("FFMPEG").d("[%s] Shell command: %s", correlationId, fullCommand)
                    ProcessBuilder("sh", "-c", fullCommand)
                        .redirectErrorStream(false)
                        .start()
                } else {
                    ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()
                }

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
