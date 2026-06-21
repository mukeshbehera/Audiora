package com.audiora.domain.usecase

import com.audiora.domain.repository.SettingsRepository

class SetThemeModeUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(mode: String) {
        settingsRepository.setThemeMode(mode)
    }
}
