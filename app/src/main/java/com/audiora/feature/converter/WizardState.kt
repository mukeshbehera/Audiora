package com.audiora.feature.converter

import com.audiora.domain.model.Chapter

enum class ChapterStrategy {
    NO_CHAPTERS,
    EACH_FILE_CHAPTER,
    MANUAL
}

object WizardState {
    var title: String = ""
    var author: String = ""
    var narrator: String = "System Narrator"
    var publisher: String = "Audiora Merged"
    var genre: String = "Audiobook"
    var year: String = "2026"
    var description: String = "High-fidelity assembled seamless stream."
    var coverSeed: String = "nebula"
    var chapterStrategy: ChapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
    var manualChapters: List<Chapter> = emptyList()

    fun reset() {
        title = ""
        author = ""
        narrator = "System Narrator"
        publisher = "Audiora Merged"
        genre = "Audiobook"
        year = "2026"
        description = "High-fidelity assembled seamless stream."
        coverSeed = "nebula"
        chapterStrategy = ChapterStrategy.EACH_FILE_CHAPTER
        manualChapters = emptyList()
    }
}
