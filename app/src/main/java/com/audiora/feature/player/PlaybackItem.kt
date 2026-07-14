package com.audiora.feature.player

import com.audiora.domain.model.Chapter

/**
 * Maps a chapter to its index in the player's media item list.
 *
 * Mirrors Voice's PlaybackItem in core/playback/session/PlaybackItems.kt.
 * Audiora chapters map 1:1 to media items (no ChapterMark hierarchy needed).
 */
data class PlaybackItem(
    val index: Int,
    val chapter: Chapter,
)
