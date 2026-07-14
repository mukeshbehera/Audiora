package com.audiora.data.repository

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.documentfile.provider.DocumentFile
import com.audiora.data.local.BookDao
import com.audiora.data.local.BookEntity
import com.audiora.data.local.FolderDao
import com.audiora.data.local.FolderEntity
import com.audiora.data.local.M4bChapterExtractor
import com.audiora.domain.model.Chapter
import com.audiora.domain.model.AudiobookFolder
import com.audiora.domain.repository.FolderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class FolderRepositoryImpl(
    private val context: Context,
    private val folderDao: FolderDao,
    private val bookDao: BookDao,
    appScope: CoroutineScope
) : FolderRepository {

    // In-memory cache mirroring BookRepositoryImpl pattern: eagerly subscribed
    // to Room on appScope so subscribers always get the latest value without DB delay.
    private val _folders = MutableStateFlow<List<AudiobookFolder>>(emptyList())

    init {
        appScope.launch {
            folderDao.getAllFolders().map { entities ->
                entities.map { it.toDomain() }
            }.collect { list ->
                _folders.value = list
            }
        }
    }

    override fun getFolders(): StateFlow<List<AudiobookFolder>> = _folders.asStateFlow()

    private val scanMutex = Mutex()

    override suspend fun addFolder(uri: String, name: String) = withContext(Dispatchers.IO) {
        val existing = folderDao.getFolderByUri(uri)
        if (existing == null) {
            val defaultOrder = (System.currentTimeMillis() / 1000).toInt()
            val folderEntity = FolderEntity(
                uri = uri,
                name = name,
                sequenceOrder = defaultOrder
            )
            folderDao.insertFolder(folderEntity)
            // Note: rescanFolder is NOT called here — the caller must launch it
            // on an application-scoped coroutine so it survives screen navigation.
        }
    }

    override suspend fun removeFolder(id: Int, uri: String) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
        bookDao.deleteAudiobooksByFolderUri(uri)
    }

    override suspend fun updateFoldersOrder(folders: List<AudiobookFolder>) = withContext(Dispatchers.IO) {
        folders.forEachIndexed { index, folder ->
            val updated = folder.copy(sequenceOrder = index)
            folderDao.updateFolder(FolderEntity.fromDomain(updated))
        }
    }

    override suspend fun rescanFolder(uri: String) = scanMutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(uri)
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            if (documentFile == null || !documentFile.isDirectory) {
                Timber.e("Rescan folder failed: DocumentFile is null or not a directory for $uri")
                return@withContext
            }

            val scannedFiles = mutableListOf<ScannedFile>()
            scanRecursively(documentFile, scannedFiles)

            val scannedPaths = mutableSetOf<String>()

            for (scanned in scannedFiles) {
                val filePathStr = scanned.uri.toString()
                scannedPaths.add(filePathStr)

                val existingBook = bookDao.getAudiobookByFilePath(filePathStr)
                if (existingBook == null) {
                    val metadata = extractMetadata(scanned.uri, scanned.name)

                    // Extract chapters at scan time so they're available instantly at play time
                    val chaptersJson = try {
                        val chapters = M4bChapterExtractor.extractFromUri(context, scanned.uri, metadata.durationMs)
                        if (chapters.isNotEmpty()) Chapter.serializeList(chapters) else null
                    } catch (e: Exception) {
                        Timber.e(e, "Error extracting chapters for $filePathStr")
                        null
                    }

                    val bookEntity = BookEntity(
                        filePath = filePathStr,
                        title = metadata.title,
                        author = metadata.author,
                        narrator = metadata.narrator,
                        publisher = metadata.publisher,
                        genre = metadata.genre,
                        year = metadata.year,
                        description = metadata.description,
                        durationMs = metadata.durationMs,
                        coverPath = metadata.coverPath,
                        fileSize = scanned.size,
                        lastModified = scanned.lastModified,
                        folderUri = uri,
                        language = metadata.language,
                        copyright = metadata.copyright,
                        chaptersJson = chaptersJson
                    )
                    bookDao.insertAudiobook(bookEntity)
                } else {
                    val hasChanged = existingBook.fileSize != scanned.size || existingBook.lastModified != scanned.lastModified
                    if (hasChanged) {
                        val metadata = extractMetadata(scanned.uri, scanned.name)

                        // Re-extract chapters when file has changed
                        val chaptersJson = try {
                            val chapters = M4bChapterExtractor.extractFromUri(context, scanned.uri, metadata.durationMs)
                            if (chapters.isNotEmpty()) Chapter.serializeList(chapters) else existingBook.chaptersJson
                        } catch (e: Exception) {
                            Timber.e(e, "Error extracting chapters for $filePathStr")
                            existingBook.chaptersJson
                        }

                        val updatedBook = existingBook.copy(
                            title = metadata.title,
                            author = metadata.author,
                            narrator = metadata.narrator,
                            publisher = metadata.publisher,
                            genre = metadata.genre,
                            year = metadata.year,
                            description = metadata.description,
                            durationMs = metadata.durationMs,
                            coverPath = metadata.coverPath ?: existingBook.coverPath,
                            fileSize = scanned.size,
                            lastModified = scanned.lastModified,
                            folderUri = uri,
                            language = metadata.language,
                            copyright = metadata.copyright,
                            chaptersJson = chaptersJson
                        )
                        bookDao.updateAudiobook(updatedBook)
                    } else if (existingBook.chaptersJson.isNullOrEmpty()) {
                        // Upgrade path: extract chapters for previously scanned files
                        val chaptersJson = try {
                            val chapters = M4bChapterExtractor.extractFromUri(context, scanned.uri, existingBook.durationMs)
                            if (chapters.isNotEmpty()) Chapter.serializeList(chapters) else null
                        } catch (e: Exception) {
                            Timber.e(e, "Error extracting chapters for $filePathStr")
                            null
                        }
                        if (chaptersJson != null) {
                            bookDao.updateAudiobook(existingBook.copy(chaptersJson = chaptersJson))
                        }
                    } else if (existingBook.folderUri != uri) {
                        bookDao.updateAudiobook(existingBook.copy(folderUri = uri))
                    }
                }
            }

            // Delete books that no longer exist in this folder
            val dbBooks = bookDao.getAudiobooksByFolderUri(uri)
            for (dbBook in dbBooks) {
                if (dbBook.filePath !in scannedPaths) {
                    bookDao.deleteAudiobookById(dbBook.id)
                    dbBook.coverPath?.let { path ->
                        try {
                            val f = java.io.File(path)
                            if (f.exists()) {
                                f.delete()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error deleting cover file $path")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error rescanning folder $uri")
        }
        }
    }

    override suspend fun rescanAllFolders() = withContext(Dispatchers.IO) {
        try {
            val folders = folderDao.getAllFolders().first()
            for (f in folders) {
                rescanFolder(f.uri)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error rescanning all folders")
        }
    }

    override suspend fun renameFolder(id: Int, name: String) = withContext(Dispatchers.IO) {
        try {
            folderDao.renameFolder(id, name)
        } catch (e: Exception) {
            Timber.e(e, "Error renaming folder $id to $name")
        }
    }

    private fun scanRecursively(root: DocumentFile, results: MutableList<ScannedFile>) {
        val files = root.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanRecursively(file, results)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext == "m4b") {
                    results.add(ScannedFile(file.uri, name, file.length(), file.lastModified()))
                }
            }
        }
    }

    private fun extractMetadata(uri: Uri, fileName: String): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        var title = fileName.substringBeforeLast('.')
        var author = "Unknown Author"
        var narrator = "Unknown Narrator"
        var publisher = "Unknown Publisher"
        var genre = "Audiobook"
        var year = "2026"
        var durationMs = 3600000L
        var coverPath: String? = null

        try {
            retriever.setDataSource(context, uri)
            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val extractedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val extractedGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val extractedYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val extractedWriter = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)

            if (!extractedTitle.isNullOrEmpty()) title = extractedTitle
            if (!extractedArtist.isNullOrEmpty()) author = extractedArtist
            if (!extractedDuration.isNullOrEmpty()) {
                durationMs = extractedDuration.toLongOrNull() ?: 3600000L
            }
            if (!extractedGenre.isNullOrEmpty()) genre = extractedGenre
            if (!extractedYear.isNullOrEmpty()) year = extractedYear
            if (!extractedWriter.isNullOrEmpty()) narrator = extractedWriter

            // Extract Cover Artwork
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null) {
                try {
                    val coversDirectory = java.io.File(context.filesDir, "covers")
                    if (!coversDirectory.exists()) {
                        coversDirectory.mkdirs()
                    }
                    val hash = Math.abs(uri.toString().hashCode()).toString()
                    val destination = java.io.File(coversDirectory, "cover_$hash.jpg")
                    destination.writeBytes(pictureBytes)
                    coverPath = destination.absolutePath
                } catch (e: Exception) {
                    Timber.e(e, "Error saving scanned cover art for $uri")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata for $uri")
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return ExtractedMetadata(
            title = title,
            author = author,
            narrator = narrator,
            publisher = publisher,
            genre = genre,
            year = year,
            description = "Imported from folder",
            durationMs = durationMs,
            coverPath = coverPath,
            language = "",
            copyright = ""
        )
    }

    private data class ScannedFile(val uri: Uri, val name: String, val size: Long, val lastModified: Long)
    private data class ExtractedMetadata(
        val title: String,
        val author: String,
        val narrator: String,
        val publisher: String,
        val genre: String,
        val year: String,
        val description: String,
        val durationMs: Long,
        val coverPath: String?,
        val language: String,
        val copyright: String
    )
}
