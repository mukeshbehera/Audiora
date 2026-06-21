package com.audiora.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.audiora.domain.model.Audiobook

@Entity(tableName = "audiobooks")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val title: String,
    val author: String,
    val narrator: String,
    val publisher: String,
    val genre: String,
    val year: String,
    val description: String,
    val durationMs: Long,
    val currentPositionMs: Long = 0,
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val folderUri: String? = null,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
    val language: String = "",
    val copyright: String = "",
    val chaptersJson: String? = null
) {
    fun toDomain(): Audiobook {
        return Audiobook(
            id = id,
            filePath = filePath,
            title = title,
            author = author,
            narrator = narrator,
            publisher = publisher,
            genre = genre,
            year = year,
            description = description,
            durationMs = durationMs,
            currentPositionMs = currentPositionMs,
            coverPath = coverPath,
            addedAt = addedAt,
            completed = completed,
            folderUri = folderUri,
            fileSize = fileSize,
            lastModified = lastModified,
            language = language,
            copyright = copyright,
            chaptersJson = chaptersJson
        )
    }

    companion object {
        fun fromDomain(domain: Audiobook): BookEntity {
            return BookEntity(
                id = domain.id,
                filePath = domain.filePath,
                title = domain.title,
                author = domain.author,
                narrator = domain.narrator,
                publisher = domain.publisher,
                genre = domain.genre,
                year = domain.year,
                description = domain.description,
                durationMs = domain.durationMs,
                currentPositionMs = domain.currentPositionMs,
                coverPath = domain.coverPath,
                addedAt = domain.addedAt,
                completed = domain.completed,
                folderUri = domain.folderUri,
                fileSize = domain.fileSize,
                lastModified = domain.lastModified,
                language = domain.language,
                copyright = domain.copyright,
                chaptersJson = domain.chaptersJson
            )
        }
    }
}
