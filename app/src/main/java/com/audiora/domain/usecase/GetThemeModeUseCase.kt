package com.audiora.domain.usecase

import com.audiora.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetThemeModeUseCase(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): Flow<String> = settingsRepository.getThemeMode()
}
