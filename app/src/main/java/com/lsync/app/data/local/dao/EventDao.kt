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

    // 전개용 조회 (반복 일정 표시) — 범위 내 단발 + 시작일이 to 이전인 모든 반복 마스터를 포함.
    // observeByDateRange는 startDate >= from이라 과거 시작 반복 마스터를 놓치므로 별도 쿼리.
    @Query("""
        SELECT * FROM events
        WHERE deletedAt IS NULL
          AND substr(startDate, 1, 10) <= :to
          AND (rrule IS NOT NULL OR substr(startDate, 1, 10) >= :from)
        ORDER BY startDate ASC
    """)
    fun observeForExpansion(from: String, to: String): Flow<List<EventEntity>>

    @Query("""
        SELECT * FROM events
        WHERE deletedAt IS NULL
          AND startDate LIKE :datePrefix || '%'
        ORDER BY startDate ASC
    """)
    suspend fun getByDate(datePrefix: String): List<EventEntity>

    // 전개용 suspend 조회 (위젯) — observeForExpansion의 1회성 버전.
    // 과거 시작 반복 마스터를 포함해야 오늘 발생이 누락되지 않는다.
    @Query("""
        SELECT * FROM events
        WHERE deletedAt IS NULL
          AND substr(startDate, 1, 10) <= :to
          AND (rrule IS NOT NULL OR substr(startDate, 1, 10) >= :from)
        ORDER BY startDate ASC
    """)
    suspend fun getForExpansion(from: String, to: String): List<EventEntity>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    // 미래 알람 조회 — 과거 시작 반복 마스터(rrule)도 포함. 누락 시 재부팅 후 반복 알람이 사라진다.
    @Query("""
        SELECT * FROM events
        WHERE hasAlarm = 1
          AND deletedAt IS NULL
          AND (rrule IS NOT NULL OR startDate >= :fromDate)
    """)
    suspend fun getFutureAlarmedEvents(fromDate: String): List<EventEntity>

    // Soft delete 대신 실제 삭제 (이벤트는 가계부 연동 없으므로 Hard delete 허용)
    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
