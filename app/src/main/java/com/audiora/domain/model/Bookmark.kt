package com.audiora.domain.model

data class Bookmark(
    val id: Int = 0,
    val bookId: Int,
    val positionMs: Long,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)
