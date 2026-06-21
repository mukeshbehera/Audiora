package com.audiora.domain.usecase

import com.audiora.domain.model.PlaybackSettings
import com.audiora.domain.repository.SettingsRepository

class UpdatePlaybackSettingsUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(settings: PlaybackSettings) {
        settingsRepository.setSkipAmount(settings.skipAmount)
        settingsRepository.setAutoRewind(settings.autoRewind)
        settingsRepository.setDefaultPlaybackSpeed(settings.defaultSpeed)
        settingsRepository.setSleepTimerDefault(settings.sleepTimerDefault)
    }
}
