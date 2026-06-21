package com.audiora.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getThemeMode(): Flow<String>
    suspend fun setThemeMode(mode: String)

    fun getColorScheme(): Flow<String>
    suspend fun setColorScheme(scheme: String)

    fun getSkipAmount(): Flow<Int>
    suspend fun setSkipAmount(seconds: Int)

    fun getAutoRewind(): Flow<Int>
    suspend fun setAutoRewind(seconds: Int)

    fun getDefaultPlaybackSpeed(): Flow<Float>
    suspend fun setDefaultPlaybackSpeed(speed: Float)

    fun getSleepTimerDefault(): Flow<Int>
    suspend fun setSleepTimerDefault(minutes: Int)

    fun isOnboardingCompleted(): Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)
}
