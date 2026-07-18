package com.audiora

import android.app.Application
import androidx.room.Room
import com.audiora.data.local.AppDatabase
import com.audiora.data.processing.FFmpegService
import com.audiora.data.processing.FfmpegBinaryManager
import com.audiora.data.processing.TempFileManager
import com.audiora.data.processing.executor.ProcessExecutor
import com.audiora.data.processing.parser.FFprobeJsonParser
import com.audiora.data.processing.parser.ProgressParser
import com.audiora.data.repository.BookRepositoryImpl
import com.audiora.data.repository.FolderRepositoryImpl
import com.audiora.data.repository.SettingsRepositoryImpl
import com.audiora.domain.repository.BookRepository
import com.audiora.domain.repository.FolderRepository
import com.audiora.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioraApplication : Application() {

    // Central Dependency Injection Singletons (Using manual Constructor Injection for absolute robustness)
    lateinit var database: AppDatabase
        private set

    lateinit var bookRepository: BookRepository
        private set

    lateinit var folderRepository: FolderRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var playbackManager: com.audiora.feature.player.PlaybackManager
        private set

    val playStateManager = com.audiora.feature.player.PlayStateManager()

    // App-scoped coroutine scope for background tasks that must outlive any screen
    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── FFmpeg processing singletons ───
    lateinit var ffmpegService: FFmpegService
        private set

    lateinit var ffmpegBinaryManager: FfmpegBinaryManager
        private set

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            "media_playback",
            "Playback",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Initialize Timber Logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release, we could plant a crash reporting tree
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // No-op for release or standard console print
                }
            })
        }

        Timber.d("Audiora App initialized successfully.")

        // 2. Initialize Database and Repositories
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "audiora_database"
        ).fallbackToDestructiveMigration() // Simple, robust migrations for V1 prototyping
         .build()

        // Initialize FFmpeg processing layer
        ffmpegBinaryManager = FfmpegBinaryManager(applicationContext)
        val processExecutor = ProcessExecutor()
        val tempFileManager = TempFileManager(applicationContext)
        val ffprobeJsonParser = FFprobeJsonParser()
        val progressParser = ProgressParser()
        ffmpegService = FFmpegService(
            binaryManager = ffmpegBinaryManager,
            processExecutor = processExecutor,
            tempFileManager = tempFileManager,
            ffprobeJsonParser = ffprobeJsonParser,
            progressParser = progressParser,
        )

        bookRepository = BookRepositoryImpl(
            bookDao = database.bookDao(),
            bookmarkDao = database.bookmarkDao(),
            appScope = appScope,
            ffmpegService = ffmpegService,
        )
        settingsRepository = SettingsRepositoryImpl(applicationContext)
        folderRepository = FolderRepositoryImpl(applicationContext, database.folderDao(), database.bookDao(), appScope)
        playbackManager = com.audiora.feature.player.PlaybackManager(applicationContext, bookRepository)

        // 3. Auto-rescan configured folders on app startup
        appScope.launch {
            try {
                folderRepository.rescanAllFolders()
            } catch (e: Exception) {
                Timber.e(e, "Auto-rescan failure on startup")
            }
        }
    }
}
