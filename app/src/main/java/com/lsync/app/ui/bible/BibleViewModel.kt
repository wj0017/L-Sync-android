package com.lsync.app.ui.bible

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.dao.BibleBook
import com.lsync.app.data.local.dao.BibleDao
import com.lsync.app.data.local.dao.EsvDao
import com.lsync.app.data.local.entity.BibleVerseEntity
import com.lsync.app.data.local.entity.EsvVerseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BibleUiState(
    val books: List<BibleBook> = emptyList(),
    val chapterCounts: Map<Int, Int> = emptyMap(),  // book → max chapter
    val currentBook: Int = 1,
    val currentChapter: Int = 1,
    val chapterCount: Int = 1,
    val verses: List<BibleVerseEntity> = emptyList(),
    val esvVerses: List<EsvVerseEntity> = emptyList(),
    val showEsv: Boolean = false,
    val isTableOfContentsOpen: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<BibleVerseEntity> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BibleViewModel @Inject constructor(
    private val bibleDao: BibleDao,
    private val esvDao: EsvDao,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("bible_position", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(BibleUiState())
    val state: StateFlow<BibleUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

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

    fun toggleEsv() {
        val show = !_state.value.showEsv
        _state.value = _state.value.copy(showEsv = show)
        if (show) {
            viewModelScope.launch {
                runCatching {
                    val esv = esvDao.getVerses(_state.value.currentBook, _state.value.currentChapter)
                    _state.value = _state.value.copy(esvVerses = esv)
                }
            }
        } else {
            _state.value = _state.value.copy(esvVerses = emptyList())
        }
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
                        val esv = if (_state.value.showEsv) esvDao.getVerses(prevBook, lastChapter) else emptyList()
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

    private suspend fun fetchAndApply(book: Int, chapter: Int) {
        val chapterCount = bibleDao.getChapterCount(book) ?: 1
        val verses = bibleDao.getVerses(book, chapter)
        val esv = if (_state.value.showEsv) esvDao.getVerses(book, chapter) else emptyList()
        _state.value = _state.value.copy(
            currentBook = book,
            currentChapter = chapter,
            chapterCount = chapterCount,
            verses = verses,
            esvVerses = esv,
        )
        savePosition(book, chapter)
    }

    private fun savePosition(book: Int, chapter: Int) {
        prefs.edit().putInt("book", book).putInt("chapter", chapter).apply()
    }
}
