package com.audiora.domain.usecase

import com.audiora.domain.repository.SettingsRepository

class SetColorSchemeUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(scheme: String) {
        settingsRepository.setColorScheme(scheme)
    }
}
