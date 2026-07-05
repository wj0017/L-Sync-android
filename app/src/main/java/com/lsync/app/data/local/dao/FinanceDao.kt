package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // isExcluded=false인 것만 통계에 포함
    @Query("""
        SELECT * FROM finance
        WHERE isExcluded = 0
          AND deletedAt IS NULL
          AND date >= :from
          AND date <= :to
        ORDER BY date DESC
    """)
    fun observeByDateRange(from: String, to: String): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): FinanceEntity?

    @Query("SELECT * FROM finance WHERE sourceTodoId = :todoId AND deletedAt IS NULL")
    suspend fun getBySourceTodo(todoId: String): FinanceEntity?

    @Query("""
        SELECT * FROM finance
        WHERE isExcluded = 0
          AND deletedAt IS NULL
          AND (category LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<FinanceEntity>>

    @Upsert
    suspend fun upsert(finance: FinanceEntity)

    // Todo Uncheck → Soft Delete (통계 제외)
    @Query("UPDATE finance SET isExcluded = 1, updatedAt = :now WHERE sourceTodoId = :todoId")
    suspend fun excludeByTodoId(todoId: String, now: Long = System.currentTimeMillis())

    // Todo Re-check → 복구
    @Query("UPDATE finance SET isExcluded = 0, updatedAt = :now WHERE sourceTodoId = :todoId")
    suspend fun includeByTodoId(todoId: String, now: Long = System.currentTimeMillis())

    // Todo 삭제 → 연결 고리만 해제 (데이터는 유지, PRD 2.2 정책)
    @Query("UPDATE finance SET sourceTodoId = NULL, updatedAt = :now WHERE sourceTodoId = :todoId")
    suspend fun unlinkTodo(todoId: String, now: Long = System.currentTimeMillis())

    // 정산 추적: settlementGroupId가 있는 모든 항목 (EXPENSE 리더 + INCOME 정산 입금)
    @Query("SELECT * FROM finance WHERE settlementGroupId IS NOT NULL AND isExcluded = 0 AND deletedAt IS NULL")
    fun observeAllSettlementItems(): Flow<List<FinanceEntity>>

    // 특정 정산 그룹에 속한 모든 항목 (그룹 삭제·정리용)
    @Query("SELECT * FROM finance WHERE settlementGroupId = :groupId AND deletedAt IS NULL")
    suspend fun getBySettlementGroup(groupId: String): List<FinanceEntity>

    // CSV 내보내기용: 날짜 범위 조회, isExcluded 항목 제외
    @Query("""
        SELECT * FROM finance
        WHERE userId = :userId
          AND date >= :from
          AND date <= :to
          AND isExcluded = 0
          AND deletedAt IS NULL
        ORDER BY date ASC
    """)
    suspend fun getAllByDateRange(userId: String, from: String, to: String): List<FinanceEntity>

    @Query("UPDATE finance SET userId = :newId WHERE userId = :oldId")
    suspend fun migrateUserId(oldId: String, newId: String)

    // 로그아웃 시 동기화 대상 테이블 전체 비움 (계정 전환 데이터 격리)
    @Query("DELETE FROM finance")
    suspend fun clearAll()
}
