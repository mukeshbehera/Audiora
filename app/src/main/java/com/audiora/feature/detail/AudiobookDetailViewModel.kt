package com.audiora.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class ExportStatus {
    object Idle : ExportStatus()
    object Exporting : ExportStatus()
    data class Success(val destinationUri: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val audiobook: Audiobook) : DetailUiState()
    object Error : DetailUiState()
}

class AudiobookDetailViewModel(
    private val bookRepository: BookRepository,
    private val bookId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    init {
        loadAudiobook()
    }

    private fun loadAudiobook() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                bookRepository.getAudiobook(bookId).collect { book ->
                    if (book != null) {
                        _uiState.value = DetailUiState.Success(book)
                    } else {
                        _uiState.value = DetailUiState.Error
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error
            }
        }
    }

    fun resetExportStatus() {
        _exportStatus.value = ExportStatus.Idle
        _exportProgress.value = 0f
    }

    fun exportAudiobook(context: android.content.Context, destinationUri: android.net.Uri) {
        viewModelScope.launch {
            _exportStatus.value = ExportStatus.Exporting
            _exportProgress.value = 0f

            val book = (_uiState.value as? DetailUiState.Success)?.audiobook
            if (book == null) {
                _exportStatus.value = ExportStatus.Error("No audiobook loaded to export.")
                return@launch
            }

            try {
                val sourcePath = book.filePath

                // Use FFmpeg for a verified stream copy with faststart
                // This preserves all embedded metadata and chapters
                val outputParam = if (destinationUri.scheme == "content") {
                    com.arthenica.ffmpegkit.FFmpegKitConfig.getSafParameterForWrite(context, destinationUri)
                } else {
                    "\"${destinationUri.path}\""
                }

                val command = "-i \"$sourcePath\" -c copy -movflags +faststart $outputParam -y"
                Timber.d("Export FFmpeg command: $command")

                val success = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    com.arthenica.ffmpegkit.FFmpegKit.executeAsync(
                        command,
                        { session ->
                            val rc = session.returnCode
                            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(rc)) {
                                Timber.d("FFmpeg export completed successfully")
                                cont.resume(true, onCancellation = null)
                            } else {
                                Timber.e("FFmpeg export failed with code ${rc.value}")
                                cont.resume(false, onCancellation = null)
                            }
                        },
                        com.arthenica.ffmpegkit.LogCallback { log ->
                            Timber.d("FFmpeg export: ${log.message?.trimEnd()}")
                        },
                        com.arthenica.ffmpegkit.StatisticsCallback { stats ->
                            val timeMs = stats.time
                            if (book.durationMs > 0) {
                                _exportProgress.value = (timeMs.toFloat() / book.durationMs).coerceIn(0f, 1f)
                            }
                        }
                    )
                }

                if (success) {
                    _exportProgress.value = 1f
                    _exportStatus.value = ExportStatus.Success(destinationUri.toString())
                } else {
                    _exportStatus.value = ExportStatus.Error("FFmpeg stream copy failed. The file may be corrupt.")
                }
            } catch (e: Exception) {
                Timber.e(e, "FFmpeg export error")
                _exportStatus.value = ExportStatus.Error(e.message ?: "Unknown FFmpeg stream copy error.")
            }
        }
    }

    companion object {
        fun provideFactory(bookRepository: BookRepository, bookId: Int): ViewModelProvider.Factory = 
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AudiobookDetailViewModel(bookRepository, bookId) as T
                }
            }
    }
}
