package com.lsync.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lsync.app.data.local.entity.ReadingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPlanDao {

    @Query("SELECT * FROM reading_plan WHERE date = :date ORDER BY book ASC, chapter ASC")
    fun observeForDate(date: String): Flow<List<ReadingPlanEntity>>

    @Query("SELECT * FROM reading_plan WHERE date = :date ORDER BY book ASC, chapter ASC")
    suspend fun getForDate(date: String): List<ReadingPlanEntity>

    @Query("SELECT COUNT(*) FROM reading_plan WHERE isRead = 1")
    suspend fun getTotalRead(): Int

    @Query("UPDATE reading_plan SET isRead = :isRead WHERE date = :date AND book = :book AND chapter = :chapter")
    suspend fun markRead(date: String, book: Int, chapter: Int, isRead: Boolean)

    @Upsert
    suspend fun upsertAll(entries: List<ReadingPlanEntity>)

    @Query("SELECT COUNT(*) FROM reading_plan WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("DELETE FROM reading_plan WHERE date >= :fromDate")
    suspend fun deleteFromDate(fromDate: String)

    @Query("SELECT DISTINCT date FROM reading_plan WHERE isRead = 1 ORDER BY date DESC LIMIT 400")
    suspend fun getReadDates(): List<String>
}
