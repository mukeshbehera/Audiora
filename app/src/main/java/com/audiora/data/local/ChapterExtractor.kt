package com.audiora.data.local

import android.content.Context
import android.net.Uri
import com.audiora.domain.model.Chapter

/**
 * Strategy interface for chapter extraction from audiobook files.
 * Implementations can use different backends (MP4 atom parser, FFprobe, etc.)
 */
interface ChapterExtractor {
    /**
     * Extract chapters from an audiobook file.
     *
     * @param context Android context
     * @param uri URI of the audiobook file (content:// or file://)
     * @param totalDurationMs Total duration in ms (for end-time calculation, 0 = unknown)
     * @return List of chapters (empty if none found or extractor cannot handle the file)
     */
    suspend fun extract(
        context: Context,
        uri: Uri,
        totalDurationMs: Long = 0L,
    ): List<Chapter>
}
