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
