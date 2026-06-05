package com.lsync.app.ui.bible

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.dao.BibleBook
import com.lsync.app.data.local.dao.BibleDao
import com.lsync.app.data.local.dao.EsvDao
import com.lsync.app.data.local.dao.MemoDao
import com.lsync.app.data.local.entity.BibleVerseEntity
import com.lsync.app.data.local.entity.EsvVerseEntity
import com.lsync.app.data.local.entity.MemoEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.repository.ReadingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BibleUiState(
    val books: List<BibleBook> = emptyList(),
    val chapterCounts: Map<Int, Int> = emptyMap(),
    val currentBook: Int = 1,
    val currentChapter: Int = 1,
    val chapterCount: Int = 1,
    val verses: List<BibleVerseEntity> = emptyList(),
    val esvVerses: List<EsvVerseEntity> = emptyList(),
    val showKorean: Boolean = true,
    val esvOnTop: Boolean = true,
    val isTableOfContentsOpen: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<BibleVerseEntity> = emptyList(),
    val error: String? = null,
    val memos: List<MemoEntity> = emptyList(),
    val todayReadingPlan: List<ReadingPlanEntity> = emptyList(),
) {
    val isPlanChapter: Boolean
        get() = todayReadingPlan.any { it.book == currentBook && it.chapter == currentChapter }
    val isPlanChapterRead: Boolean
        get() = todayReadingPlan.find { it.book == currentBook && it.chapter == currentChapter }?.isRead ?: false
}

@HiltViewModel
class BibleViewModel @Inject constructor(
    private val bibleDao: BibleDao,
    private val esvDao: EsvDao,
    private val memoDao: MemoDao,
    private val readingPlanRepository: ReadingPlanRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("bible_position", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(BibleUiState())
    val state: StateFlow<BibleUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var memoJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val books         = bibleDao.getBooks()
                if (books.isEmpty()) return@launch
                val chapterCounts = bibleDao.getChapterCounts().associate { it.book to it.chapterCount }
                _state.value = _state.value.copy(books = books, chapterCounts = chapterCounts)
                val savedBook    = prefs.getInt("book", books.first().book)
                val savedChapter = prefs.getInt("chapter", 1)
                fetchAndApply(savedBook, savedChapter)
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
        }
        observeTodayPlan()
    }

    fun navigateTo(book: Int, chapter: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isTableOfContentsOpen = false,
                isSearchActive = false,
                searchQuery = "",
                searchResults = emptyList(),
            )
            searchJob?.cancel()
            runCatching { fetchAndApply(book, chapter) }
        }
    }

    fun toggleKorean() {
        _state.value = _state.value.copy(showKorean = !_state.value.showKorean)
    }

    fun toggleOrder() {
        _state.value = _state.value.copy(esvOnTop = !_state.value.esvOnTop)
    }

    fun toggleSearch() {
        val active = !_state.value.isSearchActive
        searchJob?.cancel()
        _state.value = _state.value.copy(
            isSearchActive = active,
            searchQuery = "",
            searchResults = emptyList(),
        )
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query, searchResults = emptyList())
        searchJob?.cancel()
        if (query.length < 2) return
        searchJob = viewModelScope.launch {
            delay(300)
            runCatching {
                val results = bibleDao.search(query)
                _state.value = _state.value.copy(searchResults = results)
            }
        }
    }

    fun nextChapter() {
        val s = _state.value
        if (s.currentChapter < s.chapterCount) {
            viewModelScope.launch { runCatching { fetchAndApply(s.currentBook, s.currentChapter + 1) } }
        } else {
            val idx = s.books.indexOfFirst { it.book == s.currentBook }
            if (idx < s.books.lastIndex) {
                viewModelScope.launch { runCatching { fetchAndApply(s.books[idx + 1].book, 1) } }
            }
        }
    }

    fun prevChapter() {
        val s = _state.value
        if (s.currentChapter > 1) {
            viewModelScope.launch { runCatching { fetchAndApply(s.currentBook, s.currentChapter - 1) } }
        } else {
            val idx = s.books.indexOfFirst { it.book == s.currentBook }
            if (idx > 0) {
                val prevBook = s.books[idx - 1].book
                viewModelScope.launch {
                    runCatching {
                        val lastChapter = bibleDao.getChapterCount(prevBook) ?: 1
                        val verses = bibleDao.getVerses(prevBook, lastChapter)
                        val esv = esvDao.getVerses(prevBook, lastChapter)
                        _state.value = _state.value.copy(
                            currentBook = prevBook,
                            currentChapter = lastChapter,
                            chapterCount = lastChapter,
                            verses = verses,
                            esvVerses = esv,
                        )
                        savePosition(prevBook, lastChapter)
                    }
                }
            }
        }
    }

    fun goToChapter(chapter: Int) {
        viewModelScope.launch {
            runCatching { fetchAndApply(_state.value.currentBook, chapter) }
        }
    }

    fun toggleTableOfContents() {
        _state.value = _state.value.copy(isTableOfContentsOpen = !_state.value.isTableOfContentsOpen)
    }

    private fun observeTodayPlan() {
        viewModelScope.launch {
            readingPlanRepository.observeForDate(LocalDate.now().toString()).collect { plan ->
                _state.value = _state.value.copy(todayReadingPlan = plan)
            }
        }
    }

    fun togglePlanChapterRead() {
        val s = _state.value
        val today = LocalDate.now().toString()
        val entry = s.todayReadingPlan.find { it.book == s.currentBook && it.chapter == s.currentChapter } ?: return
        viewModelScope.launch {
            readingPlanRepository.markRead(today, entry.book, entry.chapter, !entry.isRead)
        }
    }

    private fun observeMemos(book: Int, chapter: Int) {
        memoJob?.cancel()
        memoJob = viewModelScope.launch {
            memoDao.observeForChapter(book, chapter).collect { memos ->
                _state.value = _state.value.copy(memos = memos)
            }
        }
    }

    fun saveMemo(verse: Int, text: String, date: String) {
        val s = _state.value
        viewModelScope.launch {
            memoDao.insert(MemoEntity(book = s.currentBook, chapter = s.currentChapter, verse = verse, text = text, date = date))
        }
    }

    fun deleteMemo(id: Long) {
        viewModelScope.launch { memoDao.deleteById(id) }
    }

    private suspend fun fetchAndApply(book: Int, chapter: Int) {
        val chapterCount = bibleDao.getChapterCount(book) ?: 1
        val verses = bibleDao.getVerses(book, chapter)
        val esv = esvDao.getVerses(book, chapter)
        _state.value = _state.value.copy(
            currentBook = book,
            currentChapter = chapter,
            chapterCount = chapterCount,
            verses = verses,
            esvVerses = esv,
        )
        savePosition(book, chapter)
        observeMemos(book, chapter)
    }

    private fun savePosition(book: Int, chapter: Int) {
        prefs.edit().putInt("book", book).putInt("chapter", chapter).apply()
    }
}
