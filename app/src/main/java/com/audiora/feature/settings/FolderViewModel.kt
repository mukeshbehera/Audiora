package com.audiora.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audiora.AudioraApplication
import com.audiora.domain.model.AudiobookFolder
import com.audiora.domain.usecase.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AudioraApplication
    private val folderRepository = app.folderRepository

    // UseCases (as requested)
    private val getFoldersUseCase = GetFoldersUseCase(folderRepository)
    private val addFolderUseCase = AddFolderUseCase(folderRepository)
    private val removeFolderUseCase = RemoveFolderUseCase(folderRepository)
    private val reorderFoldersUseCase = ReorderFoldersUseCase(folderRepository)
    private val rescanFolderUseCase = RescanFolderUseCase(folderRepository)

    val folders: StateFlow<List<AudiobookFolder>> = getFoldersUseCase()
        .stateIn(
            scope = viewModelScope,
            started = Eagerly,
            initialValue = emptyList()
        )

    fun addFolder(uri: String, name: String) {
        viewModelScope.launch {
            addFolderUseCase(uri, name)
            // Fire background scan on appScope so it survives navigation away
            app.appScope.launch {
                rescanFolderUseCase(uri)
            }
        }
    }

    fun removeFolder(id: Int, uri: String) {
        viewModelScope.launch {
            removeFolderUseCase(id, uri)
        }
    }

    fun moveFolderUp(index: Int) {
        viewModelScope.launch {
            val list = folders.value.toMutableList()
            if (index > 0 && index < list.size) {
                val temp = list[index]
                list[index] = list[index - 1]
                list[index - 1] = temp
                reorderFoldersUseCase(list)
            }
        }
    }

    fun moveFolderDown(index: Int) {
        viewModelScope.launch {
            val list = folders.value.toMutableList()
            if (index >= 0 && index < list.size - 1) {
                val temp = list[index]
                list[index] = list[index + 1]
                list[index + 1] = temp
                reorderFoldersUseCase(list)
            }
        }
    }

    fun rescanFolder(uri: String) {
        app.appScope.launch {
            rescanFolderUseCase(uri)
        }
    }

    fun rescanAllFolders() {
        app.appScope.launch {
            rescanFolderUseCase(null)
        }
    }

    fun renameFolder(id: Int, newName: String) {
        viewModelScope.launch {
            folderRepository.renameFolder(id, newName)
        }
    }
}
