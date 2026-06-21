package com.audiora.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM audiobooks ORDER BY addedAt DESC")
    fun getAllAudiobooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM audiobooks WHERE id = :id LIMIT 1")
    fun getAudiobookById(id: Int): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobook(book: BookEntity)

    @Update
    suspend fun updateAudiobook(book: BookEntity)

    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteAudiobookById(id: Int)

    @Query("DELETE FROM audiobooks WHERE folderUri = :folderUri")
    suspend fun deleteAudiobooksByFolderUri(folderUri: String)

    @Query("SELECT * FROM audiobooks WHERE folderUri = :folderUri")
    suspend fun getAudiobooksByFolderUri(folderUri: String): List<BookEntity>

    @Query("SELECT * FROM audiobooks WHERE filePath = :filePath LIMIT 1")
    suspend fun getAudiobookByFilePath(filePath: String): BookEntity?
}
