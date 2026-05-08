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
          AND date >= :from
          AND date <= :to
        ORDER BY date DESC
    """)
    fun observeByDateRange(from: String, to: String): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance WHERE id = :id")
    suspend fun getById(id: String): FinanceEntity?

    @Query("SELECT * FROM finance WHERE sourceTodoId = :todoId")
    suspend fun getBySourceTodo(todoId: String): FinanceEntity?

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
}
