package com.lsync.app.data.repository

import android.content.Context
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.entity.ReadingPlanEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ReadingOrder { CANONICAL, NT_FIRST }

data class ReadingPlanSettings(
    val startBook: Int = 1,
    val startChapter: Int = 1,
    val chaptersPerDay: Int = 3,
    val readingOrder: ReadingOrder = ReadingOrder.CANONICAL,
)

@Singleton
class ReadingPlanRepository @Inject constructor(
    private val dao: ReadingPlanDao,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "reading_plan_prefs"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_START_BOOK = "start_book"
        private const val KEY_START_CHAPTER = "start_chapter"
        private const val KEY_CHAPTERS_PER_DAY = "chapters_per_day"
        private const val KEY_READING_ORDER = "reading_order"

        val CHAPTER_COUNTS = intArrayOf(
            50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150,
            31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4,
            28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22,
        )
        val TOTAL_CHAPTERS = CHAPTER_COUNTS.sum() // 1189
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 시작일 ────────────────────────────────────────────────────────────────

    fun getStartDate(): LocalDate? =
        prefs.getString(KEY_START_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setStartDate(date: LocalDate) {
        prefs.edit().putString(KEY_START_DATE, date.toString()).apply()
    }

    // ── 설정 읽기/쓰기 ────────────────────────────────────────────────────────

    fun getSettings(): ReadingPlanSettings = ReadingPlanSettings(
        startBook      = prefs.getInt(KEY_START_BOOK, 1),
        startChapter   = prefs.getInt(KEY_START_CHAPTER, 1),
        chaptersPerDay = prefs.getInt(KEY_CHAPTERS_PER_DAY, 3),
        readingOrder   = prefs.getString(KEY_READING_ORDER, null)
                            ?.let { runCatching { ReadingOrder.valueOf(it) }.getOrNull() }
                            ?: ReadingOrder.CANONICAL,
    )

    fun saveSettings(settings: ReadingPlanSettings) {
        prefs.edit()
            .putInt(KEY_START_BOOK, settings.startBook)
            .putInt(KEY_START_CHAPTER, settings.startChapter)
            .putInt(KEY_CHAPTERS_PER_DAY, settings.chaptersPerDay)
            .putString(KEY_READING_ORDER, settings.readingOrder.name)
            .apply()
    }

    // ── 챕터 시퀀스 계산 ─────────────────────────────────────────────────────

    // 읽기 순서에 따른 전체 (book, chapter) 시퀀스 생성
    fun buildChapterSequence(order: ReadingOrder): List<Pair<Int, Int>> {
        val bookOrder = when (order) {
            ReadingOrder.CANONICAL -> (1..66).toList()
            ReadingOrder.NT_FIRST  -> (40..66).toList() + (1..39).toList()
        }
        return buildList {
            for (bookNum in bookOrder) {
                val chapters = CHAPTER_COUNTS[bookNum - 1]
                for (ch in 1..chapters) add(Pair(bookNum, ch))
            }
        }
    }

    // 시퀀스에서 (book, chapter)의 인덱스 찾기
    fun findSequenceIndex(sequence: List<Pair<Int, Int>>, book: Int, chapter: Int): Int =
        sequence.indexOfFirst { it.first == book && it.second == chapter }.coerceAtLeast(0)

    // 특정 날짜에 읽어야 할 챕터 목록 계산
    fun computeChaptersForDate(startDate: LocalDate, targetDate: LocalDate): List<Pair<Int, Int>> {
        val dayIndex = ChronoUnit.DAYS.between(startDate, targetDate).toInt()
        if (dayIndex < 0) return emptyList()

        val settings = getSettings()
        val sequence = buildChapterSequence(settings.readingOrder)
        val startOffset = findSequenceIndex(sequence, settings.startBook, settings.startChapter)

        val startSeq = startOffset + dayIndex * settings.chaptersPerDay
        if (startSeq >= sequence.size) return emptyList()

        return sequence.subList(startSeq, minOf(startSeq + settings.chaptersPerDay, sequence.size))
    }

    // 설정 기준 예상 완독 일수
    fun estimatedTotalDays(): Int {
        val settings = getSettings()
        val sequence = buildChapterSequence(settings.readingOrder)
        val startOffset = findSequenceIndex(sequence, settings.startBook, settings.startChapter)
        val remaining = sequence.size - startOffset
        return Math.ceil(remaining.toDouble() / settings.chaptersPerDay).toInt()
    }

    // ── DB 동기화 ─────────────────────────────────────────────────────────────

    suspend fun ensureReadingPlanForDate(date: LocalDate) {
        val startDate = getStartDate() ?: return
        if (dao.getCountForDate(date.toString()) > 0) return
        val entries = computeChaptersForDate(startDate, date).map { (book, chapter) ->
            ReadingPlanEntity(date = date.toString(), book = book, chapter = chapter)
        }
        if (entries.isNotEmpty()) dao.upsertAll(entries)
    }

    // 설정 변경 시 오늘 이후 항목 초기화 후 오늘 치 재생성
    suspend fun resetFromToday() {
        val today = LocalDate.now()
        dao.deleteFromDate(today.toString())
        ensureReadingPlanForDate(today)
    }

    fun observeForDate(date: String): Flow<List<ReadingPlanEntity>> = dao.observeForDate(date)

    suspend fun markRead(date: String, book: Int, chapter: Int, isRead: Boolean) =
        dao.markRead(date, book, chapter, isRead)

    suspend fun getTotalRead(): Int = dao.getTotalRead()
}
