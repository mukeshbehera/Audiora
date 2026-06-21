package com.audiora.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.audiora.domain.model.AudiobookFolder

@Entity(tableName = "audiobook_folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val name: String,
    val sequenceOrder: Int
) {
    fun toDomain(): AudiobookFolder {
        return AudiobookFolder(
            id = id,
            uri = uri,
            name = name,
            sequenceOrder = sequenceOrder
        )
    }

    companion object {
        fun fromDomain(domain: AudiobookFolder): FolderEntity {
            return FolderEntity(
                id = domain.id,
                uri = domain.uri,
                name = domain.name,
                sequenceOrder = domain.sequenceOrder
            )
        }
    }
}
