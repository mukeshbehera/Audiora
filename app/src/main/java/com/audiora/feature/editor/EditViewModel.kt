package com.audiora.feature.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

class EditViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val initialBookId: Int
) : AndroidViewModel(application) {

    private val _allBooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val allBooks: StateFlow<List<Audiobook>> = _allBooks.asStateFlow()

    private val _selectedBook = MutableStateFlow<Audiobook?>(null)
    val selectedBook: StateFlow<Audiobook?> = _selectedBook.asStateFlow()

    // Form inputs
    val titleInput = MutableStateFlow("")
    val authorInput = MutableStateFlow("")
    val narratorInput = MutableStateFlow("")
    val publisherInput = MutableStateFlow("")
    val genreInput = MutableStateFlow("")
    val languageInput = MutableStateFlow("")
    val descriptionInput = MutableStateFlow("")
    val copyrightInput = MutableStateFlow("")
    val yearInput = MutableStateFlow("")

    val chapters = MutableStateFlow<List<com.audiora.domain.model.Chapter>>(emptyList())

    /* Snapshot of chapters as they were when the current book was first loaded.
     * Used by resetChanges() to undo all edits (name changes, timeline drags). */
    private var originalChapters: List<com.audiora.domain.model.Chapter> = emptyList()

    // Pending cover change. null = no change, "__REMOVE__" = remove cover,
    // any other string = replace with this URI. Applied on saveChanges().
    private var _pendingCoverAction = MutableStateFlow<String?>(null)
    val pendingCoverAction: StateFlow<String?> = _pendingCoverAction.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            try {
                bookRepository.getAudiobooks().collectLatest { books ->
                    _allBooks.value = books
                    
                    // If initial ID is specified, select that book
                    if (initialBookId != -1) {
                        val matchingBook = books.find { it.id == initialBookId }
                        if (matchingBook != null) {
                            selectBook(matchingBook)
                        }
                    } else if (_selectedBook.value == null && books.isNotEmpty()) {
                        // Default to the first book if none selected and we have books
                        selectBook(books.first())
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading audiobooks in EditViewModel")
            }
        }
    }

    fun selectBook(book: Audiobook) {
        val isDifferentBook = _selectedBook.value?.id != book.id
        _selectedBook.value = book
        _saveStatus.value = SaveStatus.Idle

        if (isDifferentBook) {
            titleInput.value = book.title
            authorInput.value = book.author
            narratorInput.value = book.narrator
            publisherInput.value = book.publisher
            genreInput.value = book.genre
            languageInput.value = book.language
            descriptionInput.value = book.description
            copyrightInput.value = book.copyright
            yearInput.value = book.year

            // Load chapters or generate default ones
            val customJson = book.chaptersJson
            val loaded = if (!customJson.isNullOrEmpty()) {
                com.audiora.domain.model.Chapter.deserializeList(customJson)
            } else {
                val duration = if (book.durationMs > 0) book.durationMs else 3600000L
                val count = when {
                    duration < 600000L -> 1
                    duration < 1800000L -> 3
                    duration < 7200000L -> 6
                    else -> 10
                }
                val list = mutableListOf<com.audiora.domain.model.Chapter>()
                val step = duration / count
                val names = listOf(
                    "Prologue & Foundations",
                    "The Spark of Intention",
                    "Navigating the Wilderness",
                    "Uncharted Dimensions",
                    "Shattered Artifacts",
                    "Echoes of Memory",
                    "Unlocking the Cipher",
                    "The Confluence Edge",
                    "Into the Neon Zenith",
                    "Epilogue & Looking Forward",
                    "Afterword Notes",
                    "Collector's Appendix"
                )
                for (i in 0 until count) {
                    val startMs = i * step
                    val endMs = if (i == count - 1) duration else (i + 1) * step
                    val title = if (i < names.size) names[i] else "Section ${i + 1}"
                    list.add(
                        com.audiora.domain.model.Chapter(
                            title = title,
                            startMs = startMs,
                            endMs = endMs,
                            durationMs = endMs - startMs
                        )
                    )
                }
                list
            }
            chapters.value = loaded
            originalChapters = loaded.map { it.copy() }
        }
    }

    /**
     * Resets all chapter edits back to the state when the book was first loaded.
     * Restores chapter names, start times, and end times to their originals.
     */
    fun resetChanges() {
        if (originalChapters.isNotEmpty()) {
            chapters.value = originalChapters.map { it.copy() }
            _saveStatus.value = SaveStatus.Idle
        }
        _pendingCoverAction.value = null
    }

    fun addChapter(title: String, startMs: Long) {
        val book = _selectedBook.value ?: return
        val bookDuration = if (book.durationMs > 0) book.durationMs else 3600000L
        val current = chapters.value.toMutableList()
        
        val newChapter = com.audiora.domain.model.Chapter(
            title = title,
            startMs = startMs,
            endMs = bookDuration,
            durationMs = bookDuration - startMs
        )
        current.add(newChapter)
        
        val sorted = current.sortedBy { it.startMs }
        val reconstructed = mutableListOf<com.audiora.domain.model.Chapter>()
        for (i in sorted.indices) {
            val ch = sorted[i]
            val nextStart = sorted.getOrNull(i + 1)?.startMs ?: bookDuration
            val actualEnd = if (nextStart < ch.startMs) ch.startMs else nextStart
            reconstructed.add(
                ch.copy(
                    endMs = actualEnd,
                    durationMs = actualEnd - ch.startMs
                )
            )
        }
        chapters.value = reconstructed
    }

    fun deleteChapter(index: Int) {
        val current = chapters.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            val book = _selectedBook.value ?: return
            val bookDuration = if (book.durationMs > 0) book.durationMs else 3600000L
            
            if (current.isNotEmpty()) {
                val first = current[0]
                current[0] = first.copy(
                    startMs = 0,
                    durationMs = first.endMs
                )
                
                val reconstructed = mutableListOf<com.audiora.domain.model.Chapter>()
                val sorted = current.sortedBy { it.startMs }
                for (i in sorted.indices) {
                    val ch = sorted[i]
                    val nextStart = sorted.getOrNull(i + 1)?.startMs ?: bookDuration
                    val actualEnd = if (nextStart < ch.startMs) ch.startMs else nextStart
                    reconstructed.add(
                        ch.copy(
                            endMs = actualEnd,
                            durationMs = actualEnd - ch.startMs
                        )
                    )
                }
                chapters.value = reconstructed
            } else {
                chapters.value = emptyList()
            }
        }
    }

    fun updateChapter(index: Int, newName: String, newStartMs: Long) {
        val book = _selectedBook.value ?: return
        val bookDuration = if (book.durationMs > 0) book.durationMs else 3600000L
        val current = chapters.value.toMutableList()
        if (index in current.indices) {
            val oldChapter = current[index]
            val updatedStartMs = if (index == 0) 0L else newStartMs
            current[index] = oldChapter.copy(
                title = newName,
                startMs = updatedStartMs,
                endMs = bookDuration,
                durationMs = bookDuration - updatedStartMs
            )
            
            val sorted = current.sortedBy { it.startMs }
            val reconstructed = mutableListOf<com.audiora.domain.model.Chapter>()
            for (i in sorted.indices) {
                var ch = sorted[i]
                if (i == 0) {
                    ch = ch.copy(startMs = 0L)
                }
                val nextStart = sorted.getOrNull(i + 1)?.startMs ?: bookDuration
                val actualEnd = if (nextStart < ch.startMs) ch.startMs else nextStart
                reconstructed.add(
                    ch.copy(
                        endMs = actualEnd,
                        durationMs = actualEnd - ch.startMs
                    )
                )
            }
            chapters.value = reconstructed
        }
    }

    fun saveChanges() {
        val book = _selectedBook.value ?: return
        _saveStatus.value = SaveStatus.Saving
        viewModelScope.launch {
            try {
                // Run all I/O on IO dispatcher so the main thread is not blocked
                withContext(Dispatchers.IO) {
                    // 1. Save metadata fields
                    bookRepository.updateBookMetadata(
                        context = getApplication(),
                        bookId = book.id,
                        title = titleInput.value.trim(),
                        author = authorInput.value.trim(),
                        narrator = narratorInput.value.trim(),
                        publisher = publisherInput.value.trim(),
                        genre = genreInput.value.trim(),
                        language = languageInput.value.trim(),
                        description = descriptionInput.value.trim(),
                        copyright = copyrightInput.value.trim(),
                        year = yearInput.value.trim()
                    )

                    // 2. Save chapters — pass filePath so updateBookChapters doesn't
                    // need to re-read Room (which may be stale after metadata write).
                    bookRepository.updateBookChapters(
                        context = getApplication(),
                        bookId = book.id,
                        chapters = chapters.value,
                        filePath = book.filePath
                    )

                    // 3. Apply pending cover change if any
                    val pendingCover = _pendingCoverAction.value
                    if (pendingCover != null) {
                        val coverUri = if (pendingCover == "__REMOVE__") null
                            else android.net.Uri.parse(pendingCover)
                        bookRepository.updateBookCover(
                            context = getApplication(),
                            bookId = book.id,
                            imageUri = coverUri
                        )
                    }
                }
                // All I/O complete — update state on Main thread in one batch
                _pendingCoverAction.value = null
                _saveStatus.value = SaveStatus.Success
                // Refresh selectedBook from DB so cover preview and fields update
                val freshBook = bookRepository.getAudiobook(book.id).firstOrNull()
                if (freshBook != null) {
                    _selectedBook.value = freshBook
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save changes")
                _saveStatus.value = SaveStatus.Error(e.localizedMessage ?: "Unknown error while saving changes.")
            }
        }
    }

    fun saveMetadata() {
        val book = _selectedBook.value ?: return
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving
            try {
                bookRepository.updateBookMetadata(
                    context = getApplication(),
                    bookId = book.id,
                    title = titleInput.value.trim(),
                    author = authorInput.value.trim(),
                    narrator = narratorInput.value.trim(),
                    publisher = publisherInput.value.trim(),
                    genre = genreInput.value.trim(),
                    language = languageInput.value.trim(),
                    description = descriptionInput.value.trim(),
                    copyright = copyrightInput.value.trim(),
                    year = yearInput.value.trim()
                )
                _saveStatus.value = SaveStatus.Success
            } catch (e: Exception) {
                Timber.e(e, "Failed to save metadata")
                _saveStatus.value = SaveStatus.Error(e.localizedMessage ?: "Unknown error occurred while writing directly to storage.")
            }
        }
    }

    /**
     * Queues a cover change to be applied when saveChanges() is called.
     * Does NOT write to the file or DB immediately — user can preview the change
     * and save everything together with the Save button.
     */
    fun updateCoverArt(imageUri: android.net.Uri?) {
        _pendingCoverAction.value = imageUri?.toString() ?: "__REMOVE__"
        _saveStatus.value = SaveStatus.Idle
    }

    fun resetStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    companion object {
        fun provideFactory(
            application: Application,
            bookRepository: BookRepository,
            initialBookId: Int
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditViewModel(application, bookRepository, initialBookId) as T
            }
        }
    }
}
