package com.audiora.data.processing

/**
 * JNI bridge to execute FFmpeg via memfd_create + fexecve.
 *
 * The native C code (ffmpeg_jni.c) reads the FFmpeg binary into an anonymous
 * memory file and executes it from there, bypassing Android's noexec mount
 * restrictions on /data/ partitions.
 *
 * Usage:
 *   val exitCode = FFmpegNative.execute(binaryPath, arrayOf("-y", "-i", "input.mp3", ...))
 */
object FFmpegNative {
    private var loaded = false

    /**
     * Ensure the native library is loaded.
     */
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("ffmpeg_jni")
            loaded = true
        }
    }

    /**
     * Execute FFmpeg with the given arguments.
     *
     * @param binaryPath Absolute path to the FFmpeg binary in app storage
     * @param args FFmpeg command-line arguments (without the program name)
     * @return Exit code (0 = success)
     */
    fun execute(binaryPath: String, args: Array<String>): Int {
        ensureLoaded()
        return executeFFmpeg(binaryPath, args)
    }

    /**
     * Execute FFprobe with the given arguments.
     */
    fun executeFFprobe(binaryPath: String, args: Array<String>): Int {
        ensureLoaded()
        return executeFFmpeg(binaryPath, args)
    }

    // Native method declarations
    private external fun executeFFmpeg(binaryPath: String, args: Array<String>): Int
}
