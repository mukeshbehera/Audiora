package com.audiora.domain.usecase

import com.audiora.domain.repository.FolderRepository

class AddFolderUseCase(private val folderRepository: FolderRepository) {
    suspend operator fun invoke(uri: String, name: String) {
        folderRepository.addFolder(uri, name)
    }
}
