@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiora.data.local

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.ExtractorInput
import com.audiora.domain.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * Extracts chapter metadata directly from M4B (MP4 container) files by walking
 * the MP4 atom tree and parsing embedded chapter data.
 *
 * Two extraction strategies:
 * 1. Nero-style chapter list (moov > udta > chpl) — covers ~95% of M4B files
 * 2. Chapter tracks (moov > trak > tref > chap) — dedicated text track with samples
 *
 * Ported from the open-source Voice audiobook player (PaulWoitaschek/Voice).
 */
object M4bChapterExtractor {

  /**
   * Extracts chapters from an M4B file at the given content:// or file:// URI.
   */
  suspend fun extractFromUri(
    context: Context,
    uri: Uri,
    totalDurationMs: Long = 0L,
  ): List<Chapter> {
    return extract(context, uri, totalDurationMs)
  }

  /**
   * Extracts chapters from an M4B file at the given file path.
   */
  suspend fun extractFromFile(
    context: Context,
    filePath: String,
    totalDurationMs: Long = 0L,
  ): List<Chapter> {
    return extract(context, Uri.fromFile(java.io.File(filePath)), totalDurationMs)
  }

  // ─── Internal data model ──────────────────────────────────────────────

  private data class ParsedData(
    val chunkOffsets: MutableList<List<Long>> = mutableListOf(),
    val durations: MutableList<List<SttsEntry>> = mutableListOf(),
    val stscEntries: MutableList<List<StscEntry>> = mutableListOf(),
    val timeScales: MutableList<Long> = mutableListOf(),
    var chplChapters: List<MarkData> = emptyList(),
    var chapterTrackId: Int? = null,
  )

  private data class MarkData(
    val startMs: Long,
    val name: String,
  ) : Comparable<MarkData> {
    override fun compareTo(other: MarkData): Int = startMs.compareTo(other.startMs)
  }

  private data class SttsEntry(
    val sampleCount: Long,
    val sampleDuration: Long,
  )

  private data class StscEntry(
    val firstChunk: Long,
    val samplesPerChunk: Int,
  )

  // ─── Core extraction ──────────────────────────────────────────────────

  private suspend fun extract(
    context: Context,
    uri: Uri,
    totalDurationMs: Long,
  ): List<Chapter> = withContext(Dispatchers.IO) {
    val dataSource = DefaultDataSource.Factory(context).createDataSource()
    try {
      dataSource.open(DataSpec(uri))
      val input = DefaultExtractorInput(dataSource, 0, C.LENGTH_UNSET)
      val parsed = walkAtoms(input)
      val marks = buildMarks(parsed, dataSource, uri)
      if (marks.isEmpty()) return@withContext emptyList()
      marksToChapters(marks, totalDurationMs)
    } catch (e: Exception) {
      Timber.w(e, "M4bChapterExtractor: Failed to extract chapters")
      emptyList()
    } finally {
      try { dataSource.close() } catch (_: IOException) {}
    }
  }

  private fun marksToChapters(marks: List<MarkData>, totalDurationMs: Long): List<Chapter> {
    if (marks.isEmpty()) return emptyList()
    val sorted = marks.sorted()
    return sorted.mapIndexed { i, mark ->
      val endMs = if (i < sorted.lastIndex) {
        sorted[i + 1].startMs
      } else {
        if (totalDurationMs > mark.startMs) totalDurationMs else mark.startMs + 30_000L
      }
      Chapter(
        title = mark.name,
        startMs = mark.startMs,
        endMs = endMs,
        durationMs = (endMs - mark.startMs).coerceAtLeast(1000L),
        index = i,
      )
    }
  }

  // ─── Atom walking ────────────────────────────────────────────────────

  private val P_CHPL = listOf("moov", "udta", "chpl")
  private val P_CHAP = listOf("moov", "trak", "tref", "chap")
  private val P_MDHD = listOf("moov", "trak", "mdia", "mdhd")
  private val P_STCO = listOf("moov", "trak", "mdia", "minf", "stbl", "stco")
  private val P_STSC = listOf("moov", "trak", "mdia", "minf", "stbl", "stsc")
  private val P_STTS = listOf("moov", "trak", "mdia", "minf", "stbl", "stts")

  private val ALL_PATHS = setOf(P_CHPL, P_CHAP, P_MDHD, P_STCO, P_STSC, P_STTS)

  companion object {
    private const val ATOM_HEADER_SIZE = 8
    private const val ATOM_LONG_HEADER_SIZE = 16
  }

  private fun walkAtoms(input: ExtractorInput): ParsedData {
    val scratch = ParsableByteArray(ATOM_LONG_HEADER_SIZE)
    val output = ParsedData()
    parseBoxes(input, emptyList(), Long.MAX_VALUE, scratch, output)
    return output
  }

  private fun parseBoxes(
    input: ExtractorInput,
    path: List<String>,
    parentEnd: Long,
    scratch: ParsableByteArray,
    output: ParsedData,
  ) {
    while (input.position < parentEnd) {
      scratch.reset(ATOM_HEADER_SIZE)
      if (!input.readFully(scratch.data, 0, ATOM_HEADER_SIZE, true)) return

      var atomSize = scratch.readUnsignedInt()
      val atomType = scratch.readString(4)
      var headerSize = ATOM_HEADER_SIZE

      if (atomSize == 1L) {
        input.readFully(
          scratch.data,
          ATOM_HEADER_SIZE,
          ATOM_LONG_HEADER_SIZE - ATOM_HEADER_SIZE,
          true,
        )
        scratch.setPosition(ATOM_HEADER_SIZE)
        atomSize = scratch.readUnsignedLongToLong()
        headerSize = ATOM_LONG_HEADER_SIZE
      }

      val payloadSize = (atomSize - headerSize).toInt()
      val payloadEnd = input.position + payloadSize
      val currentPath = path + atomType

      when {
        currentPath == P_CHPL -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitChpl(scratch, output)
          if (output.chplChapters.isNotEmpty()) return
        }
        currentPath == P_CHAP -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitChap(scratch, output)
        }
        currentPath == P_MDHD -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitMdhd(scratch, output)
        }
        currentPath == P_STCO -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitStco(scratch, output)
        }
        currentPath == P_STSC -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitStsc(scratch, output)
        }
        currentPath == P_STTS -> {
          scratch.reset(payloadSize)
          if (!input.readFully(scratch.data, 0, payloadSize, true)) return
          visitStts(scratch, output)
        }
        ALL_PATHS.any { fullPathStartsWith(it, currentPath) } -> {
          parseBoxes(input, currentPath, payloadEnd, scratch, output)
          if (output.chplChapters.isNotEmpty()) return
        }
        else -> {
          if (!input.skipFully(payloadSize, true)) return
        }
      }

      if (input.position < payloadEnd) {
        if (!input.skipFully((payloadEnd - input.position).toInt(), true)) return
      }
    }
  }

  private fun fullPathStartsWith(fullPath: List<String>, prefix: List<String>): Boolean {
    return fullPath.take(prefix.size) == prefix
  }

  // ─── Visitors ────────────────────────────────────────────────────────

  /** moov > udta > chpl — Nero chapter list */
  private fun visitChpl(buffer: ParsableByteArray, output: ParsedData) {
    buffer.setPosition(0)
    val version = buffer.readUnsignedByte()
    buffer.skipBytes(3) // flags

    if (version != 0 && version != 1) {
      Timber.w("M4bChapterExtractor: Unexpected chpl version $version")
      return
    }

    if (version == 1) {
      buffer.skipBytes(4) // reserved
    }

    val chapterCount = buffer.readUnsignedByte()

    val chapters = (0 until chapterCount).map {
      val timestamp = if (version == 0) {
        buffer.readUnsignedInt()
      } else {
        buffer.readUnsignedLongToLong()
      }

      val titleLength = buffer.readUnsignedByte()
      val title = buffer.readString(titleLength)

      // chpl timestamps are in 100-nanosecond units; convert to milliseconds
      MarkData(startMs = timestamp / 10_000, name = title)
    }

    output.chplChapters = chapters
  }

  /** moov > trak > tref > chap — chapter track reference */
  private fun visitChap(buffer: ParsableByteArray, output: ParsedData) {
    val trackId = buffer.readUnsignedIntToInt()
    output.chapterTrackId = trackId
  }

  /** moov > trak > mdia > mdhd — media header with timescale */
  private fun visitMdhd(buffer: ParsableByteArray, output: ParsedData) {
    val version = buffer.readUnsignedByte()
    if (version != 0 && version != 1) {
      Timber.w("M4bChapterExtractor: Unexpected mdhd version $version")
      return
    }

    val flagsSize = 3
    val creationTimeSize = if (version == 0) 4 else 8
    val modificationTimeSize = if (version == 0) 4 else 8
    buffer.skipBytes(flagsSize + creationTimeSize + modificationTimeSize)
    val timescale = buffer.readUnsignedInt()
    output.timeScales += timescale
  }

  /** moov > trak > mdia > minf > stbl > stco — chunk offset table */
  private fun visitStco(buffer: ParsableByteArray, output: ParsedData) {
    val version = buffer.readUnsignedByte()
    if (version != 0) {
      Timber.w("M4bChapterExtractor: Unexpected stco version $version")
      return
    }

    buffer.skipBytes(3) // flags
    val count = buffer.readUnsignedIntToInt()
    val offsets = (0 until count).map { buffer.readUnsignedInt() }
    output.chunkOffsets.add(offsets)
  }

  /** moov > trak > mdia > minf > stbl > stsc — sample-to-chunk table */
  private fun visitStsc(buffer: ParsableByteArray, output: ParsedData) {
    val version = buffer.readUnsignedByte()
    if (version != 0) {
      Timber.w("M4bChapterExtractor: Unexpected stsc version $version")
      return
    }

    buffer.skipBytes(3) // flags
    val count = buffer.readUnsignedIntToInt()
    val entries = (0 until count).map {
      val firstChunk = buffer.readUnsignedInt()
      val samplesPerChunk = buffer.readUnsignedIntToInt()
      buffer.skipBytes(4) // sample description index
      StscEntry(firstChunk = firstChunk, samplesPerChunk = samplesPerChunk)
    }
    output.stscEntries.add(entries)
  }

  /** moov > trak > mdia > minf > stbl > stts — time-to-sample table */
  private fun visitStts(buffer: ParsableByteArray, output: ParsedData) {
    val version = buffer.readUnsignedByte()
    if (version != 0) {
      Timber.w("M4bChapterExtractor: Unexpected stts version $version")
      return
    }

    buffer.skipBytes(3) // flags
    val count = buffer.readUnsignedIntToInt()
    val entries = (0 until count).map {
      SttsEntry(
        sampleCount = buffer.readUnsignedInt(),
        sampleDuration = buffer.readUnsignedInt(),
      )
    }
    output.durations.add(entries)
  }

  // ─── Chapter track processing ────────────────────────────────────────

  private fun buildMarks(
    parsed: ParsedData,
    dataSource: DefaultDataSource,
    uri: Uri,
  ): List<MarkData> {
    if (parsed.chplChapters.isNotEmpty()) return parsed.chplChapters
    val trackId = parsed.chapterTrackId ?: return emptyList()
    return processChapterTrack(uri, dataSource, trackId, parsed)
  }

  /**
   * Reads chapter names from a dedicated chapter track and computes
   * their start times using the stts duration table.
   */
  private fun processChapterTrack(
    uri: Uri,
    dataSource: DefaultDataSource,
    trackId: Int,
    output: ParsedData,
  ): List<MarkData> {
    val chunkOffsets = output.chunkOffsets.getOrNull(trackId - 1) ?: return emptyList()
    val timeScale = output.timeScales.getOrNull(trackId - 1) ?: return emptyList()
    val durations = output.durations.getOrNull(trackId - 1) ?: return emptyList()
    val stscEntries = output.stscEntries.getOrNull(trackId - 1) ?: return emptyList()
    val numberOfChapters = chunkOffsets.size

    val names = try {
      chunkOffsets.map { offset ->
        dataSource.close()
        dataSource.open(DataSpec.Builder().setUri(uri).setPosition(offset).build())
        val buffer = ParsableByteArray()
        buffer.reset(2)
        dataSource.read(buffer.data, 0, 2)
        val textLength = buffer.readShort().toInt()
        buffer.reset(textLength)
        dataSource.read(buffer.data, 0, textLength)
        buffer.readString(textLength)
      }
    } catch (e: Exception) {
      Timber.w(e, "M4bChapterExtractor: Error reading chapter track names")
      return emptyList()
    }

    if (names.size != numberOfChapters) return emptyList()

    var position = 0L
    var entryIndex = 0
    var samplesConsumed = 0L

    return (0 until numberOfChapters).map { chunkIndex ->
      val chapterName = names[chunkIndex]
      val samples = getSamplesPerChunk(chunkIndex, stscEntries)
      val consumed = consumeDuration(samples, durations, entryIndex, samplesConsumed)
      entryIndex = consumed.index
      samplesConsumed = consumed.consumed

      MarkData(
        startMs = position * 1000 / timeScale,
        name = chapterName,
      ).also { position += consumed.duration }
    }.sorted()
  }

  private data class ConsumedDuration(
    val duration: Long,
    val index: Int,
    val consumed: Long,
  )

  private fun consumeDuration(
    sampleCount: Int,
    durations: List<SttsEntry>,
    entryIndex: Int,
    samplesConsumed: Long,
  ): ConsumedDuration {
    var remaining = sampleCount.toLong()
    var idx = entryIndex
    var consumed = samplesConsumed
    var duration = 0L

    while (remaining > 0) {
      val entry = durations.getOrNull(idx)
        ?: return ConsumedDuration(duration, idx, consumed)

      val samplesLeft = entry.sampleCount - consumed
      if (samplesLeft <= 0) {
        idx++
        consumed = 0
      } else {
        val take = minOf(remaining, samplesLeft)
        duration += take * entry.sampleDuration
        remaining -= take
        consumed += take
      }
    }

    return ConsumedDuration(duration, idx, consumed)
  }

  private fun getSamplesPerChunk(
    chunkIndex: Int,
    stscEntries: List<StscEntry>,
  ): Int {
    for (i in stscEntries.indices) {
      val entry = stscEntries[i]
      val next = stscEntries.getOrNull(i + 1)
      if (chunkIndex + 1 >= entry.firstChunk) {
        if (next == null || chunkIndex + 1 < next.firstChunk) {
          return entry.samplesPerChunk
        }
      }
    }
    return 1
  }
}
