package com.audiora.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MediaMetadata
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter

/**
 * Builds per-chapter MediaItems with ClippingConfiguration.
 *
 * Mirrors Voice's MediaItemProvider.playbackItems() + MediaItemBuilder patterns.
 *
 * Each chapter gets its own MediaItem with:
 *   - Source URI pointing to the full audiobook file
 *   - ClippingConfiguration restricting playback to the chapter's time range
 *   - Rich metadata (title, author, cover art)
 *
 * When a book has no chapters (single-file), a single "Full Audiobook" MediaItem
 * is returned without clipping, matching the current behavior.
 */
object MediaItemsBuilder {

    /**
     * Parses chapters from the Audiobook's chaptersJson field.
     * Returns the stored chapters list, or a single fallback entry.
     */
    fun getChapters(book: Audiobook): List<Chapter> {
        if (!book.chaptersJson.isNullOrEmpty()) {
            val decoded = Chapter.deserializeList(book.chaptersJson)
            if (decoded.isNotEmpty()) return decoded
        }
        val fallbackDuration = if (book.durationMs > 0) book.durationMs else 3600000L
        return listOf(
            Chapter(
                title = book.title.ifEmpty { "Full Audiobook" },
                startMs = 0L,
                endMs = fallbackDuration,
                durationMs = fallbackDuration,
                index = 0
            )
        )
    }

    /**
     * Builds per-chapter PlaybackItems from the book's chapter data.
     */
    fun buildPlaybackItems(book: Audiobook): List<PlaybackItem> {
        return getChapters(book).mapIndexed { index, chapter ->
            PlaybackItem(index = index, chapter = chapter)
        }
    }

    /**
     * Builds a list of MediaItems, one per chapter, with ClippingConfiguration.
     * For single-chapter books, returns a single unclipped MediaItem.
     */
    fun buildMediaItems(
        book: Audiobook,
        context: Context,
    ): List<MediaItem> {
        val chapters = getChapters(book)
        val uri = book.filePath.takeIf { it.isNotEmpty() }
            ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

        return chapters.map { chapter ->
            val builder = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setArtist(book.author)
                        .setMediaType(
                            if (chapters.size > 1) MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
                            else MediaMetadata.MEDIA_TYPE_AUDIO_BOOK
                        )
                        .apply {
                            if (!book.coverPath.isNullOrBlank()) {
                                val coverFile = java.io.File(book.coverPath)
                                if (coverFile.exists()) {
                                    val coverUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".coverprovider",
                                        coverFile
                                    )
                                    setArtworkUri(coverUri)
                                }
                            }
                        }
                        .build()
                )

            // Apply clipping for multi-chapter books to constrain each item
            // to its chapter's time range within the shared file.
            if (chapters.size > 1) {
                builder.setClippingConfiguration(
                    ClippingConfiguration.Builder()
                        .setStartPositionMs(chapter.startMs)
                        .setEndPositionMs(chapter.endMs)
                        .build()
                )
            }

            builder.build()
        }
    }
}
