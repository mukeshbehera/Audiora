package com.audiora.domain.model

data class AudiobookFolder(
    val id: Int = 0,
    val uri: String,
    val name: String,
    val sequenceOrder: Int
)
