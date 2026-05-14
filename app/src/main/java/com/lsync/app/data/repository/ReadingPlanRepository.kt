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

@Singleton
class ReadingPlanRepository @Inject constructor(
    private val dao: ReadingPlanDao,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "reading_plan_prefs"
        private const val KEY_START_DATE = "start_date"

        // 66권 각 챕터 수 (창세기~요한계시록)
        val CHAPTER_COUNTS = intArrayOf(
            50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150,
            31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4,
            28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22,
        )
        val TOTAL_CHAPTERS = CHAPTER_COUNTS.sum() // 1189
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStartDate(): LocalDate? =
        prefs.getString(KEY_START_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setStartDate(date: LocalDate) {
        prefs.edit().putString(KEY_START_DATE, date.toString()).apply()
    }

    // 시작일 기준 dayIndex → 그 날 읽어야 할 (book 1-based, chapter 1-based) 목록
    fun computeChaptersForDate(startDate: LocalDate, targetDate: LocalDate): List<Pair<Int, Int>> {
        val dayIndex = ChronoUnit.DAYS.between(startDate, targetDate).toInt()
        if (dayIndex < 0 || dayIndex >= 365) return emptyList()

        // 1189 = 365*3 + 94 → 앞 94일은 4챕터, 나머지 271일은 3챕터
        val extraDays = TOTAL_CHAPTERS % 365  // 94
        val startSeq = if (dayIndex < extraDays) dayIndex * 4
                       else extraDays * 4 + (dayIndex - extraDays) * 3
        val count = if (dayIndex < extraDays) 4 else 3

        return (startSeq until startSeq + count).map { seqToBookChapter(it) }
    }

    // 0-based 연번 → (book 1-based, chapter 1-based)
    private fun seqToBookChapter(seqIdx: Int): Pair<Int, Int> {
        var remaining = seqIdx
        for ((i, count) in CHAPTER_COUNTS.withIndex()) {
            if (remaining < count) return Pair(i + 1, remaining + 1)
            remaining -= count
        }
        return Pair(66, 22)
    }

    suspend fun ensureReadingPlanForDate(date: LocalDate) {
        val startDate = getStartDate() ?: return
        if (dao.getCountForDate(date.toString()) > 0) return
        val entries = computeChaptersForDate(startDate, date).map { (book, chapter) ->
            ReadingPlanEntity(date = date.toString(), book = book, chapter = chapter)
        }
        if (entries.isNotEmpty()) dao.upsertAll(entries)
    }

    fun observeForDate(date: String): Flow<List<ReadingPlanEntity>> = dao.observeForDate(date)

    suspend fun markRead(date: String, book: Int, chapter: Int, isRead: Boolean) =
        dao.markRead(date, book, chapter, isRead)

    suspend fun getTotalRead(): Int = dao.getTotalRead()
}
