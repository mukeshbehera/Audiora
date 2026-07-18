package com.audiora.data.processing.parser

import com.audiora.data.processing.dto.FFprobeChapter
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.data.processing.dto.FFprobeStream
import com.audiora.data.processing.dto.FfprobeResult
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Parses FFprobe JSON output into typed Kotlin DTOs.
 * Uses org.json (already a project dependency).
 * Never throws — returns FfprobeResult for all parsing outcomes.
 */
class FFprobeJsonParser {

    fun parseChapters(json: String): FfprobeResult<List<FFprobeChapter>> = try {
        val root = JSONObject(json)
        val chaptersArray = root.optJSONArray("chapters") ?: JSONArray()
        val chapters = (0 until chaptersArray.length()).map { i ->
            val obj = chaptersArray.getJSONObject(i)
            FFprobeChapter(
                id = obj.optInt("id", 0),
                timeBase = obj.optString("time_base", null),
                start = obj.optLong("start", 0L),
                end = obj.optLong("end", 0L),
                startTime = obj.optString("start_time", null),
                endTime = obj.optString("end_time", null),
                title = obj.optJSONObject("tags")?.optString("title", null),
            )
        }
        FfprobeResult.success(chapters)
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe chapters JSON")
        FfprobeResult.error("Failed to parse chapters: ${e.message}")
    }

    fun parseFormat(json: String): FfprobeResult<FFprobeFormat> = try {
        val root = JSONObject(json)
        val formatObj = root.getJSONObject("format")
        FfprobeResult.success(
            FFprobeFormat(
                filename = formatObj.optString("filename", null),
                nbStreams = formatObj.optInt("nb_streams", -1).let { if (it < 0) null else it },
                nbPrograms = formatObj.optInt("nb_programs", -1).let { if (it < 0) null else it },
                formatName = formatObj.optString("format_name", null),
                formatLongName = formatObj.optString("format_long_name", null),
                startTime = formatObj.optString("start_time", null),
                duration = formatObj.optString("duration", null),
                size = formatObj.optString("size", null),
                bitRate = formatObj.optString("bit_rate", null),
                probeScore = formatObj.optInt("probe_score", -1).let { if (it < 0) null else it },
            )
        )
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe format JSON")
        FfprobeResult.error("Failed to parse format: ${e.message}")
    }

    fun parseStreams(json: String): FfprobeResult<List<FFprobeStream>> = try {
        val root = JSONObject(json)
        val streamsArray = root.optJSONArray("streams") ?: JSONArray()
        val streams = (0 until streamsArray.length()).map { i ->
            val obj = streamsArray.getJSONObject(i)
            FFprobeStream(
                index = obj.optInt("index", 0),
                codecName = obj.optString("codec_name", null),
                codecLongName = obj.optString("codec_long_name", null),
                codecType = obj.optString("codec_type", null),
                codecTagString = obj.optString("codec_tag_string", null),
                codecTag = obj.optString("codec_tag", null),
                sampleRate = obj.optString("sample_rate", null),
                channels = if (obj.has("channels")) obj.getInt("channels") else null,
                channelLayout = obj.optString("channel_layout", null),
                bitRate = obj.optString("bit_rate", null),
                maxBitRate = obj.optString("max_bit_rate", null),
                duration = obj.optString("duration", null),
                durationTs = if (obj.has("duration_ts")) obj.getLong("duration_ts") else null,
            )
        }
        FfprobeResult.success(streams)
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe streams JSON")
        FfprobeResult.error("Failed to parse streams: ${e.message}")
    }

    /**
     * Parses all three sections from a single FFprobe JSON output.
     * Expects JSON with "format", "streams", and optionally "chapters" keys.
     */
    fun parseAll(json: String): FfprobeResult<AllInfo> = try {
        val formatResult = parseFormat(json)
        val streamsResult = parseStreams(json)
        val chaptersResult = parseChapters(json)

        FfprobeResult.success(
            AllInfo(
                format = formatResult.getOrNull(),
                streams = streamsResult.getOrNull() ?: emptyList(),
                chapters = chaptersResult.getOrNull() ?: emptyList(),
            )
        )
    } catch (e: Exception) {
        Timber.tag("FFMPEG").e(e, "Failed to parse FFprobe all JSON")
        FfprobeResult.error("Failed to parse all info: ${e.message}")
    }

    data class AllInfo(
        val format: FFprobeFormat?,
        val streams: List<FFprobeStream>,
        val chapters: List<FFprobeChapter>,
    )
}
