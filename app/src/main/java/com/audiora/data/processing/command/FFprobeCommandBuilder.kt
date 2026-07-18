package com.audiora.data.processing.command

/**
 * Builds FFprobe commands as List<String>.
 * All commands request JSON output for programmatic parsing.
 * Never constructs commands via string concatenation.
 */
class FFprobeCommandBuilder(
    private val ffprobePath: String,
) {
    /**
     * Read format, streams, and chapters in a single FFprobe invocation.
     */
    fun readAll(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_format")
        add("-show_streams")
        add("-show_chapters")
        add(filePath)
    }

    /**
     * Read container format information only.
     */
    fun readFormat(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_format")
        add(filePath)
    }

    /**
     * Read stream information (codec, sample rate, channels, etc.) only.
     */
    fun readStreams(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_streams")
        add(filePath)
    }

    /**
     * Read chapter markers and titles only.
     */
    fun readChapters(filePath: String): List<String> = buildList {
        add(ffprobePath)
        add("-v")
        add("quiet")
        add("-print_format")
        add("json")
        add("-show_chapters")
        add(filePath)
    }
}
