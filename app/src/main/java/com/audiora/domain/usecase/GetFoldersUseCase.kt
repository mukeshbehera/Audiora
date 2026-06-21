package com.audiora.domain.usecase

import com.audiora.domain.model.AudiobookFolder
import com.audiora.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow

class GetFoldersUseCase(private val folderRepository: FolderRepository) {
    operator fun invoke(): Flow<List<AudiobookFolder>> {
        return folderRepository.getFolders()
    }
}
