package com.audiora.domain.repository

import com.audiora.domain.model.AudiobookFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getFolders(): Flow<List<AudiobookFolder>>
    suspend fun addFolder(uri: String, name: String)
    suspend fun removeFolder(id: Int, uri: String)
    suspend fun updateFoldersOrder(folders: List<AudiobookFolder>)
    suspend fun rescanFolder(uri: String)
    suspend fun rescanAllFolders()
    suspend fun renameFolder(id: Int, name: String)
}
