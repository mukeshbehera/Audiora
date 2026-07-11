package com.audiora.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audiora.AudioraApplication
import com.audiora.domain.model.PlaybackSettings
import com.audiora.domain.usecase.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AudioraApplication
    private val settingsRepository = app.settingsRepository

    // UseCases
    private val getThemeModeUseCase = GetThemeModeUseCase(settingsRepository)
    private val setThemeModeUseCase = SetThemeModeUseCase(settingsRepository)
    private val getColorSchemeUseCase = GetColorSchemeUseCase(settingsRepository)
    private val setColorSchemeUseCase = SetColorSchemeUseCase(settingsRepository)
    private val getPlaybackSettingsUseCase = GetPlaybackSettingsUseCase(settingsRepository)
    private val updatePlaybackSettingsUseCase = UpdatePlaybackSettingsUseCase(settingsRepository)

    val themeMode: StateFlow<String> = getThemeModeUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "SYSTEM"
        )

    val colorSchemeName: StateFlow<String> = getColorSchemeUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "AUDIORA_PURPLE"
        )

    val playbackSettings: StateFlow<PlaybackSettings> = getPlaybackSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackSettings(15, 3, 1.0f, 0)
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            setThemeModeUseCase(mode)
        }
    }

    fun setColorScheme(scheme: String) {
        viewModelScope.launch {
            setColorSchemeUseCase(scheme)
        }
    }

    fun updatePlaybackSettings(settings: PlaybackSettings) {
        viewModelScope.launch {
            updatePlaybackSettingsUseCase(settings)
        }
    }

    fun setSkipAmount(seconds: Int) {
        viewModelScope.launch {
            val current = playbackSettings.value
            updatePlaybackSettingsUseCase(current.copy(skipAmount = seconds))
        }
    }

    fun setAutoRewind(seconds: Int) {
        viewModelScope.launch {
            val current = playbackSettings.value
            updatePlaybackSettingsUseCase(current.copy(autoRewind = seconds))
        }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            val current = playbackSettings.value
            updatePlaybackSettingsUseCase(current.copy(defaultSpeed = speed))
        }
    }

    fun setSleepTimerDefault(minutes: Int) {
        viewModelScope.launch {
            val current = playbackSettings.value
            updatePlaybackSettingsUseCase(current.copy(sleepTimerDefault = minutes))
        }
    }
}
