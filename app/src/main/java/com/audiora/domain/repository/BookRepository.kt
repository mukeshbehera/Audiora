package com.audiora.domain.repository

import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Bookmark
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Processing result for FFmpeg-based operations at the repository level.
 * UI-facing type that hides FFmpegResult implementation details.
 */
sealed class BookProcessingResult {
    data class Success(val outputPath: String) : BookProcessingResult()
    data class Error(val message: String) : BookProcessingResult()
}

interface BookRepository {
    fun getAudiobooks(): StateFlow<List<Audiobook>>
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
        chapters: List<Chapter>,
        filePath: String? = null,
    )

    // Bookmark operations
    fun getBookmarks(bookId: Int): Flow<List<Bookmark>>
    suspend fun saveBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: Int)

    // ─── FFprobe-based reading ───
    suspend fun readChaptersFromFile(filePath: String): List<Chapter>

    // ─── FFmpeg-based writing ───
    suspend fun createM4B(
        context: android.content.Context,
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions = ConversionOptions.DEFAULT,
        metadata: Map<String, String> = emptyMap(),
        coverData: ByteArray? = null,
        chapters: List<Chapter>? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun exportAudiobook(
        context: android.content.Context,
        bookId: Int,
        outputPath: String,
        options: ExportOptions = ExportOptions.DEFAULT,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun replaceChaptersInFile(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)? = null,
    ): BookProcessingResult

    suspend fun getAudiobookInfo(filePath: String): FFprobeFormat?
}
