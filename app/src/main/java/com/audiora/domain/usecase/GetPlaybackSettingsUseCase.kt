package com.audiora.domain.usecase

import com.audiora.domain.model.PlaybackSettings
import com.audiora.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetPlaybackSettingsUseCase(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): Flow<PlaybackSettings> {
        return combine(
            settingsRepository.getSkipAmount(),
            settingsRepository.getAutoRewind(),
            settingsRepository.getDefaultPlaybackSpeed(),
            settingsRepository.getSleepTimerDefault()
        ) { skip, rewind, speed, sleep ->
            PlaybackSettings(
                skipAmount = skip,
                autoRewind = rewind,
                defaultSpeed = speed,
                sleepTimerDefault = sleep
            )
        }
    }
}
