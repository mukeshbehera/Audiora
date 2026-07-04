package com.audiora.feature.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("audiora_search_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()

    private val allAudiobooks: StateFlow<List<Audiobook>> = bookRepository.getAudiobooks()

    val searchResults: StateFlow<List<Audiobook>> = combine(allAudiobooks, _searchQuery) { books, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            books.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true) ||
                book.narrator.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadRecentSearches()
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSearchAction(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            addRecentSearch(trimmed)
        }
    }

    private fun loadRecentSearches() {
        val saved = sharedPrefs.getStringSet("recent_searches_list", emptySet()) ?: emptySet()
        // SharedPreferences string sets don't preserve order. We can store order in a comma-separated string,
        // or just sort alphabetically, or store as a joined string. Let's use a single joined string for order!
        val orderedString = sharedPrefs.getString("ordered_recent_searches", "") ?: ""
        val list = if (orderedString.isNotEmpty()) {
            orderedString.split("|||").filter { it.isNotEmpty() }
        } else {
            saved.toList()
        }
        _recentSearches.value = list.take(10)
    }

    fun addRecentSearch(query: String) {
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(query) // Remove duplicate if exists so it goes to top
        currentList.add(0, query)
        val trimmedList = currentList.distinct().take(10)
        
        _recentSearches.value = trimmedList
        
        sharedPrefs.edit()
            .putStringSet("recent_searches_list", trimmedList.toSet())
            .putString("ordered_recent_searches", trimmedList.joinToString("|||"))
            .apply()
    }

    fun removeRecentSearch(query: String) {
        val currentList = _recentSearches.value.toMutableList()
        currentList.remove(query)
        _recentSearches.value = currentList
        
        sharedPrefs.edit()
            .putStringSet("recent_searches_list", currentList.toSet())
            .putString("ordered_recent_searches", currentList.joinToString("|||"))
            .apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        sharedPrefs.edit()
            .remove("recent_searches_list")
            .remove("ordered_recent_searches")
            .apply()
    }

    companion object {
        fun provideFactory(application: Application, bookRepository: BookRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(application, bookRepository) as T
            }
        }
    }
}
