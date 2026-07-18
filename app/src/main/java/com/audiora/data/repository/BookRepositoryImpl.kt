package com.audiora.data.repository

import com.audiora.data.local.BookDao
import com.audiora.data.local.BookEntity
import com.audiora.data.local.BookmarkDao
import com.audiora.data.local.BookmarkEntity
import com.audiora.data.processing.FFmpegService
import com.audiora.data.processing.dto.FFmpegResult
import com.audiora.data.processing.dto.FFprobeFormat
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Bookmark
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.ConversionOptions
import com.audiora.domain.model.ExportOptions
import com.audiora.domain.repository.BookProcessingResult
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val appScope: CoroutineScope,
    private val ffmpegService: FFmpegService,
) : BookRepository {

    // In-memory cache mirroring Voice's BookContentRepoImpl pattern: eagerly subscribed
    // to Room on appScope so subscribers always get the latest value without DB delay.
    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())

    init {
        appScope.launch {
            bookDao.getAllAudiobooks().map { entities ->
                entities.map { it.toDomain() }
            }.collect { list ->
                _audiobooks.value = list
            }
        }
    }

    override fun getAudiobooks(): StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    override fun getAudiobook(id: Int): Flow<Audiobook?> {
        return bookDao.getAudiobookById(id).map { it?.toDomain() }
    }

    override suspend fun saveAudiobook(audiobook: Audiobook) {
        bookDao.insertAudiobook(BookEntity.fromDomain(audiobook))
    }

    override suspend fun deleteAudiobook(id: Int) {
        bookDao.deleteAudiobookById(id)
    }

    override fun getBookmarks(bookId: Int): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForBook(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(BookmarkEntity.fromDomain(bookmark))
    }

    override suspend fun deleteBookmark(id: Int) {
        bookmarkDao.deleteBookmarkById(id)
    }

    override suspend fun updateBookMetadata(
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
    ) {
        val existingEntity = bookDao.getAudiobookById(bookId).first() ?: throw IllegalArgumentException("Book with ID $bookId not found")
        val filePathStr = existingEntity.filePath
        if (filePathStr.isNotEmpty()) {
            val isContentUri = filePathStr.startsWith("content://")
            var tempFile: java.io.File? = null
            try {
                if (isContentUri) {
                    val uri = android.net.Uri.parse(filePathStr)
                    val ext = "m4b"
                    tempFile = java.io.File.createTempFile("edit_metadata_", ".$ext", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                    val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                    tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                    tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, author)
                    tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, author)
                    tag.setField(org.jaudiotagger.tag.FieldKey.COMPOSER, narrator)
                    tag.setField(org.jaudiotagger.tag.FieldKey.RECORD_LABEL, publisher)
                    tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, genre)
                    tag.setField(org.jaudiotagger.tag.FieldKey.LANGUAGE, language)
                    // Note: COMMENT field is reserved for chapters JSON in updateBookChapters
                    tag.setField(org.jaudiotagger.tag.FieldKey.COPYRIGHT, copyright)
                    tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, year)
                    org.jaudiotagger.audio.AudioFileIO.write(audioFile)

                    context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val file = java.io.File(filePathStr)
                    if (file.exists()) {
                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                        val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, author)
                        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, author)
                        tag.setField(org.jaudiotagger.tag.FieldKey.COMPOSER, narrator)
                        tag.setField(org.jaudiotagger.tag.FieldKey.RECORD_LABEL, publisher)
                        tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, genre)
                        tag.setField(org.jaudiotagger.tag.FieldKey.LANGUAGE, language)
                        // Note: COMMENT field is reserved for chapters JSON in updateBookChapters
                        tag.setField(org.jaudiotagger.tag.FieldKey.COPYRIGHT, copyright)
                        tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, year)
                        org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing metadata directly to file: $filePathStr")
            } finally {
                try {
                    tempFile?.delete()
                } catch (ignored: Exception) {}
            }
        }

        val updatedEntity = existingEntity.copy(
            title = title,
            author = author,
            narrator = narrator,
            publisher = publisher,
            genre = genre,
            language = language,
            description = description,
            copyright = copyright,
            year = year,
            lastModified = System.currentTimeMillis()
        )
        bookDao.updateAudiobook(updatedEntity)
    }

    override suspend fun updateBookCover(
        context: android.content.Context,
        bookId: Int,
        imageUri: android.net.Uri?
    ) {
        val existingEntity = bookDao.getAudiobookById(bookId).first() ?: throw IllegalArgumentException("Book with ID $bookId not found")
        val filePathStr = existingEntity.filePath

        // 1. Delete old local cover file if valid
        existingEntity.coverPath?.let { oldPath ->
            if (oldPath.startsWith("/") &&
                !oldPath.contains("nebula") &&
                !oldPath.contains("horizon") &&
                !oldPath.contains("eternity") &&
                !oldPath.contains("neon") &&
                !oldPath.contains("infinite")
            ) {
                try {
                    java.io.File(oldPath).delete()
                } catch (e: Exception) {
                    Timber.e(e, "Error deleting old cover file: $oldPath")
                }
            }
        }

        var newCoverPath: String? = null

        if (imageUri != null) {
            // Pick image bytes
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            if (bytes != null) {
                // Determine format
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                // Embed directly within audiobook file metadata tags
                if (filePathStr.isNotEmpty()) {
                    val isContentUri = filePathStr.startsWith("content://")
                    var tempFile: java.io.File? = null
                    try {
                        if (isContentUri) {
                            val uri = android.net.Uri.parse(filePathStr)
                            val ext = "m4b"
                            tempFile = java.io.File.createTempFile("edit_cover_", ".$ext", context.cacheDir)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                            val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                            tag.deleteArtworkField()
                            val artwork = org.jaudiotagger.tag.images.AndroidArtwork()
                            artwork.binaryData = bytes
                            artwork.mimeType = mimeType
                            tag.setField(artwork)
                            org.jaudiotagger.audio.AudioFileIO.write(audioFile)

                            context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                        } else {
                            val file = java.io.File(filePathStr)
                            if (file.exists()) {
                                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                                val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                                tag.deleteArtworkField()
                                val artwork = org.jaudiotagger.tag.images.AndroidArtwork()
                                artwork.binaryData = bytes
                                artwork.mimeType = mimeType
                                tag.setField(artwork)
                                org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error writing cover artwork to file: $filePathStr")
                    } finally {
                        try {
                            tempFile?.delete()
                        } catch (ignored: Exception) {}
                    }
                }

                // Save selected cover locally for UI displaying
                val coversDirectory = java.io.File(context.filesDir, "covers")
                if (!coversDirectory.exists()) {
                    coversDirectory.mkdirs()
                }
                val hash = bookId.toString() + "_" + System.currentTimeMillis()
                val extension = when {
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val destination = java.io.File(coversDirectory, "cover_$hash.$extension")
                destination.writeBytes(bytes)
                newCoverPath = destination.absolutePath
            }
        } else {
            // Remove cover case: delete embedded tag
            if (filePathStr.isNotEmpty()) {
                val isContentUri = filePathStr.startsWith("content://")
                var tempFile: java.io.File? = null
                try {
                    if (isContentUri) {
                        val uri = android.net.Uri.parse(filePathStr)
                        val ext = "m4b"
                        tempFile = java.io.File.createTempFile("remove_cover_", ".$ext", context.cacheDir)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                        val tag = audioFile.tag
                        if (tag != null) {
                            tag.deleteArtworkField()
                            org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                            context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                                tempFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } else {
                        val file = java.io.File(filePathStr)
                        if (file.exists()) {
                            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                            val tag = audioFile.tag
                            if (tag != null) {
                                tag.deleteArtworkField()
                                org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error removing cover metadata from file: $filePathStr")
                } finally {
                    try {
                        tempFile?.delete()
                    } catch (ignored: Exception) {}
                }
            }
        }

        // Update DB
        val updatedEntity = existingEntity.copy(
            coverPath = newCoverPath,
            lastModified = System.currentTimeMillis()
        )
        bookDao.updateAudiobook(updatedEntity)
    }

    override suspend fun updateBookChapters(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        filePath: String?,
    ) {
        val filePathStr = if (filePath != null) filePath else {
            bookDao.getAudiobookById(bookId).first()?.filePath
                ?: throw IllegalArgumentException("Book with ID $bookId not found")
        }
        val serialized = Chapter.serializeList(chapters)

        // Always update Room DB first so data is never lost
        bookDao.getAudiobookById(bookId).first()?.let { existingEntity ->
            val updatedEntity = existingEntity.copy(
                chaptersJson = serialized,
                lastModified = System.currentTimeMillis()
            )
            bookDao.updateAudiobook(updatedEntity)
        }

        // Then attempt to write to the M4B file — if this fails, Room still has the data
        if (filePathStr.isNotEmpty()) {
            val isContentUri = filePathStr.startsWith("content://")
            var tempFile: java.io.File? = null
            try {
                if (isContentUri) {
                    val uri = android.net.Uri.parse(filePathStr)
                    val ext = "m4b"
                    tempFile = java.io.File.createTempFile("edit_chapters_", ".$ext", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                    val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }

                    // We save the chapters in the standard COMMENT field of the M4B
                    tag.setField(org.jaudiotagger.tag.FieldKey.COMMENT, "ChaptersJSON:$serialized")
                    org.jaudiotagger.audio.AudioFileIO.write(audioFile)

                    context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val file = java.io.File(filePathStr)
                    if (file.exists()) {
                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                        val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                        tag.setField(org.jaudiotagger.tag.FieldKey.COMMENT, "ChaptersJSON:$serialized")
                        org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing chapters metadata to M4B: $filePathStr")
                throw e
            } finally {
                try {
                    tempFile?.delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    // ─── FFprobe-based reading ───

    override suspend fun readChaptersFromFile(filePath: String): List<Chapter> {
        return ffmpegService.readChapters(filePath)
    }

    // ─── FFmpeg-based writing ───

    override suspend fun createM4B(
        context: android.content.Context,
        inputFiles: List<String>,
        outputPath: String,
        options: ConversionOptions,
        metadata: Map<String, String>,
        coverData: ByteArray?,
        chapters: List<Chapter>?,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val result = ffmpegService.createM4B(
                inputFiles = inputFiles,
                outputPath = outputPath,
                options = options,
                metadata = metadata,
                coverData = coverData,
                chapters = chapters,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                BookProcessingResult.Success(outputPath)
            } else {
                val error = result as FFmpegResult.Error
                BookProcessingResult.Error("Processing failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "createM4B failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun exportAudiobook(
        context: android.content.Context,
        bookId: Int,
        outputPath: String,
        options: ExportOptions,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val book = bookDao.getAudiobookById(bookId).first()
                ?: return BookProcessingResult.Error("Book with ID $bookId not found")
            val result = ffmpegService.exportAudiobook(
                inputPath = book.filePath,
                outputPath = outputPath,
                options = options,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                BookProcessingResult.Success(outputPath)
            } else {
                val error = result as FFmpegResult.Error
                BookProcessingResult.Error("Export failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "exportAudiobook failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun replaceChaptersInFile(
        context: android.content.Context,
        bookId: Int,
        chapters: List<Chapter>,
        onProgress: ((Float) -> Unit)?,
    ): BookProcessingResult {
        return try {
            val book = bookDao.getAudiobookById(bookId).first()
                ?: return BookProcessingResult.Error("Book with ID $bookId not found")
            val result = ffmpegService.replaceChaptersInFile(
                filePath = book.filePath,
                chapters = chapters,
                onProgress = onProgress,
            )
            if (result.isSuccess) {
                // Also update Room cache
                val serialized = Chapter.serializeList(chapters)
                bookDao.getAudiobookById(bookId).first()?.let { entity ->
                    bookDao.updateAudiobook(entity.copy(
                        chaptersJson = serialized,
                        lastModified = System.currentTimeMillis()
                    ))
                }
                BookProcessingResult.Success(book.filePath)
            } else {
                val error = result as FFmpegResult.Error
                BookProcessingResult.Error("Chapter replacement failed: ${error.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "replaceChaptersInFile failed")
            BookProcessingResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun getAudiobookInfo(filePath: String): FFprobeFormat? {
        return ffmpegService.readFormat(filePath)
    }
}
