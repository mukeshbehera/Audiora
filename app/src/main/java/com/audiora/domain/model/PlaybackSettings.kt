package com.audiora.domain.model

data class PlaybackSettings(
    val skipAmount: Int,
    val autoRewind: Int,
    val defaultSpeed: Float,
    val sleepTimerDefault: Int
)
