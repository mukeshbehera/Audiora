package com.audiora.domain.usecase

import com.audiora.domain.model.AudiobookFolder
import com.audiora.domain.repository.FolderRepository

class ReorderFoldersUseCase(private val folderRepository: FolderRepository) {
    suspend operator fun invoke(folders: List<AudiobookFolder>) {
        folderRepository.updateFoldersOrder(folders)
    }
}
