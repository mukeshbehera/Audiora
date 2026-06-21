package com.audiora.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM audiobook_folders ORDER BY sequenceOrder ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM audiobook_folders WHERE id = :id")
    suspend fun deleteFolder(id: Int)

    @Query("SELECT * FROM audiobook_folders WHERE uri = :uri LIMIT 1")
    suspend fun getFolderByUri(uri: String): FolderEntity?

    @Query("UPDATE audiobook_folders SET name = :newName WHERE id = :id")
    suspend fun renameFolder(id: Int, newName: String)
}
