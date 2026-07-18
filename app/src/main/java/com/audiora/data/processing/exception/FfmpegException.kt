package com.audiora.data.processing.exception

/**
 * Base sealed class for all FFmpeg-related exceptions.
 * Provides a structured exception hierarchy that never exposes raw process failures to UI code.
 */
sealed class FfmpegException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when the device CPU architecture is not supported by the bundled binaries.
 */
class UnsupportedAbiException(message: String) : FfmpegException(message)

/**
 * Thrown when binary extraction from assets fails (copy error, permissions, etc.).
 */
class BinaryInitException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)

/**
 * Thrown when a binary fails version verification or produces unexpected output.
 */
class BinaryVerificationException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)

/**
 * Thrown when a required binary file is not found in assets.
 */
class BinaryNotFoundException(message: String) : FfmpegException(message)

/**
 * Thrown when a binary exists but appears corrupted (size mismatch, etc.).
 */
class BinaryCorruptedException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)

/**
 * Thrown when a command execution fails at the process level.
 */
class CommandExecutionException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)

/**
 * Thrown when FFprobe JSON output cannot be parsed.
 */
class FfprobeParseException(message: String, cause: Throwable? = null) : FfmpegException(message, cause)

/**
 * Thrown when an FFmpeg operation exceeds the configured timeout.
 */
class FfmpegTimeoutException(message: String) : FfmpegException(message)

/**
 * Thrown when an FFmpeg operation is cancelled by the user or system.
 */
class FfmpegCancellationException(message: String) : FfmpegException(message)
