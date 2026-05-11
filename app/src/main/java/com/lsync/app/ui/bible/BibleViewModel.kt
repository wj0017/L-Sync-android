package com.lsync.app.ui.bible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.dao.BibleBook
import com.lsync.app.data.local.dao.BibleDao
import com.lsync.app.data.local.entity.BibleVerseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BibleUiState(
    val books: List<BibleBook> = emptyList(),
    val currentBook: Int = 1,
    val currentChapter: Int = 1,
    val chapterCount: Int = 1,
    val verses: List<BibleVerseEntity> = emptyList(),
    val isTableOfContentsOpen: Boolean = false,
)

@HiltViewModel
class BibleViewModel @Inject constructor(
    private val bibleDao: BibleDao,
) : ViewModel() {

    private val _state = MutableStateFlow(BibleUiState())
    val state: StateFlow<BibleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val books = bibleDao.getBooks()
            if (books.isEmpty()) return@launch
            _state.value = _state.value.copy(books = books)
            fetchAndApply(books.first().book, 1)
        }
    }

    fun navigateTo(book: Int, chapter: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isTableOfContentsOpen = false)
            fetchAndApply(book, chapter)
        }
    }

    fun nextChapter() {
        val s = _state.value
        if (s.currentChapter < s.chapterCount) {
            viewModelScope.launch { fetchAndApply(s.currentBook, s.currentChapter + 1) }
        } else {
            val idx = s.books.indexOfFirst { it.book == s.currentBook }
            if (idx < s.books.lastIndex) {
                viewModelScope.launch { fetchAndApply(s.books[idx + 1].book, 1) }
            }
        }
    }

    fun prevChapter() {
        val s = _state.value
        if (s.currentChapter > 1) {
            viewModelScope.launch { fetchAndApply(s.currentBook, s.currentChapter - 1) }
        } else {
            val idx = s.books.indexOfFirst { it.book == s.currentBook }
            if (idx > 0) {
                val prevBook = s.books[idx - 1].book
                viewModelScope.launch {
                    val lastChapter = bibleDao.getChapterCount(prevBook)
                    _state.value = _state.value.copy(
                        currentBook = prevBook,
                        currentChapter = lastChapter,
                        chapterCount = lastChapter,
                        verses = bibleDao.getVerses(prevBook, lastChapter),
                    )
                }
            }
        }
    }

    fun toggleTableOfContents() {
        _state.value = _state.value.copy(isTableOfContentsOpen = !_state.value.isTableOfContentsOpen)
    }

    private suspend fun fetchAndApply(book: Int, chapter: Int) {
        val chapterCount = bibleDao.getChapterCount(book)
        val verses = bibleDao.getVerses(book, chapter)
        _state.value = _state.value.copy(
            currentBook = book,
            currentChapter = chapter,
            chapterCount = chapterCount,
            verses = verses,
        )
    }
}
