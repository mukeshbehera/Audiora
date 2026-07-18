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
