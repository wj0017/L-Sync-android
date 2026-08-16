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

    @Query("""
        SELECT * FROM todos
        WHERE deletedAt IS NULL
          AND title LIKE '%' || :query || '%'
        ORDER BY dueDate DESC, createdAt DESC
    """)
    fun searchByTitle(query: String): Flow<List<TodoEntity>>

    // 월간 리포트 집계용 — 마감일 없는 Todo는 완료율 분모에서 제외
    @Query("""
        SELECT * FROM todos
        WHERE deletedAt IS NULL
          AND dueDate IS NOT NULL
          AND dueDate >= :from
          AND dueDate <= :to
        ORDER BY dueDate ASC
    """)
    fun observeByDueDateRange(from: String, to: String): Flow<List<TodoEntity>>

    // 위젯 리포트용 일회성 조회 — observeByDueDateRange와 동일 WHERE 절(수치 일치 필수).
    @Query("""
        SELECT * FROM todos
        WHERE deletedAt IS NULL
          AND dueDate IS NOT NULL
          AND dueDate >= :from
          AND dueDate <= :to
        ORDER BY dueDate ASC
    """)
    suspend fun getByDueDateRange(from: String, to: String): List<TodoEntity>

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Upsert
    suspend fun upsertAll(todos: List<TodoEntity>)

    // Todo 삭제: Soft delete (deletedAt 기록) + linkedFinanceId 연결 고리 해제는 Repository에서 처리
    @Query("UPDATE todos SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE todos SET userId = :newId WHERE userId = :oldId")
    suspend fun migrateUserId(oldId: String, newId: String)

    // 로그아웃 시 동기화 대상 테이블 전체 비움 (계정 전환 데이터 격리)
    @Query("DELETE FROM todos")
    suspend fun clearAll()
}
