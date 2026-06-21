package com.audiora.domain.usecase

import com.audiora.domain.repository.FolderRepository

class RemoveFolderUseCase(private val folderRepository: FolderRepository) {
    suspend operator fun invoke(id: Int, uri: String) {
        folderRepository.removeFolder(id, uri)
    }
}
