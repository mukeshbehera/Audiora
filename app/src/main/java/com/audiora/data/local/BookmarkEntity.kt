package com.audiora.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiora.domain.model.Bookmark

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val positionMs: Long,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Bookmark {
        return Bookmark(
            id = id,
            bookId = bookId,
            positionMs = positionMs,
            name = name,
            addedAt = addedAt
        )
    }

    companion object {
        fun fromDomain(domain: Bookmark): BookmarkEntity {
            return BookmarkEntity(
                id = domain.id,
                bookId = domain.bookId,
                positionMs = domain.positionMs,
                name = domain.name,
                addedAt = domain.addedAt
            )
        }
    }
}
