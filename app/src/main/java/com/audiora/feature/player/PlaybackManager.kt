package com.audiora.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audiora.domain.model.Audiobook
import com.audiora.domain.model.Chapter
import com.audiora.domain.repository.BookRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

    private val playStateManager: PlayStateManager
        get() = (context.applicationContext as com.audiora.AudioraApplication).playStateManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    /**
     * Safe controller accessor that detects a dead controller and rebuilds it.
     *
     * Mirrors Voice's PlayerController.controller getter: if the controller was
     * disconnected (service process killed), it releases the old reference and
     * starts a new async connection. The caller must still null-check the result
     * since the new connection is asynchronous.
     */
    private val safeController: MediaController?
        get() {
            val c = controller
            if (c != null && !c.isConnected) {
                Timber.w("safeController: controller disconnected, rebuilding")
                c.release()
                controller = null
                initializeController()
                return null
            }
            return c
        }

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

    // Volume gain for LoudnessEnhancer (skip silence handled via ExoPlayer directly)
    private val volumeGain = VolumeGain()
    private var _audioSessionId: Int? = null
    private val shakeDetector = ShakeDetector(context)

    /**
     * Loads chapters for the given book.
     * Chapters are extracted at scan time and persisted as chaptersJson on the book,
     * so this is a synchronous, instant operation.
     */
    private fun loadChaptersForBook(book: Audiobook) {
        // 1. Check cached chapters JSON (extracted at scan time)
        if (!book.chaptersJson.isNullOrEmpty()) {
            val decoded = Chapter.deserializeList(book.chaptersJson)
            if (decoded.isNotEmpty()) {
                _chapters.value = decoded
                _currentChapterIndex.value = findChapterIndexForPosition(_currentPosition.value, decoded)
                return
            }
        }

        // 2. Fallback: single "Full Audiobook" entry for files without embedded chapters
        val fallbackDuration = if (book.durationMs > 0) book.durationMs else 3600000L
        _chapters.value = listOf(
            Chapter(
                title = book.title.ifEmpty { "Full Audiobook" },
                startMs = 0L,
                endMs = fallbackDuration,
                durationMs = fallbackDuration,
                index = 0
            )
        )
        _currentChapterIndex.value = 0
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
        
        // Track audio session ID from the underlying ExoPlayer for LoudnessEnhancer
        _audioSessionId = ExoPlayerInstance.player?.audioSessionId
            ?.takeUnless { it == C.AUDIO_SESSION_ID_UNSET }

        activeController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                _isPlaying.value = isPlayingChanged
                // Mirror Voice's PlayStateDelegatingListener: update play state on
                // onIsPlayingChanged in addition to onPlaybackStateChanged.
                // This handles the case where prepare() reaches STATE_READY before
                // play() is called — play() changes playWhenReady without a state
                // transition, so onPlaybackStateChanged doesn't fire.
                playStateManager.playState = if (isPlayingChanged)
                    PlayStateManager.PlayState.Playing
                else
                    PlayStateManager.PlayState.Paused
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
                // Consolidate play state from playbackState + playWhenReady.
                // Matches Voice's PlayStateDelegatingListener pattern.
                playStateManager.playState = when {
                    playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE ->
                        PlayStateManager.PlayState.Paused
                    activeController.playWhenReady ->
                        PlayStateManager.PlayState.Playing
                    else ->
                        PlayStateManager.PlayState.Paused
                }
                // End-of-chapter sleep timer — now reliable since per-chapter MediaItems
                // trigger STATE_ENDED when ClippingConfiguration end is reached.
                // Matches Voice's VoicePlayer.endOfChapterSleepTimerListener pattern.
                if (playbackState == Player.STATE_ENDED && _sleepTimerType.value == SleepTimerType.END_OF_CHAPTER) {
                    activeController.pause()
                    _sleepTimerType.value = SleepTimerType.OFF
                    Timber.d("EndOfChapter sleep timer: triggered at chapter end boundary")
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                _audioSessionId = audioSessionId.takeUnless { it == C.AUDIO_SESSION_ID_UNSET }
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                // Chapters are now handled via M4bChapterExtractor — ignore runtime metadata chapters
            }
        })
    }

    /**
     * Ensures the player is ready for the given book.
     *
     * This is a NO-OP if the player is already configured for this book,
     * eliminating the delay when navigating to the Now Playing screen.
     * Only prepares the player on cold start (first launch after app restart).
     */
    fun ensureBookLoaded(book: Audiobook) {
        // Always set the book state synchronously so PlayerScreen renders immediately
        // with the correct metadata. This must happen before any async checks.
        _currentBook.value = book
        _duration.value = book.durationMs
        loadChaptersForBook(book)
        _currentPosition.value = book.currentPositionMs

        val activeController = controller
        if (activeController == null) {
            Timber.d("ensureBookLoaded: controller not connected yet — screen renders from Room data, audio will start when controller connects")
            return
        }

        // Check if this book is already loaded in the player.
        // With per-chapter MediaItems, also compare item count to detect chapter changes.
        val currentUri = activeController.currentMediaItem?.localConfiguration?.uri?.toString()
        val bookFileUri = if (book.filePath.isNotEmpty()) book.filePath else null
        val expectedItemCount = MediaItemsBuilder.getChapters(book).size

        if (currentUri != null && bookFileUri != null && currentUri == bookFileUri &&
            activeController.mediaItemCount == expectedItemCount &&
            activeController.playbackState != Player.STATE_IDLE
        ) {
            Timber.d("ensureBookLoaded: book already loaded, skipping player init")
            return
        }

        Timber.d("ensureBookLoaded: cold start, preparing player")
        playBook(book)
    }

    fun playBook(book: Audiobook, autoPlay: Boolean = false) {
        // Capture previous book ID before overwriting (used for effectivePosition below)
        val previousBookId = _currentBook.value?.id

        // Set book state synchronously — PlayerScreen sees this immediately
        // instead of rendering "No Audiobook Loaded" for several frames.
        _currentBook.value = book
        _duration.value = book.durationMs
        loadChaptersForBook(book)

        scope.launch {
            // Save preceding book state before changing
            saveCurrentPositionToDb()

            // Calculate target position BEFORE touching player, so all async reads complete
            // before we enter the player critical path (matches Voice's approach of computing
            // the position before calling setMediaItems).
            // Per-book speed: if the book has a saved speed, use it; otherwise fall back to global default.
            val effectiveSpeed = if (book.playbackSpeed > 0f) book.playbackSpeed
                else settingsRepository?.getDefaultPlaybackSpeed()?.firstOrNull() ?: 1.0f
            _playbackSpeed.value = effectiveSpeed

            // Track last played timestamp on every playback start (matches Voice's
            // VoicePlayer.updateLastPlayedAt() which is called on every play()).
            val nowBook = book.copy(lastPlayedAt = System.currentTimeMillis())
            _currentBook.value = nowBook
            bookRepository.saveAudiobook(nowBook)

            val effectivePosition = if (previousBookId == book.id) {
                _currentPosition.value.coerceAtLeast(book.currentPositionMs)
            } else {
                book.currentPositionMs
            }

            val targetPos: Long
            if (effectivePosition in 1 until book.durationMs) {
                val autoRewindSecs = settingsRepository?.getAutoRewind()?.firstOrNull() ?: 3
                targetPos = (effectivePosition - autoRewindSecs * 1000L).coerceAtLeast(0L)
            } else {
                targetPos = 0L
            }
            _currentPosition.value = targetPos

            val activeController = controller
            if (activeController != null) {
                activeController.stop()

                // Build per-chapter MediaItems with ClippingConfiguration.
                // Each chapter gets its own MediaItem clipped to its time range,
                // enabling native chapter transitions and Android Auto support.
                val chapters = _chapters.value
                val targetChapterIndex = findChapterIndexForPosition(targetPos, chapters)
                    .coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
                val targetChapter = chapters.getOrNull(targetChapterIndex)
                val targetPositionInChapter = if (targetChapter != null) {
                    (targetPos - targetChapter.startMs).coerceIn(0L, targetChapter.durationMs)
                } else {
                    0L
                }
                _currentChapterIndex.value = targetChapterIndex

                val mediaItems = MediaItemsBuilder.buildMediaItems(book, context)
                activeController.setMediaItems(mediaItems, targetChapterIndex, targetPositionInChapter)
                activeController.prepare()

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
                activeController.setPlaybackSpeed(effectiveSpeed)

                // Apply per-book settings: skip silence and volume gain (matches Voice's setBook() pattern)
                if (book.skipSilence) {
                    ExoPlayerInstance.player?.skipSilenceEnabled = true
                }
                val gainDb = book.volumeGain
                if (gainDb > 0f) {
                    _audioSessionId?.let { volumeGain.setGain(gainDb, it) }
                }

                if (autoPlay) {
                    activeController.play()
                }
            } else {
                Timber.w("PlaybackManager: MediaController not online yet")
            }
        }
    }

    fun togglePlayPause() {
        val activeController = controller ?: return
        if (activeController.isPlaying) {
            activeController.pause()
            // Auto-rewind on pause. Matches Voice's VoicePlayer.setPlayWhenReady(false)
            // pattern: seek back by the configured rewind amount within the current item.
            scope.launch {
                val rewindSecs = settingsRepository?.getAutoRewind()?.firstOrNull() ?: 3
                if (rewindSecs > 0) {
                    // currentPosition is clipped to current chapter — rewind within it
                    val clipPos = activeController.currentPosition.coerceAtLeast(0L)
                    val rewindMs = (rewindSecs * 1000L).coerceAtMost(clipPos)
                    if (rewindMs > 0) {
                        val targetClipPos = clipPos - rewindMs
                        val itemIndex = activeController.currentMediaItemIndex
                        if (itemIndex != C.INDEX_UNSET) {
                            activeController.seekTo(itemIndex, targetClipPos)
                            val newAbsolutePos = (_currentPosition.value - rewindMs).coerceAtLeast(0L)
                            _currentPosition.value = newAbsolutePos
                            // Save corrected position after rewind (overrides the pre-rewind
                            // save that fired from onIsPlayingChanged(false)).
                            saveCurrentPositionToDb()
                        }
                    }
                }
            }
        } else {
            val activeBook = _currentBook.value
            if (activeBook != null && activeController.mediaItemCount == 0) {
                playBook(activeBook, autoPlay = true)
            } else {
                activeController.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val activeController = controller ?: return
        // With per-chapter MediaItems, map absolute position to (chapterIndex, offsetInChapter).
        val chapters = _chapters.value
        val chapterIndex = findChapterIndexForPosition(positionMs, chapters)
            .coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
        val chapter = chapters.getOrNull(chapterIndex)
        val offsetInChapter = if (chapter != null) {
            (positionMs - chapter.startMs).coerceIn(0L, chapter.durationMs)
        } else {
            0L
        }
        activeController.seekTo(chapterIndex, offsetInChapter)
        _currentPosition.value = positionMs
        _currentChapterIndex.value = chapterIndex
        scope.launch {
            saveCurrentPositionToDb()
        }
    }

    fun setSpeed(speed: Float) {
        val activeController = controller ?: return
        activeController.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
        // Persist per-book speed (matches Voice's VoicePlayer.setPlaybackSpeed → updateBook pattern)
        scope.launch {
            val book = _currentBook.value ?: return@launch
            if (book.playbackSpeed != speed) {
                val updated = book.copy(playbackSpeed = speed)
                _currentBook.value = updated
                bookRepository.saveAudiobook(updated)
            }
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        // Send command cross-process via MediaController (matches Voice's CustomCommand pattern).
        // Also set directly via ExoPlayerInstance as a safety net for cold-start before controller connects.
        controller?.sendPlaybackCommand(PlaybackCommand.SetSkipSilence(enabled))
        scope.launch {
            val book = _currentBook.value ?: return@launch
            val updated = book.copy(skipSilence = enabled)
            _currentBook.value = updated
            bookRepository.saveAudiobook(updated)
        }
    }

    fun setVolumeGain(gainDb: Float) {
        // Send command cross-process via MediaController (matches Voice's CustomCommand pattern).
        controller?.sendPlaybackCommand(PlaybackCommand.SetGain(gainDb))
        // Also apply locally via ExoPlayerInstance as safety net before controller connects
        val sessionId = _audioSessionId
        if (sessionId != null) {
            volumeGain.setGain(gainDb, sessionId)
        }
        scope.launch {
            val book = _currentBook.value ?: return@launch
            val updated = book.copy(volumeGain = gainDb)
            _currentBook.value = updated
            bookRepository.saveAudiobook(updated)
        }
    }

    fun skipForward() {
        val activeController = controller ?: return
        scope.launch {
            val skipAmt = settingsRepository?.getSkipAmount()?.firstOrNull() ?: 15
            // currentPosition returns position within the clipped MediaItem (chapter offset)
            val currentClipPos = activeController.currentPosition
            val currentItemIndex = activeController.currentMediaItemIndex
                .takeUnless { it == C.INDEX_UNSET } ?: return@launch
            val chapters = _chapters.value
            val chapter = chapters.getOrNull(currentItemIndex) ?: return@launch
            val absolutePos = chapter.startMs + currentClipPos
            val targetAbsolutePos = (absolutePos + skipAmt * 1000L).coerceAtMost(_duration.value.coerceAtLeast(absolutePos))
            seekTo(targetAbsolutePos)
        }
    }

    fun skipBackward() {
        val activeController = controller ?: return
        scope.launch {
            val skipAmt = settingsRepository?.getSkipAmount()?.firstOrNull() ?: 15
            // currentPosition returns position within the clipped MediaItem (chapter offset)
            val currentClipPos = activeController.currentPosition
            val currentItemIndex = activeController.currentMediaItemIndex
                .takeUnless { it == C.INDEX_UNSET } ?: return@launch
            val chapters = _chapters.value
            val chapter = chapters.getOrNull(currentItemIndex) ?: return@launch
            val absolutePos = chapter.startMs + currentClipPos
            val targetAbsolutePos = (absolutePos - skipAmt * 1000L).coerceAtLeast(0L)
            seekTo(targetAbsolutePos)
        }
    }

    private fun startPositionTracker() {
        // Observe play state with collectLatest so the tracking loop automatically cancels
        // when paused and restarts when playing resumes.
        // Matches Voice's PositionUpdater: playStateFlow → distinctUntilChanged → collectLatest.
        scope.launch {
            playStateManager.playStateFlow
                .collectLatest { playState ->
                    if (playState != PlayStateManager.PlayState.Playing) return@collectLatest

                    // Counter for periodic DB saves (~2min at 500ms interval)
                    var saveCounter = 0
                    while (isActive) {
                        delay(500)
                        val activeController = controller
                        if (activeController == null || !activeController.isPlaying) continue

                        // Read current media item index to determine the active chapter.
                        // With per-chapter MediaItems, currentMediaItemIndex maps directly to
                        // the chapter index.
                        val currentMediaItemIndex = activeController.currentMediaItemIndex
                            .takeUnless { it == C.INDEX_UNSET } ?: continue
                        val chapters = _chapters.value
                        val currentChapter = chapters.getOrNull(currentMediaItemIndex) ?: continue

                        // Position within the clipped MediaItem (chapter offset)
                        val clippedPosition = activeController.currentPosition
                            .takeUnless { it == C.TIME_UNSET } ?: continue

                        // Compute absolute position in the book file
                        val absolutePosition = currentChapter.startMs + clippedPosition

                        // Update position StateFlow for PlayerScreen slider
                        _currentPosition.value = absolutePosition
                        _currentChapterIndex.value = currentMediaItemIndex
                        _duration.value = if (activeController.duration > 0) activeController.duration else _duration.value

                        // Sleep Timer Check (timed only — EOC is handled via STATE_ENDED callback)
                        val currentType = _sleepTimerType.value
                        if (currentType != SleepTimerType.OFF && currentType != SleepTimerType.END_OF_CHAPTER) {
                            val remaining = _sleepTimerRemaining.value
                            if (remaining > 0) {
                                val nextRemaining = (remaining - 500L).coerceAtLeast(0L)
                                _sleepTimerRemaining.value = nextRemaining

                                // Fade-out: gradually lower volume during last 30 seconds.
                                // Matches Voice's SleepTimerImpl.updateVolume() with FastOutSlowInInterpolator.
                                val fadeOutDuration = 30_000L
                                if (remaining < fadeOutDuration) {
                                    val fraction = remaining.toFloat() / fadeOutDuration.toFloat()
                                    // FastOutSlowIn approximation: volume = 1 - (1 - fraction)^2
                                    val volume = 1f - (1f - fraction) * (1f - fraction)
                                    activeController.volume = volume.coerceIn(0f, 1f)
                                }

                                if (nextRemaining == 0L) {
                                    activeController.volume = 1f // Reset volume first
                                    activeController.pause()
                                    _sleepTimerType.value = SleepTimerType.OFF

                                    // Shake-to-reset: wait up to 30s for a shake gesture to resume.
                                    // Matches Voice's SleepTimerImpl.detectShakeWithTimeout().
                                    scope.launch {
                                        val shakeTimeoutMs = 30_000L
                                        val shakeDetected = kotlinx.coroutines.withTimeoutOrNull(shakeTimeoutMs) {
                                            shakeDetector.detect()
                                        } ?: false
                                        if (shakeDetected) {
                                            Timber.d("Sleep timer: shake detected, resuming playback")
                                            activeController.play()
                                            // Restart the same timer duration
                                            val originalTotal = when (currentType) {
                                                SleepTimerType.MIN_5 -> 5L
                                                SleepTimerType.MIN_10 -> 10L
                                                SleepTimerType.MIN_15 -> 15L
                                                SleepTimerType.MIN_30 -> 30L
                                                SleepTimerType.MIN_45 -> 45L
                                                SleepTimerType.MIN_60 -> 60L
                                                else -> return@launch
                                            }
                                            _sleepTimerType.value = currentType
                                            _sleepTimerRemaining.value = originalTotal * 60 * 1000L
                                        }
                                    }
                                }
                            }
                        }
                        // NOTE: EndOfChapter sleep timer no longer polled here.
                        // With per-chapter MediaItems, ClippingConfiguration end triggers
                        // STATE_ENDED which is handled in setupControllerListener().

                        // Periodically write state to DB (~every 2 minutes)
                        saveCounter++
                        if (saveCounter >= 240) {
                            saveCounter = 0
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
        val chapters = _chapters.value
        if (index in chapters.indices) {
            val activeController = controller
            if (activeController != null) {
                // Seek to chapter start atomically using native MediaItem navigation.
                activeController.seekTo(index, 0L)
            }
            _currentPosition.value = chapters[index].startMs
            _currentChapterIndex.value = index
            scope.launch { saveCurrentPositionToDb() }
        }
    }

    fun skipToNextChapter() {
        val activeController = controller ?: return
        val nextIndex = activeController.nextMediaItemIndex
        val chapters = _chapters.value
        if (nextIndex != C.INDEX_UNSET && nextIndex in chapters.indices) {
            activeController.seekTo(nextIndex, 0L)
            _currentChapterIndex.value = nextIndex
            _currentPosition.value = chapters[nextIndex].startMs
            scope.launch { saveCurrentPositionToDb() }
        }
    }

    fun skipToPreviousChapter() {
        val activeController = controller ?: return
        val chapters = _chapters.value
        val currentIndex = _currentChapterIndex.value
        if (currentIndex in chapters.indices) {
            // If more than 3s into current chapter, restart it
            val clippedPosition = activeController.currentPosition
            if (clippedPosition > 3000L) {
                activeController.seekTo(currentIndex, 0L)
                _currentPosition.value = chapters[currentIndex].startMs
            } else {
                val prevIndex = currentIndex - 1
                if (prevIndex in chapters.indices) {
                    activeController.seekTo(prevIndex, 0L)
                    _currentChapterIndex.value = prevIndex
                    _currentPosition.value = chapters[prevIndex].startMs
                } else {
                    activeController.seekTo(0, 0L)
                    _currentPosition.value = 0L
                }
            }
            scope.launch { saveCurrentPositionToDb() }
        }
    }

    fun stopPlayback() {
        saveCurrentPositionToDb()
        volumeGain.reset()
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

    fun getCurrentBookId(): Int? = _currentBook.value?.id

    fun getSkipSilence(): Boolean = _currentBook.value?.skipSilence ?: false

    fun getVolumeGain(): Float = _currentBook.value?.volumeGain ?: 0f

    fun release() {
        saveCurrentPositionToDb()
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
