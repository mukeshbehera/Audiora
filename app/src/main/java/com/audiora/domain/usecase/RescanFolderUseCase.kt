package com.audiora.domain.usecase

import com.audiora.domain.repository.FolderRepository

class RescanFolderUseCase(private val folderRepository: FolderRepository) {
    suspend operator fun invoke(uri: String?) {
        if (uri != null) {
            folderRepository.rescanFolder(uri)
        } else {
            folderRepository.rescanAllFolders()
        }
    }
}
