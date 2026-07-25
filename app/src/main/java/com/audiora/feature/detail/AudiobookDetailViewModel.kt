package com.audiora.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiora.domain.model.Audiobook
import com.audiora.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed class ExportStatus {
    object Idle : ExportStatus()
    object Exporting : ExportStatus()
    data class Success(val destinationUri: String) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val path: String) : SaveStatus()
    data class Error(val message: String) : SaveStatus()
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

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    val isInCache: StateFlow<Boolean> = _uiState.map { state ->
        if (state is DetailUiState.Success) {
            val path = state.audiobook.filePath
            // Cache paths are under /data/ and contain /cache/
            path.startsWith("/data/") && path.contains("/cache/")
        } else false
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

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

    fun resetSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    /**
     * Moves the audiobook file from app cache to Downloads/Audiora/ for permanent storage.
     * Uses MediaStore on API 29+ and direct file I/O on older versions.
     */
    fun saveToDownloads(context: android.content.Context) {
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving

            val book = (_uiState.value as? DetailUiState.Success)?.audiobook
            if (book == null) {
                _saveStatus.value = SaveStatus.Error("No audiobook loaded.")
                return@launch
            }

            val sourceFile = java.io.File(book.filePath)
            if (!sourceFile.exists()) {
                _saveStatus.value = SaveStatus.Error("Audiobook file not found in cache.")
                return@launch
            }

            try {
                val fileName = "${book.title.replace("[^a-zA-Z0-9_\\- ]".toRegex(), "_").take(80)}_${System.currentTimeMillis()}.m4b"
                val newPath = withContext(Dispatchers.IO) {
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        saveViaMediaStore(context, sourceFile, fileName)
                    } else {
                        saveViaDirectFile(context, sourceFile, fileName)
                    }
                }

                // Update the DB with the new file path
                val updatedBook = book.copy(filePath = newPath)
                bookRepository.saveAudiobook(updatedBook)
                _saveStatus.value = SaveStatus.Success(newPath)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save audiobook to Downloads/Audiora")
                _saveStatus.value = SaveStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun saveViaMediaStore(context: android.content.Context, source: java.io.File, fileName: String): String {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/mp4")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Audiora")
        }
        val outputUri = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
        ) ?: throw java.io.IOException("MediaStore insert returned null")

        context.contentResolver.openOutputStream(outputUri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw java.io.IOException("Could not open output stream")

        val updateValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        }
        context.contentResolver.update(outputUri, updateValues, null, null)

        // Delete source cache file — this is a move
        source.delete()

        return outputUri.toString()
    }

    private fun saveViaDirectFile(context: android.content.Context, source: java.io.File, fileName: String): String {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val audioraDir = java.io.File(downloadsDir, "Audiora")
        if (!audioraDir.exists()) audioraDir.mkdirs()

        val destFile = java.io.File(audioraDir, fileName)
        if (!source.renameTo(destFile)) {
            // renameTo failed (cross-partition), fallback to copy+delete
            java.io.FileOutputStream(destFile).use { out ->
                source.inputStream().use { inp -> inp.copyTo(out) }
            }
            source.delete()
        }
        return destFile.absolutePath
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
