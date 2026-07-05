package com.audiora.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    val audiobooks: StateFlow<List<Audiobook>> = bookRepository.getAudiobooks()

    companion object {
        fun provideFactory(bookRepository: BookRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(bookRepository) as T
            }
        }
    }
}
