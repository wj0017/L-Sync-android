package com.lsync.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.lsync.app.data.local.entity.BibleVerseEntity

data class BibleBook(
    val book: Int,
    @ColumnInfo(name = "book_name") val bookName: String,
    @ColumnInfo(name = "book_short") val bookShort: String,
    val testament: String,
)

data class BookChapterCount(
    val book: Int,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
)

@Dao
interface BibleDao {
    @Query("SELECT DISTINCT book, book_name, book_short, testament FROM bible_verses ORDER BY book ASC")
    suspend fun getBooks(): List<BibleBook>

    @Query("SELECT MAX(chapter) FROM bible_verses WHERE book = :book")
    suspend fun getChapterCount(book: Int): Int?

    @Query("SELECT book, MAX(chapter) AS chapter_count FROM bible_verses GROUP BY book ORDER BY book ASC")
    suspend fun getChapterCounts(): List<BookChapterCount>

    @Query("SELECT * FROM bible_verses WHERE book = :book AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getVerses(book: Int, chapter: Int): List<BibleVerseEntity>

    @Query("SELECT * FROM bible_verses WHERE text LIKE '%' || :query || '%' LIMIT 100")
    suspend fun search(query: String): List<BibleVerseEntity>
}
