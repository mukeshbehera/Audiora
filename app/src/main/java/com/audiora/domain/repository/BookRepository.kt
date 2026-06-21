package com.audiora.domain.repository

import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAudiobooks(): Flow<List<Audiobook>>
    fun getAudiobook(id: Int): Flow<Audiobook?>
    suspend fun saveAudiobook(audiobook: Audiobook)
    suspend fun deleteAudiobook(id: Int)
    suspend fun updateBookMetadata(
        context: android.content.Context,
        bookId: Int,
        title: String,
        author: String,
        narrator: String,
        publisher: String,
        genre: String,
        language: String,
        description: String,
        copyright: String,
        year: String
    )
    suspend fun updateBookCover(
        context: android.content.Context,
        bookId: Int,
        imageUri: android.net.Uri?
    )
    suspend fun updateBookChapters(
        context: android.content.Context,
        bookId: Int,
        chapters: List<com.audiora.domain.model.Chapter>
    )

    // Bookmark operations
    fun getBookmarks(bookId: Int): Flow<List<Bookmark>>
    suspend fun saveBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: Int)
}
