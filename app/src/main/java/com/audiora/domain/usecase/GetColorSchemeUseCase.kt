package com.audiora.domain.usecase

import com.audiora.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetColorSchemeUseCase(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): Flow<String> = settingsRepository.getColorScheme()
}
