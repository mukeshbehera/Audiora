package com.audiora.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    val audiobooks: StateFlow<List<Audiobook>> = bookRepository.getAudiobooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Prepopulate database with mock data if it's empty
        viewModelScope.launch {
            try {
                val list = bookRepository.getAudiobooks().first()
                if (list.isEmpty()) {
                    prepopulateMockData()
                }
            } catch (e: Exception) {
                // Handle potential flow initialization errors gracefully
            }
        }
    }

    private suspend fun prepopulateMockData() {
        val mockData = listOf(
            Audiobook(
                id = 1,
                title = "The Nebula Artifact",
                author = "Aria Thorne",
                narrator = "Marcus Vance",
                publisher = "Galactic Press",
                genre = "Sci-Fi",
                year = "2284",
                description = "Deep in the Orion sector, an ancient probe is discovered transmitting ancient celestial coordinates.",
                durationMs = 14 * 3600 * 1000 + 15 * 60 * 1000, // 14h 15m
                currentPositionMs = 5 * 3600 * 1000 + 40 * 60 * 1000, // ~39.7% progressed
                filePath = "",
                coverPath = "nebula"
            ),
            Audiobook(
                id = 2,
                title = "Beyond the Horizon",
                author = "Demetric Grayson",
                narrator = "Helena Rostova",
                publisher = "Summit Audio",
                genre = "Philosophy",
                year = "2026",
                description = "A profound meditation on consciousness, mindfulness, and mapping the future of human intention.",
                durationMs = 8 * 3600 * 1000, // 8h
                currentPositionMs = 8 * 3600 * 1000, // 100% completed
                filePath = "",
                coverPath = "horizon",
                completed = true
            ),
            Audiobook(
                id = 3,
                title = "Echoes of Eternity",
                author = "Julian Pendelton",
                narrator = "Arthur Sterling",
                publisher = "Dreamscape Library",
                genre = "Fantasy",
                year = "2025",
                description = "When the ancient magical seals begin to break, a young scribe holding absolute memory must seek the runes.",
                durationMs = 22 * 3600 * 1000 + 45 * 60 * 1000, // 22h 45m
                currentPositionMs = 2 * 3600 * 1000 + 16 * 60 * 1000, // ~10.0% progressed
                filePath = "",
                coverPath = "eternity"
            ),
            Audiobook(
                id = 4,
                title = "Neon Nexus",
                author = "Kira Thorne",
                narrator = "Sato Takahashi",
                publisher = "Sub-Level Zero",
                genre = "Cyberpunk",
                year = "2088",
                description = "In the mega-city underwires, a rogue netrunner finds a ghost intelligence inhabiting her biomechanical optic nerves.",
                durationMs = 10 * 3600 * 1000 + 30 * 60 * 1000, // 10h 30m
                currentPositionMs = 0, // 0% progressed
                filePath = "",
                coverPath = "neon"
            ),
            Audiobook(
                id = 5,
                title = "The Infinite Mind",
                author = "Dr. Sarah Jenkins",
                narrator = "Dr. Sarah Jenkins",
                publisher = "Mindful Media",
                genre = "Self-Help",
                year = "2024",
                description = "Mastering neuroplasticity exercises to break bad cycles, accelerate focus, and harness daily motivation.",
                durationMs = 6 * 3600 * 1000 + 10 * 60 * 1000, // 6h 10m
                currentPositionMs = 4 * 3600 * 1000 + 37 * 60 * 1000, // ~75% progressed
                filePath = "",
                coverPath = "infinite"
            )
        )
        for (book in mockData) {
            bookRepository.saveAudiobook(book)
        }
    }

    companion object {
        fun provideFactory(bookRepository: BookRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(bookRepository) as T
            }
        }
    }
}
