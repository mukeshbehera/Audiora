package com.audiora.data.local

import com.audiora.domain.model.Chapter
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes Nero chapter list (chpl) atoms directly into an existing M4B (MP4) file
 * at the binary level, without relying on FFmpeg or any external tool.
 *
 * The chpl atom lives at moov > udta > chpl. This writer:
 * 1. Parses the top-level MP4 box structure to find "moov"
 * 2. Parses inside moov to find/create "udta"
 * 3. Replaces or inserts the "chpl" atom inside udta
 * 4. Updates parent atom sizes
 *
 * Ported in spirit from the open-source Voice audiobook player
 * (PaulWoitaschek/Voice), which embeds chapters the same way.
 */
object M4bChapterWriter {

    private const val ATOM_HEADER_SIZE = 8

    /**
     * Writes chapters as a Nero chpl atom into the source file and produces the
     * patched output file. The source is NOT modified in-place.
     *
     * @return true if chapters were written successfully
     */
    fun writeChapters(
        sourceFile: File,
        outputFile: File,
        chapters: List<Chapter>
    ): Boolean {
        if (chapters.isEmpty()) return false
        if (!sourceFile.exists() || sourceFile.length() < 16L) return false

        return try {
            // Read entire file into memory. MP4 files can be large, but for M4B
            // audiobooks on mobile this is acceptable. A streaming approach could
            // be added later for very large files.
            val fileBytes = sourceFile.readBytes()
            val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.BIG_ENDIAN)

            // Build the chpl atom bytes
            val chplBytes = buildChplAtom(chapters)

            // Find moov atom position
            val moovPos = findAtom(buffer, "moov")
            if (moovPos < 0) {
                Timber.w("M4bChapterWriter: No moov atom found in file")
                return false
            }

            // Parse inside moov to get its size and children
            val moovSize = buffer.getInt((moovPos).toInt())
            val moovPayloadStart = moovPos + ATOM_HEADER_SIZE
            val moovPayloadEnd = moovPos + (moovSize.toLong() and 0xFFFFFFFFL)
            val moovPayloadLen = (moovPayloadEnd - moovPayloadStart).toInt()

            // Read moov payload into a mutable byte array we can edit
            val moovPayload = ByteArray(moovPayloadLen)
            buffer.position(moovPayloadStart.toInt())
            buffer.get(moovPayload)

            // Process the moov payload: replace/insert chpl inside udta
            val patchedMoovPayload = patchMoovPayload(moovPayload, chplBytes)
                ?: return false

            // Build the output file: everything before moov + patched moov + everything after moov
            val moovHeaderSize = if (moovSize.toLong() == 1L) 16 else ATOM_HEADER_SIZE
            val afterMoovStart = moovPos + if (moovSize.toLong() == 1L) {
                val extendedSize = ByteBuffer.wrap(fileBytes, moovPos + ATOM_HEADER_SIZE, 8)
                    .order(ByteOrder.BIG_ENDIAN).getLong()
                ATOM_HEADER_SIZE + 8 + extendedSize.toInt()
            } else {
                moovSize.toInt()
            }

            outputFile.outputStream().use { out ->
                // 1. Write everything before moov
                out.write(fileBytes, 0, moovPos.toInt())

                // 2. Write new moov atom (header + patched payload)
                val newMoovSize = ATOM_HEADER_SIZE + patchedMoovPayload.size
                val moovHeader = ByteBuffer.allocate(ATOM_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
                moovHeader.putInt(newMoovSize)
                moovHeader.put("moov".toByteArray())
                out.write(moovHeader.array())

                // 3. Write patched moov payload
                out.write(patchedMoovPayload)

                // 4. Write everything after moov
                if (afterMoovStart.toInt() < fileBytes.size) {
                    out.write(fileBytes, afterMoovStart.toInt(), fileBytes.size - afterMoovStart.toInt())
                }
            }

            Timber.d("M4bChapterWriter: Successfully wrote ${chapters.size} chapters to ${outputFile.name}")
            true
        } catch (e: Exception) {
            Timber.e(e, "M4bChapterWriter: Failed to write chapters")
            false
        }
    }

    /**
     * Builds the raw bytes of a Nero chpl atom (version 1).
     *
     * Atom structure:
     *   [0..3]  uint32 size (includes this header)
     *   [4..7]  char[4] type = "chpl"
     *   [8]     uint8  version = 1
     *   [9..11] uint24 flags = 0
     *   [12..15] uint32 reserved = 0
     *   [16]    uint8  chapterCount
     *   [17..]  for each chapter:
     *     [0..7]   uint64 timestamp (in 100-nanosecond units)
     *     [8]      uint8  titleLength
     *     [9..]    char[titleLength] title (UTF-8)
     */
    private fun buildChplAtom(chapters: List<Chapter>): ByteArray {
        val count = chapters.size.coerceAtMost(255) // uint8 max

        // Calculate payload sizes
        var chaptersPayloadSize = 0
        for (i in 0 until count) {
            val titleBytes = chapters[i].title.toByteArray(Charsets.UTF_8)
            val titleLen = titleBytes.size.coerceAtMost(255) // uint8 max
            chaptersPayloadSize += 8 + 1 + titleLen // timestamp + titleLength + title
        }

        val fixedSize = 1 + 3 + 4 + 1 // version + flags + reserved + count
        val totalPayloadSize = fixedSize + chaptersPayloadSize
        val totalAtomSize = ATOM_HEADER_SIZE + totalPayloadSize

        val buffer = ByteBuffer.allocate(totalAtomSize).order(ByteOrder.BIG_ENDIAN)

        // Atom header
        buffer.putInt(totalAtomSize)
        buffer.put("chpl".toByteArray())

        // chpl payload
        buffer.put(1.toByte())       // version = 1
        buffer.put(0.toByte())       // flags[0]
        buffer.put(0.toByte())       // flags[1]
        buffer.put(0.toByte())       // flags[2]
        buffer.putInt(0)             // reserved
        buffer.put(count.toByte())   // chapterCount

        for (i in 0 until count) {
            val ch = chapters[i]
            // Convert milliseconds to 100-nanosecond units
            val timestamp100ns = ch.startMs * 10_000L
            val titleBytes = ch.title.toByteArray(Charsets.UTF_8)
            val titleLen = titleBytes.size.coerceAtMost(255)

            buffer.putLong(timestamp100ns)
            buffer.put(titleLen.toByte())
            buffer.put(titleBytes, 0, titleLen)
        }

        return buffer.array()
    }

    /**
     * Scans the file for an atom with the given 4-byte type at the top level.
     * Returns the offset (position of the size field) or -1 if not found.
     */
    private fun findAtom(buffer: ByteBuffer, type: String): Long {
        val typeBytes = type.toByteArray()
        buffer.position(0)

        while (buffer.remaining() >= 8) {
            val pos = buffer.position()

            val size = buffer.getInt().toLong() and 0xFFFFFFFFL
            val atomType = ByteArray(4)
            buffer.get(atomType)

            val atomTypeStr = String(atomType, Charsets.US_ASCII)
            if (atomTypeStr == type) {
                return pos.toLong()
            }

            // Skip past this atom
            val atomEnd = if (size == 1L) {
                // Extended size: 8 more bytes
                if (buffer.remaining() < 8) break
                buffer.getLong()
            } else {
                size
            }

            val skip = (atomEnd - 8).toInt() // subtract the 8 bytes we already read
            if (skip <= 0) break
            if (buffer.remaining() < skip) break
            buffer.position(buffer.position() + skip)
        }

        return -1
    }

    /**
     * Patches the moov payload to replace or insert a chpl atom inside udta.
     * Returns the new moov payload bytes, or null on failure.
     */
    private fun patchMoovPayload(moovPayload: ByteArray, chplBytes: ByteArray): ByteArray? {
        val buffer = ByteBuffer.wrap(moovPayload).order(ByteOrder.BIG_ENDIAN)

        // Find udta atom inside moov
        val udtaPos = findAtom(buffer, "udta")
        if (udtaPos < 0) {
            // No udta atom exists — we'd need to create one, which is complex.
            // For now, return false. Most M4B files have udta.
            Timber.w("M4bChapterWriter: No udta atom found inside moov")
            return null
        }

        val udtaSize = buffer.getInt((udtaPos).toInt())
        val udtaPayloadStart = udtaPos + ATOM_HEADER_SIZE
        val udtaPayloadEnd = udtaPos + (udtaSize.toLong() and 0xFFFFFFFFL)

        // Read udta payload into a mutable byte array
        val udtaPayloadLen = (udtaPayloadEnd - udtaPayloadStart).toInt()
        val udtaPayload = ByteArray(udtaPayloadLen)
        buffer.position(udtaPayloadStart.toInt())
        buffer.get(udtaPayload)

        // Find existing chpl atom inside udta payload
        val chplBuffer = ByteBuffer.wrap(udtaPayload).order(ByteOrder.BIG_ENDIAN)
        val existingChplPos = findAtom(chplBuffer, "chpl")

        // Build new udta payload by replacing or appending chpl
        val newUdtaPayload: ByteArray = if (existingChplPos >= 0) {
            // Replace existing chpl atom
            val beforeChpl = udtaPayload.copyOfRange(0, existingChplPos.toInt())
            val afterChplEnd = existingChplPos + getAtomSize(udtaPayload, existingChplPos.toInt())
            val afterChpl = if (afterChplEnd < udtaPayload.size) {
                udtaPayload.copyOfRange(afterChplEnd, udtaPayload.size)
            } else {
                ByteArray(0)
            }
            beforeChpl + chplBytes + afterChpl
        } else {
            // No existing chpl — append to udta payload
            udtaPayload + chplBytes
        }

        // Build new moov payload: before-udta + new udta atom + after-udta
        val beforeUdta = moovPayload.copyOfRange(0, udtaPos.toInt())
        val afterUdta = moovPayload.copyOfRange(
            (udtaPos + udtaSize.toInt()).coerceAtMost(moovPayload.size),
            moovPayload.size
        )

        // New udta atom: header + payload
        val newUdtaSize = ATOM_HEADER_SIZE + newUdtaPayload.size
        val udtaHeader = ByteBuffer.allocate(ATOM_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        udtaHeader.putInt(newUdtaSize)
        udtaHeader.put("udta".toByteArray())

        return beforeUdta + udtaHeader.array() + newUdtaPayload + afterUdta
    }

    /**
     * Reads the size field of an atom at the given offset.
     */
    private fun getAtomSize(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        val buf = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN)
        return buf.getInt()
    }
}
