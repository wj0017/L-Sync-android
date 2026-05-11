package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events WHERE deletedAt IS NULL ORDER BY startDate ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): EventEntity?

    // 특정 날짜 범위의 이벤트 조회 (캘린더 월 뷰)
    @Query("""
        SELECT * FROM events
        WHERE deletedAt IS NULL
          AND startDate >= :from
          AND startDate <= :to
        ORDER BY startDate ASC
    """)
    fun observeByDateRange(from: String, to: String): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    @Query("""
        SELECT * FROM events
        WHERE hasAlarm = 1
          AND deletedAt IS NULL
          AND startDate >= :fromDate
    """)
    suspend fun getFutureAlarmedEvents(fromDate: String): List<EventEntity>

    // Soft delete 대신 실제 삭제 (이벤트는 가계부 연동 없으므로 Hard delete 허용)
    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
