package com.audiora.data.processing.executor

import com.audiora.data.processing.FFmpegNative
import com.audiora.data.processing.dto.FFmpegResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Executes FFmpeg/FFprobe via JNI + memfd_create + fexecve.
 *
 * Reads the FFmpeg binary into an anonymous memory file (memfd_create) and
 * executes from there via fexecve. This bypasses Android's noexec mount
 * restrictions because the binary is loaded from memory, not the filesystem.
 *
 * Falls back to direct ProcessBuilder if JNI native library is not available.
 */
class ProcessExecutor {

    data class ExecutionConfig(
        val timeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
        val captureOutput: Boolean = true,
    )

    suspend fun execute(
        command: List<String>,
        config: ExecutionConfig = ExecutionConfig(),
        progressCallback: ((String) -> Unit)? = null,
    ): FFmpegResult = withContext(Dispatchers.IO) {
        val correlationId = UUID.randomUUID().toString().take(8)

        if (command.size < 2) {
            return@withContext FFmpegResult.error(-1, "Command too short: ${command.joinToString(" ")}")
        }

        val binaryPath = command[0]
        val args = command.drop(1).toTypedArray()

        Timber.tag("FFMPEG").d("[%s] Executing via JNI: %s", correlationId, command.joinToString(" "))

        try {
            withTimeout(config.timeoutMs) {
                // Try JNI execution first (memfd_create + fexecve bypasses noexec)
                var exitCode: Int
                try {
                    exitCode = FFmpegNative.execute(binaryPath, args)
                } catch (e: UnsatisfiedLinkError) {
                    Timber.tag("FFMPEG").w("[%s] JNI not available, using ProcessBuilder", correlationId)
                    exitCode = executeViaProcessBuilder(command)
                } catch (e: NoClassDefFoundError) {
                    exitCode = executeViaProcessBuilder(command)
                }

                // If JNI returned >= 127, it means our JNI wrapper's _exit(127+errno)
                // was triggered (binary execution failed). Fall back to ProcessBuilder.
                if (exitCode >= 127) {
                    val errno = exitCode - 127
                    Timber.tag("FFMPEG").w("[%s] JNI failed (errno=%d), trying ProcessBuilder", correlationId, errno)
                    exitCode = executeViaProcessBuilder(command)
                }

                if (!isActive) {
                    Timber.tag("FFMPEG").d("[%s] Cancelled", correlationId)
                    return@withTimeout FFmpegResult.error(-1, "Execution cancelled")
                }

                if (exitCode == 0) {
                    Timber.tag("FFMPEG").d("[%s] Success (exit=%d)", correlationId, exitCode)
                    FFmpegResult.success(exitCode)
                } else {
                    Timber.tag("FFMPEG").w("[%s] Failed (exit=%d)", correlationId, exitCode)
                    FFmpegResult.error(exitCode, "FFmpeg exited with code $exitCode")
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

    /**
     * Fallback: execute via ProcessBuilder if JNI is unavailable.
     */
    private fun executeViaProcessBuilder(command: List<String>): Int {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
        } catch (e: Exception) {
            Timber.tag("FFMPEG").e(e, "ProcessBuilder fallback failed")
            -1
        }
    }
}
