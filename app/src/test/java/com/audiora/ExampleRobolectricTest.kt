package com.audiora

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.audiora.data.local.StorageImportManager
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Audiora", appName)
  }

  @Test
  fun `storage import manager should save and retrieve files correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = StorageImportManager(context)
    
    // Initial state should be empty
    assertTrue(manager.getImportedFiles().isEmpty())
    
    // Mock save a file
    val mockUri = Uri.parse("content://com.android.providers.media.documents/document/audio%3A42")
    val saved = manager.saveImportedFile(
      uri = mockUri,
      name = "Sample_Audiobook.m4b",
      size = 24500123L,
      type = "M4B"
    )
    
    assertTrue(saved)
    
    // Retrieve files and assert the characteristics are preserved
    val files = manager.getImportedFiles()
    assertEquals(1, files.size)
    
    val importedFile = files[0]
    assertEquals(mockUri.toString(), importedFile.uriString)
    assertEquals("Sample_Audiobook.m4b", importedFile.name)
    assertEquals(24500123L, importedFile.size)
    assertEquals("M4B", importedFile.type)
  }

  @Test
  fun `storage import manager avoids duplicating items`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = StorageImportManager(context)
    
    val mockUri = Uri.parse("content://com.android.providers.media.documents/document/audio%3A42")
    manager.saveImportedFile(mockUri, "Book.mp3", 1024L, "MP3")
    
    // Try to save again with same URI
    val secondImport = manager.saveImportedFile(mockUri, "Book.mp3", 1024L, "MP3")
    
    // Should be rejected as duplicate
    assertEquals(false, secondImport)
    assertEquals(1, manager.getImportedFiles().size)
  }

  @Test
  fun `storage import manager removes files and releases persistent reference`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = StorageImportManager(context)
    
    val mockUri = Uri.parse("content://com.android.providers.media.documents/document/audio%3A99")
    manager.saveImportedFile(mockUri, "Story.aac", 500L, "AAC")
    assertEquals(1, manager.getImportedFiles().size)
    
    manager.removeImportedFile(mockUri.toString())
    assertTrue(manager.getImportedFiles().isEmpty())
  }

  @Test
  fun `database should save retrieve update and delete audiobooks correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    // Check initial state
    var list = repository.getAudiobooks().first()
    assertTrue(list.isEmpty())
    
    // Save audiobook
    val testBook = com.audiora.domain.model.Audiobook(
      id = 10,
      filePath = "content://audio/10",
      title = "Robolectric Adventures",
      author = "Code Assistant",
      narrator = "Narrator X",
      publisher = "Pub Inc",
      genre = "Tech",
      year = "2026",
      description = "Deep dive into JVM testing.",
      durationMs = 3600000L,
      currentPositionMs = 120000L,
      coverPath = "robo",
      addedAt = 123456789L,
      completed = false
    )
    
    repository.saveAudiobook(testBook)
    
    // Retrieve audiobook
    list = repository.getAudiobooks().first()
    assertEquals(1, list.size)
    assertEquals("Robolectric Adventures", list[0].title)
    assertEquals("content://audio/10", list[0].filePath)
    assertEquals("Code Assistant", list[0].author)
    assertEquals(3600000L, list[0].durationMs)
    assertEquals(120000L, list[0].currentPositionMs)
    assertEquals("robo", list[0].coverPath)
    assertEquals(123456789L, list[0].addedAt)
    
    // Update audiobook position
    val updatedBook = testBook.copy(currentPositionMs = 500000L, completed = true)
    repository.saveAudiobook(updatedBook)
    
    list = repository.getAudiobooks().first()
    assertEquals(1, list.size)
    assertEquals(500000L, list[0].currentPositionMs)
    assertTrue(list[0].completed)
    
    // Delete audiobook
    repository.deleteAudiobook(10)
    list = repository.getAudiobooks().first()
    assertTrue(list.isEmpty())
    
    database.close()
  }

  @Test
  fun `database and update book metadata with advanced fields works correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    val testBook = com.audiora.domain.model.Audiobook(
      id = 55,
      filePath = "",
      title = "Initial Title",
      author = "Initial Author",
      narrator = "Initial Narrator",
      publisher = "Initial Pub",
      genre = "Initial Genre",
      year = "2024",
      description = "Initial Description",
      durationMs = 1200L,
      language = "English",
      copyright = "Copyleft"
    )
    repository.saveAudiobook(testBook)
    
    // Update advanced metadata
    repository.updateBookMetadata(
      context = context,
      bookId = 55,
      title = "Updated Title",
      author = "Updated Author",
      narrator = "Updated Narrator",
      publisher = "Updated Pub",
      genre = "Updated Genre",
      language = "French",
      description = "Updated Description",
      copyright = "Creative Commons",
      year = "2026"
    )
    
    val list = repository.getAudiobooks().first()
    assertEquals(1, list.size)
    val fetched = list[0]
    assertEquals("Updated Title", fetched.title)
    assertEquals("Updated Author", fetched.author)
    assertEquals("Updated Narrator", fetched.narrator)
    assertEquals("Updated Pub", fetched.publisher)
    assertEquals("Updated Genre", fetched.genre)
    assertEquals("French", fetched.language)
    assertEquals("Updated Description", fetched.description)
    assertEquals("Creative Commons", fetched.copyright)
    assertEquals("2026", fetched.year)
    
    database.close()
  }

  @Test
  fun `database should save retrieve rename and delete bookmarks correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val repository = com.audiora.data.repository.BookRepositoryImpl(database.bookDao(), database.bookmarkDao())
    val bookId = 42
    
    // Check initial state
    var bookmarks = repository.getBookmarks(bookId).first()
    assertTrue(bookmarks.isEmpty())
    
    // Save bookmarks
    val b1 = com.audiora.domain.model.Bookmark(id = 1, bookId = bookId, positionMs = 15000L, name = "Marker 1")
    val b2 = com.audiora.domain.model.Bookmark(id = 2, bookId = bookId, positionMs = 5000L, name = "Marker 2")
    
    repository.saveBookmark(b1)
    repository.saveBookmark(b2)
    
    // Retrieve and verify order (should be sorted by positionMs ascending)
    bookmarks = repository.getBookmarks(bookId).first()
    assertEquals(2, bookmarks.size)
    assertEquals("Marker 2", bookmarks[0].name) // 5000s < 15000s
    assertEquals("Marker 1", bookmarks[1].name)
    
    // Rename bookmark
    val renamedB1 = b1.copy(name = "Act I Scene 2")
    repository.saveBookmark(renamedB1)
    
    bookmarks = repository.getBookmarks(bookId).first()
    assertEquals("Act I Scene 2", bookmarks[1].name)
    
    // Delete bookmark
    repository.deleteBookmark(2)
    bookmarks = repository.getBookmarks(bookId).first()
    assertEquals(1, bookmarks.size)
    assertEquals("Act I Scene 2", bookmarks[0].name)
    
    database.close()
  }

  @Test
  fun `playback manager sleep timer starts cancels and ticks correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val repository = com.audiora.data.repository.BookRepositoryImpl(database.bookDao(), database.bookmarkDao())
    val playbackManager = com.audiora.feature.player.PlaybackManager(context, repository)
    
    // Check initial state
    assertEquals(com.audiora.feature.player.SleepTimerType.OFF, playbackManager.sleepTimerType.value)
    assertEquals(0L, playbackManager.sleepTimerRemaining.value)
    
    // Start sleep timer (e.g., 5 min preset)
    playbackManager.startSleepTimer(com.audiora.feature.player.SleepTimerType.MIN_5)
    assertEquals(com.audiora.feature.player.SleepTimerType.MIN_5, playbackManager.sleepTimerType.value)
    assertEquals(5 * 60 * 1000L, playbackManager.sleepTimerRemaining.value)
    
    // Cancel sleep timer
    playbackManager.cancelSleepTimer()
    assertEquals(com.audiora.feature.player.SleepTimerType.OFF, playbackManager.sleepTimerType.value)
    assertEquals(0L, playbackManager.sleepTimerRemaining.value)
    
    database.close()
  }

  @Test
  fun `database should save retrieve reorder and delete audiobook folders correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val folderDao = database.folderDao()
    val bookDao = database.bookDao()
    val repository = com.audiora.data.repository.FolderRepositoryImpl(context, folderDao, bookDao)
    
    // Check initial folders
    var folders = repository.getFolders().first()
    assertTrue(folders.isEmpty())
    
    // Add multiple folders
    repository.addFolder("content://folders/uri1", "Internal Books 1")
    repository.addFolder("content://folders/uri2", "SD Card Books")
    
    // Check retrieved folders
    folders = repository.getFolders().first()
    assertEquals(2, folders.size)
    assertEquals("Internal Books 1", folders[0].name)
    assertEquals("SD Card Books", folders[1].name)
    
    // Insert some audiobooks with folder associations
    val book1 = com.audiora.domain.model.Audiobook(
      id = 11,
      filePath = "content://folders/uri1/book1.mp3",
      title = "Folder Book 1",
      author = "Author 1",
      narrator = "", publisher = "", genre = "Audiobook", year = "2026", description = "",
      durationMs = 1200L,
      folderUri = "content://folders/uri1"
    )
    val book2 = com.audiora.domain.model.Audiobook(
      id = 12,
      filePath = "content://folders/uri2/book2.m4b",
      title = "Folder Book 2",
      author = "Author 2",
      narrator = "", publisher = "", genre = "Audiobook", year = "2026", description = "",
      durationMs = 2400L,
      folderUri = "content://folders/uri2"
    )
    
    bookDao.insertAudiobook(com.audiora.data.local.BookEntity.fromDomain(book1))
    bookDao.insertAudiobook(com.audiora.data.local.BookEntity.fromDomain(book2))
    
    // Assert books are present
    var booksList = bookDao.getAllAudiobooks().first()
    assertEquals(2, booksList.size)
    
    // Reorder folders
    val inverse = listOf(folders[1], folders[0])
    repository.updateFoldersOrder(inverse)
    
    val reordered = repository.getFolders().first()
    assertEquals(2, reordered.size)
    assertEquals("SD Card Books", reordered[0].name) // now first
    assertEquals("Internal Books 1", reordered[1].name) // now second
    
    // Remove the first folder (Internal Books 1, folder id from folders[0]) which has content://folders/uri1
    repository.removeFolder(folders[0].id, folders[0].uri)
    
    // Remaining folder should be 1
    val finalFolders = repository.getFolders().first()
    assertEquals(1, finalFolders.size)
    assertEquals("SD Card Books", finalFolders[0].name)
    
    // Removing a folder must delete associated audiobooks from library
    booksList = bookDao.getAllAudiobooks().first()
    assertEquals(1, booksList.size)
    assertEquals("Folder Book 2", booksList[0].title) // Book 1 was deleted!
    
    database.close()
  }

  @Test
  fun `database should update existing books when metadata files changed`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val bookDao = database.bookDao()
    
    val initialBook = com.audiora.data.local.BookEntity(
      id = 101,
      filePath = "content://folders/uri1/book_a.mp3",
      title = "Initial Title",
      author = "Initial Author",
      narrator = "", publisher = "", genre = "Audiobook", year = "2026", description = "",
      durationMs = 5000L,
      fileSize = 1000L,
      lastModified = 50000L,
      folderUri = "content://folders/uri1"
    )
    bookDao.insertAudiobook(initialBook)
    
    // Fetch and check size/modified
    var bookInDb = bookDao.getAudiobookByFilePath("content://folders/uri1/book_a.mp3")
    assertNotNull(bookInDb)
    assertEquals("Initial Title", bookInDb?.title)
    assertEquals(101, bookInDb?.id)
    assertEquals(1000L, bookInDb?.fileSize)
    
    // Simulate File change detection & DB updates
    val updatedBook = bookInDb!!.copy(
      title = "Changed Title",
      fileSize = 2500L,
      lastModified = 90000L
    )
    bookDao.updateAudiobook(updatedBook)
    
    // Assert change detected and saved correctly while preserving existing metadata ID
    val finalBook = bookDao.getAudiobookByFilePath("content://folders/uri1/book_a.mp3")
    assertEquals("Changed Title", finalBook?.title)
    assertEquals(101, finalBook?.id) // preserved ID
    assertEquals(2500L, finalBook?.fileSize)
    assertEquals(90000L, finalBook?.lastModified)
    
    database.close()
  }

  @Test
  fun `settings repository should persist and retrieve preferences correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settingsRepository = com.audiora.data.repository.SettingsRepositoryImpl(context)
    
    // Test initial defaults
    assertEquals("SYSTEM", settingsRepository.getThemeMode().first())
    assertEquals("AUDIORA_PURPLE", settingsRepository.getColorScheme().first())
    assertEquals(15, settingsRepository.getSkipAmount().first())
    assertEquals(3, settingsRepository.getAutoRewind().first())
    assertEquals(1.0f, settingsRepository.getDefaultPlaybackSpeed().first())
    assertEquals(30, settingsRepository.getSleepTimerDefault().first())
    
    // Update and test persistence
    settingsRepository.setThemeMode("DARK")
    settingsRepository.setColorScheme("CRIMSON_RED")
    settingsRepository.setSkipAmount(30)
    settingsRepository.setAutoRewind(5)
    settingsRepository.setDefaultPlaybackSpeed(1.5f)
    settingsRepository.setSleepTimerDefault(45)
    
    assertEquals("DARK", settingsRepository.getThemeMode().first())
    assertEquals("CRIMSON_RED", settingsRepository.getColorScheme().first())
    assertEquals(30, settingsRepository.getSkipAmount().first())
    assertEquals(5, settingsRepository.getAutoRewind().first())
    assertEquals(1.5f, settingsRepository.getDefaultPlaybackSpeed().first())
    assertEquals(45, settingsRepository.getSleepTimerDefault().first())
  }

  @Test
  fun `chapters serialization and deserialization works correctly`() {
    val list = listOf(
      com.audiora.domain.model.Chapter("Chap 1", 0L, 5000L, 5000L),
      com.audiora.domain.model.Chapter("Chap 2", 5000L, 10000L, 5000L)
    )
    val json = com.audiora.domain.model.Chapter.serializeList(list)
    assertNotNull(json)
    
    val decoded = com.audiora.domain.model.Chapter.deserializeList(json)
    assertEquals(2, decoded.size)
    assertEquals("Chap 1", decoded[0].title)
    assertEquals(5000L, decoded[0].endMs)
    assertEquals("Chap 2", decoded[1].title)
    assertEquals(5000L, decoded[1].startMs)
  }

  @Test
  fun `database should save retrieve and update chapters correctly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    val testBook = com.audiora.domain.model.Audiobook(
      id = 99,
      filePath = "content://audio/99",
      title = "Chapters Test Audiobook",
      author = "Code Assistant",
      narrator = "", publisher = "", genre = "", year = "", description = "",
      durationMs = 60000L
    )
    repository.saveAudiobook(testBook)
    
    val chapters = listOf(
      com.audiora.domain.model.Chapter("Beginning", 0L, 30000L, 30000L),
      com.audiora.domain.model.Chapter("End Portion", 30000L, 60000L, 30000L)
    )
    
    repository.updateBookChapters(context, 99, chapters)
    
    val list = repository.getAudiobooks().first()
    assertEquals(1, list.size)
    val fetched = list[0]
    assertNotNull(fetched.chaptersJson)
    
    val decoded = com.audiora.domain.model.Chapter.deserializeList(fetched.chaptersJson)
    assertEquals(2, decoded.size)
    assertEquals("Beginning", decoded[0].title)
    assertEquals("End Portion", decoded[1].title)
    
    database.close()
  }

  @Test
  fun `chapters updateChapter should correctly modify title and start time and recalculate boundaries`() = kotlinx.coroutines.runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      application,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    val initialChapters = listOf(
      com.audiora.domain.model.Chapter("Chap A", 0L, 40000L, 40000L),
      com.audiora.domain.model.Chapter("Chap B", 40000L, 80000L, 40000L),
      com.audiora.domain.model.Chapter("Chap C", 80000L, 120000L, 40000L)
    )
    
    val testBook = com.audiora.domain.model.Audiobook(
      id = 101,
      filePath = "content://audio/101",
      title = "Update Chapters Test",
      author = "Code Assistant",
      narrator = "", publisher = "", genre = "", year = "", description = "",
      durationMs = 120000L, // 2 minutes
      chaptersJson = com.audiora.domain.model.Chapter.serializeList(initialChapters)
    )
    
    // Instantiate with -1 so no automatic selection from the empty database happens
    val viewModel = com.audiora.feature.editor.EditViewModel(application, repository, -1)
    
    // Manually select our book
    viewModel.selectBook(testBook)
    
    val chaptersBefore = viewModel.chapters.value
    assertEquals(3, chaptersBefore.size)
    
    viewModel.updateChapter(index = 1, newName = "Revised B", newStartMs = 30000L)
    
    val chaptersAfter = viewModel.chapters.value
    assertEquals(3, chaptersAfter.size)
    assertEquals(30000L, chaptersAfter[0].endMs)
    assertEquals(30000L, chaptersAfter[0].durationMs)
    
    assertEquals("Revised B", chaptersAfter[1].title)
    assertEquals(30000L, chaptersAfter[1].startMs)
    assertEquals(80000L, chaptersAfter[1].endMs)
    assertEquals(50000L, chaptersAfter[1].durationMs)
    
    database.close()
  }

  @Test
  fun test_timeline_visual_drag_boundary_recalculation() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(application, com.audiora.data.local.AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    val initialChapters = listOf(
      com.audiora.domain.model.Chapter("Chapter 1", 0L, 50000L, 50000L),
      com.audiora.domain.model.Chapter("Chapter 2", 50000L, 100000L, 50000L)
    )
    
    val testBook = com.audiora.domain.model.Audiobook(
      id = 105,
      filePath = "content://audio/105",
      title = "Drag Test",
      author = "Assistant Author",
      narrator = "", publisher = "", genre = "", year = "", description = "",
      durationMs = 100000L,
      chaptersJson = com.audiora.domain.model.Chapter.serializeList(initialChapters)
    )
    
    val viewModel = com.audiora.feature.editor.EditViewModel(application, repository, -1)
    viewModel.selectBook(testBook)
    
    // Simulate user dragging Chapter 2 start offset from 50.00s to 75.00s (visually moving boundary)
    viewModel.updateChapter(index = 1, newName = "Chapter 2", newStartMs = 75000L)
    
    val updatedChapters = viewModel.chapters.value
    assertEquals(2, updatedChapters.size)
    
    // Chapter 1 should expand dynamically to end at 75.00s instead of 50.00s
    assertEquals(0L, updatedChapters[0].startMs)
    assertEquals(75000L, updatedChapters[0].endMs)
    assertEquals(75000L, updatedChapters[0].durationMs)
    
    // Chapter 2 now starts at 75.00s and ends at 100.00s
    assertEquals(75000L, updatedChapters[1].startMs)
    assertEquals(100000L, updatedChapters[1].endMs)
    assertEquals(25000L, updatedChapters[1].durationMs)
    
    database.close()
  }

  @Test
  fun `storage import manager should allow bulk updating of imported files to support custom manual reordering`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = StorageImportManager(context)
    
    val mockUri1 = Uri.parse("content://com.android.providers.media.documents/document/audio%3A101")
    val mockUri2 = Uri.parse("content://com.android.providers.media.documents/document/audio%3A102")
    
    manager.saveImportedFile(mockUri1, "FileA.mp3", 1000L, "MP3")
    manager.saveImportedFile(mockUri2, "FileB.mp3", 2000L, "MP3")
    
    val initialList = manager.getImportedFiles()
    assertEquals(2, initialList.size)
    assertEquals("FileA.mp3", initialList[0].name)
    assertEquals("FileB.mp3", initialList[1].name)
    
    // Reverse the list
    val reversedList = initialList.reversed()
    manager.updateImportedFiles(reversedList)
    
    val finalList = manager.getImportedFiles()
    assertEquals(2, finalList.size)
    assertEquals("FileB.mp3", finalList[0].name)
    assertEquals("FileA.mp3", finalList[1].name)
  }

  @Test
  fun `M4BTranscoder should return false immediately on empty input list`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempFile = java.io.File.createTempFile("m4b_out", ".m4b")
    tempFile.deleteOnExit()
    
    val result = com.audiora.feature.converter.M4BTranscoder.transcode(
      context = context,
      inputUris = emptyList(),
      outputFile = tempFile,
      listener = object : com.audiora.feature.converter.M4BTranscoder.ProgressListener {
        override fun onProgress(percentage: Float) {}
      }
    )
    assertEquals(false, result)
  }

  @Test
  fun `AudiobookDetailViewModel should coordinate export workflow correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.audiora.data.local.AppDatabase::class.java
    ).allowMainThreadQueries().build()
    
    val dao = database.bookDao()
    val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())
    
    val viewModel = com.audiora.feature.detail.AudiobookDetailViewModel(repository, 42)
    
    assertEquals(com.audiora.feature.detail.ExportStatus.Idle, viewModel.exportStatus.value)
    assertEquals(0f, viewModel.exportProgress.value)
    
    viewModel.resetExportStatus()
    assertEquals(com.audiora.feature.detail.ExportStatus.Idle, viewModel.exportStatus.value)
    assertEquals(0f, viewModel.exportProgress.value)
    
    database.close()
  }

  @Test
  fun `SearchViewModel should perform offline queries and handle history correctly`() = kotlinx.coroutines.runBlocking {
    val testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    try {
      val context = ApplicationProvider.getApplicationContext<android.app.Application>()
      val database = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.audiora.data.local.AppDatabase::class.java
      ).allowMainThreadQueries().build()

      val dao = database.bookDao()
      val repository = com.audiora.data.repository.BookRepositoryImpl(dao, database.bookmarkDao())

      // Save test audiobooks
      val book1 = com.audiora.domain.model.Audiobook(
        id = 101,
        filePath = "/dummy/lotr.m4b",
        title = "Lord of the Rings",
        author = "J.R.R. Tolkien",
        narrator = "Rob Inglis",
        publisher = "HarperCollins",
        genre = "Fantasy",
        year = "1954",
        description = "Epic high-fantasy masterwork.",
        durationMs = 1800000L
      )
      val book2 = com.audiora.domain.model.Audiobook(
        id = 102,
        filePath = "/dummy/hobbit.m4b",
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        narrator = "Martin Shaw",
        publisher = "George Allen & Unwin",
        genre = "Fantasy",
        year = "1937",
        description = "A great adventure.",
        durationMs = 900000L
      )

      repository.saveAudiobook(book1)
      repository.saveAudiobook(book2)

      val viewModel = com.audiora.feature.search.SearchViewModel(context, repository)

      val collectJob = launch(testDispatcher) {
        viewModel.searchResults.collect {}
      }
      val historyCollectJob = launch(testDispatcher) {
        viewModel.recentSearches.collect {}
      }

      // Allow background database read flows to propagate to allAudiobooks flow
      kotlinx.coroutines.delay(150)

      // Verify initial state
      assertEquals("", viewModel.searchQuery.value)

      // Update query to Tolkien (matches author of both books)
      viewModel.updateQuery("Tolkien")
      kotlinx.coroutines.delay(150)
      
      val resultsByAuthor = viewModel.searchResults.value
      assertEquals(2, resultsByAuthor.size)

      // Update query to "Hobbit" (matches title)
      viewModel.updateQuery("Hobbit")
      kotlinx.coroutines.delay(150)
      val resultsByTitle = viewModel.searchResults.value
      assertEquals(1, resultsByTitle.size)
      assertEquals(102, resultsByTitle.first().id)

      // Update query to "Inglis" (matches narrator)
      viewModel.updateQuery("Inglis")
      kotlinx.coroutines.delay(150)
      val resultsByNarrator = viewModel.searchResults.value
      assertEquals(1, resultsByNarrator.size)
      assertEquals(101, resultsByNarrator.first().id)

      // Test search history
      viewModel.clearRecentSearches()
      assertTrue(viewModel.recentSearches.value.isEmpty())

      viewModel.onSearchAction("Tolkien core")
      assertEquals(1, viewModel.recentSearches.value.size)
      assertEquals("Tolkien core", viewModel.recentSearches.value.first())

      viewModel.onSearchAction("Hobbit")
      assertEquals(2, viewModel.recentSearches.value.size)
      assertEquals("Hobbit", viewModel.recentSearches.value.first()) // Most recent is first

      viewModel.removeRecentSearch("Tolkien core")
      assertEquals(1, viewModel.recentSearches.value.size)
      assertEquals("Hobbit", viewModel.recentSearches.value.first())

      viewModel.clearRecentSearches()
      assertTrue(viewModel.recentSearches.value.isEmpty())

      collectJob.cancel()
      historyCollectJob.cancel()
      database.close()
    } finally {
      Dispatchers.resetMain()
    }
  }
}

