package com.audiora.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audiora.data.local.M4bChapterExtractor
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter
import com.audiora.domain.repository.BookRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

enum class SleepTimerType(val label: String) {
    OFF("Off"),
    MIN_5("5 Minutes"),
    MIN_10("10 Minutes"),
    MIN_15("15 Minutes"),
    MIN_30("30 Minutes"),
    MIN_45("45 Minutes"),
    MIN_60("60 Minutes"),
    END_OF_CHAPTER("End of Chapter")
}

@OptIn(UnstableApi::class)
class PlaybackManager(
    private val context: Context,
    private val bookRepository: BookRepository
) {
    private val settingsRepository: com.audiora.domain.repository.SettingsRepository? by lazy {
        (context.applicationContext as? com.audiora.AudioraApplication)?.settingsRepository
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI state flows
    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Chapter Navigation states
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(-1)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    /**
     * Loads chapters for the given book, trying in order:
     * 1. Cached chapters from DB (chaptersJson)
     * 2. M4B chapter extraction from the file
     * 3. Single "Full Audiobook" entry as fallback
     */
    private fun loadChaptersForBook(book: Audiobook) {
        // 1. Check cached chapters JSON first
        if (!book.chaptersJson.isNullOrEmpty()) {
            val decoded = Chapter.deserializeList(book.chaptersJson)
            if (decoded.isNotEmpty()) {
                _chapters.value = decoded
                _currentChapterIndex.value = findChapterIndexForPosition(_currentPosition.value, decoded)
                return
            }
        }

        scope.launch(Dispatchers.IO) {
            val extracted = try {
                val isContentUri = book.filePath.startsWith("content://")
                if (isContentUri) {
                    M4bChapterExtractor.extractFromUri(context, android.net.Uri.parse(book.filePath), book.durationMs)
                } else if (book.filePath.isNotEmpty()) {
                    M4bChapterExtractor.extractFromFile(context, book.filePath, book.durationMs)
                } else {
                    emptyList()
                }
            } catch (e: Throwable) {
                Timber.e(e, "PlaybackManager: Error extracting chapters")
                emptyList()
            }

            val chapters = if (extracted.isNotEmpty()) {
                // Persist extracted chapters to DB for future opens
                try {
                    if (book.id > 0) {
                        bookRepository.updateBookChapters(context, book.id, extracted)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "PlaybackManager: Error persisting extracted chapters")
                }
                extracted
            } else {
                // 3. Fallback: single entry covering the whole audiobook
                val duration = if (book.durationMs > 0) book.durationMs else 3600000L
                listOf(
                    Chapter(
                        title = book.title.ifEmpty { "Full Audiobook" },
                        startMs = 0L,
                        endMs = duration,
                        durationMs = duration,
                        index = 0
                    )
                )
            }

            _chapters.value = chapters
            _currentChapterIndex.value = findChapterIndexForPosition(_currentPosition.value, chapters)
        }
    }

    private fun findChapterIndexForPosition(positionMs: Long, list: List<Chapter>): Int {
        if (list.isEmpty()) return -1
        for (i in list.indices) {
            val ch = list[i]
            if (positionMs >= ch.startMs && positionMs < ch.endMs) {
                return i
            }
        }
        return if (positionMs >= list.last().endMs) list.lastIndex else 0
    }

    private fun updateCurrentChapterIndex(positionMs: Long) {
        _currentChapterIndex.value = findChapterIndexForPosition(positionMs, _chapters.value)
    }

    // Sleep Timer state flows
    private val _sleepTimerType = MutableStateFlow(SleepTimerType.OFF)
    val sleepTimerType: StateFlow<SleepTimerType> = _sleepTimerType.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

    fun startSleepTimer(type: SleepTimerType) {
        _sleepTimerType.value = type
        _sleepTimerRemaining.value = when (type) {
            SleepTimerType.OFF -> 0L
            SleepTimerType.MIN_5 -> 5 * 60 * 1000L
            SleepTimerType.MIN_10 -> 10 * 60 * 1000L
            SleepTimerType.MIN_15 -> 15 * 60 * 1000L
            SleepTimerType.MIN_30 -> 30 * 60 * 1000L
            SleepTimerType.MIN_45 -> 45 * 60 * 1000L
            SleepTimerType.MIN_60 -> 60 * 60 * 1000L
            SleepTimerType.END_OF_CHAPTER -> 0L
        }
    }

    fun cancelSleepTimer() {
        _sleepTimerType.value = SleepTimerType.OFF
        _sleepTimerRemaining.value = 0L
    }

    init {
        val isRobolectric = android.os.Build.FINGERPRINT == "robolectric" || try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (e: ClassNotFoundException) {
            false
        }
        if (!isRobolectric) {
            initializeController()
            startPositionTracker()
        } else {
            Timber.d("PlaybackManager: Robolectric unit test mode detected. Skipping MediaController setup.")
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setupControllerListener()
                Timber.d("PlaybackManager: MediaController established successfully.")
            } catch (e: Exception) {
                Timber.e(e, "PlaybackManager: Controller future initialization error")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val activeController = controller ?: return
        
        // Sync initial state
        _isPlaying.value = activeController.isPlaying
        _playbackSpeed.value = activeController.playbackParameters.speed
        _duration.value = if (activeController.duration > 0) activeController.duration else 0L
        
        activeController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                _isPlaying.value = isPlayingChanged
                if (!isPlayingChanged) {
                    saveCurrentPositionToDb()
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isPlaying.value = activeController.isPlaying
                _duration.value = if (activeController.duration > 0) activeController.duration else 0L
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                // Chapters are now handled via M4bChapterExtractor — ignore runtime metadata chapters
            }
        })
    }

    fun playBook(book: Audiobook) {
        scope.launch {
            // Save preceding book state before changing
            saveCurrentPositionToDb()

            _currentBook.value = book
            _duration.value = book.durationMs
            loadChaptersForBook(book)

            // Set up fallback stream if filePath is missing
            val uriToPlay = if (book.filePath.isNotEmpty()) {
                book.filePath
            } else {
                // High-fidelity public domain stream
                "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            }

            val activeController = controller
            if (activeController != null) {
                activeController.stop()
                val mediaItem = MediaItem.fromUri(uriToPlay)
                activeController.setMediaItem(mediaItem)
                activeController.prepare()
                
                // Get playback speed default and update local state
                val defaultSpeed = settingsRepository?.getDefaultPlaybackSpeed()?.firstOrNull() ?: 1.0f
                _playbackSpeed.value = defaultSpeed

                // Seek to saved position with auto-rewind if applicable
                if (book.currentPositionMs > 0 && book.currentPositionMs < book.durationMs) {
                    val autoRewindSecs = settingsRepository?.getAutoRewind()?.firstOrNull() ?: 3
                    val targetPos = (book.currentPositionMs - autoRewindSecs * 1000L).coerceAtLeast(0L)
                    activeController.seekTo(targetPos)
                    _currentPosition.value = targetPos
                } else {
                    _currentPosition.value = 0L
                }

                // Apply defaults: sleep timer
                val sleepDefault = settingsRepository?.getSleepTimerDefault()?.firstOrNull() ?: 30
                if (sleepDefault > 0) {
                    val sleepType = when (sleepDefault) {
                        5 -> SleepTimerType.MIN_5
                        10 -> SleepTimerType.MIN_10
                        15 -> SleepTimerType.MIN_15
                        30 -> SleepTimerType.MIN_30
                        45 -> SleepTimerType.MIN_45
                        60 -> SleepTimerType.MIN_60
                        else -> SleepTimerType.OFF
                    }
                    if (sleepType != SleepTimerType.OFF) {
                        startSleepTimer(sleepType)
                    }
                }

                // Set speed & play
                activeController.setPlaybackSpeed(defaultSpeed)
                activeController.play()
            } else {
                Timber.w("PlaybackManager: MediaController not online yet")
            }
        }
    }

    fun togglePlayPause() {
        val activeController = controller ?: return
        if (activeController.isPlaying) {
            activeController.pause()
        } else {
            val activeBook = _currentBook.value
            if (activeBook != null && activeController.mediaItemCount == 0) {
                playBook(activeBook)
            } else {
                scope.launch {
                    val autoRewindSecs = settingsRepository?.getAutoRewind()?.firstOrNull() ?: 3
                    if (autoRewindSecs > 0) {
                        val currentPos = activeController.currentPosition
                        val targetPos = (currentPos - autoRewindSecs * 1000L).coerceAtLeast(0L)
                        activeController.seekTo(targetPos)
                        _currentPosition.value = targetPos
                    }
                    activeController.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val activeController = controller ?: return
        activeController.seekTo(positionMs)
        _currentPosition.value = positionMs
        updateCurrentChapterIndex(positionMs)
        scope.launch {
            saveCurrentPositionToDb()
        }
    }

    fun setSpeed(speed: Float) {
        val activeController = controller ?: return
        activeController.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun skipForward() {
        val activeController = controller ?: return
        scope.launch {
            val skipAmt = settingsRepository?.getSkipAmount()?.firstOrNull() ?: 15
            val currentPos = activeController.currentPosition
            val maxDur = if (activeController.duration > 0) activeController.duration else _duration.value
            val targetPos = (currentPos + skipAmt * 1000L).coerceAtMost(maxDur)
            activeController.seekTo(targetPos)
            _currentPosition.value = targetPos
            saveCurrentPositionToDb()
        }
    }

    fun skipBackward() {
        val activeController = controller ?: return
        scope.launch {
            val skipAmt = settingsRepository?.getSkipAmount()?.firstOrNull() ?: 15
            val currentPos = activeController.currentPosition
            val targetPos = (currentPos - skipAmt * 1000L).coerceAtLeast(0L)
            activeController.seekTo(targetPos)
            _currentPosition.value = targetPos
            saveCurrentPositionToDb()
        }
    }

    private fun startPositionTracker() {
        scope.launch {
            var counter = 0
            while (isActive) {
                delay(500)
                val activeController = controller
                if (activeController != null && activeController.isPlaying) {
                    val currentPos = activeController.currentPosition
                    _currentPosition.value = currentPos
                    _duration.value = if (activeController.duration > 0) activeController.duration else _duration.value
                    
                    // Update current chapter index
                    updateCurrentChapterIndex(currentPos)
                    
                    // Sleep Timer Check
                    val currentType = _sleepTimerType.value
                    if (currentType != SleepTimerType.OFF && currentType != SleepTimerType.END_OF_CHAPTER) {
                        val remaining = _sleepTimerRemaining.value
                        if (remaining > 0) {
                            val nextRemaining = (remaining - 500L).coerceAtLeast(0L)
                            _sleepTimerRemaining.value = nextRemaining
                            if (nextRemaining == 0L) {
                                activeController.pause()
                                _sleepTimerType.value = SleepTimerType.OFF
                            }
                        }
                    } else if (currentType == SleepTimerType.END_OF_CHAPTER) {
                        val currentChapters = _chapters.value
                        val currentIdx = _currentChapterIndex.value
                        if (currentChapters.isNotEmpty() && currentIdx in currentChapters.indices) {
                            val chapter = currentChapters[currentIdx]
                            if (currentPos >= chapter.endMs - 1200L) { // Trigger within last ~1.2 seconds of chapter
                                activeController.pause()
                                _sleepTimerType.value = SleepTimerType.OFF
                            }
                        }
                    }

                    // Periodically (approx. every 10 seconds) write the updated state into the DB
                    counter++
                    if (counter >= 20) {
                        counter = 0
                        saveCurrentPositionToDb()
                    }
                }
            }
        }
    }

    fun saveCurrentPositionToDb() {
        val activeBook = _currentBook.value ?: return
        val pos = _currentPosition.value
        val dur = if (_duration.value > 0) _duration.value else activeBook.durationMs
        
        // Only update if there is actual progress difference
        if (Math.abs(activeBook.currentPositionMs - pos) > 1000) {
            val isCompleted = pos >= dur - 5000 // Treat as completed if within 5s of end
            val updated = activeBook.copy(
                currentPositionMs = pos,
                durationMs = dur,
                completed = isCompleted
            )
            _currentBook.value = updated
            scope.launch(Dispatchers.IO) {
                try {
                    bookRepository.saveAudiobook(updated)
                } catch (e: Exception) {
                    Timber.e(e, "Error saving position to database")
                }
            }
        }
    }

    fun seekToChapter(index: Int) {
        val list = _chapters.value
        if (index in list.indices) {
            val chapter = list[index]
            seekTo(chapter.startMs)
            _currentChapterIndex.value = index
        }
    }

    fun skipToNextChapter() {
        val list = _chapters.value
        val currentIndex = _currentChapterIndex.value
        if (list.isNotEmpty()) {
            val nextIndex = currentIndex + 1
            if (nextIndex in list.indices) {
                seekToChapter(nextIndex)
            } else {
                seekTo(list.last().endMs)
            }
        }
    }

    fun skipToPreviousChapter() {
        val list = _chapters.value
        val currentIndex = _currentChapterIndex.value
        if (list.isNotEmpty() && currentIndex in list.indices) {
            val chapter = list[currentIndex]
            val relativePos = _currentPosition.value - chapter.startMs
            if (relativePos > 3000L) {
                seekTo(chapter.startMs)
            } else {
                val prevIndex = currentIndex - 1
                if (prevIndex in list.indices) {
                    seekToChapter(prevIndex)
                } else {
                    seekTo(0)
                }
            }
        }
    }

    fun stopPlayback() {
        saveCurrentPositionToDb()
        val activeController = controller
        if (activeController != null) {
            activeController.stop()
            activeController.clearMediaItems()
        }
        _currentBook.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _chapters.value = emptyList()
        _currentChapterIndex.value = -1
        cancelSleepTimer()
    }

    fun release() {
        saveCurrentPositionToDb()
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
