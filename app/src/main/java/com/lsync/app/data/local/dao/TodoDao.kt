package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY dueDate ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE dueDate = :date AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeByDate(date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): TodoEntity?

    // 미완료 미래 인스턴스 조회 (템플릿 업데이트 시 덮어쓰기 대상)
    @Query("""
        SELECT * FROM todos
        WHERE templateId = :templateId
          AND isCompleted = 0
          AND dueDate >= :fromDate
          AND deletedAt IS NULL
    """)
    suspend fun getPendingFutureByTemplate(templateId: String, fromDate: String): List<TodoEntity>

    @Query("""
        SELECT * FROM todos
        WHERE isCompleted = 0
          AND deletedAt IS NULL
          AND dueDate IS NOT NULL
          AND dueDate >= :fromDate
    """)
    suspend fun getFutureAlarmedTodos(fromDate: String): List<TodoEntity>

    @Query("""
        SELECT * FROM todos
        WHERE dueDate = :date
          AND isCompleted = 0
          AND deletedAt IS NULL
        ORDER BY createdAt ASC
    """)
    suspend fun getIncompleteByDate(date: String): List<TodoEntity>

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Upsert
    suspend fun upsertAll(todos: List<TodoEntity>)

    // Todo 삭제: Soft delete (deletedAt 기록) + linkedFinanceId 연결 고리 해제는 Repository에서 처리
    @Query("UPDATE todos SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}
