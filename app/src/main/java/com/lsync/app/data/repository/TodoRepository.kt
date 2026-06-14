package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.local.entity.TodoTemplateEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.notification.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val todoTemplateDao: TodoTemplateDao,
    private val financeDao: FinanceDao,
    private val remote: FirestoreDataSource,
    private val alarmScheduler: AlarmScheduler,
) {
    fun observeAll(): Flow<List<TodoEntity>> = todoDao.observeAll()

    fun observeActiveTemplates(userId: String) = todoTemplateDao.observeActiveTemplates(userId)

    suspend fun getActiveTemplates(userId: String): List<TodoTemplateEntity> =
        todoTemplateDao.getActiveTemplates(userId)

    suspend fun upsertTemplate(template: TodoTemplateEntity) = todoTemplateDao.upsert(template)

    // 반복 Todo 템플릿 생성 — 로컬 저장 (원격 템플릿 스키마는 이 task 범위 밖이라 로컬 전용)
    suspend fun createTemplate(
        userId: String,
        title: String,
        rrule: String,
        financeIsLinked: Boolean = false,
        financeType: String? = null,
        financeCategory: String? = null,
        financeAmount: Long? = null,
    ): TodoTemplateEntity {
        val now = System.currentTimeMillis()
        val template = TodoTemplateEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            rrule = rrule,
            financeIsLinked = financeIsLinked,
            financeType = financeType,
            financeCategory = financeCategory,
            financeAmount = financeAmount,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        todoTemplateDao.upsert(template)
        return template
    }

    // Materializer가 생성한 인스턴스 일괄 반영 — 로컬(SSOT) → 알람 → 동기화 순서
    suspend fun upsertMaterialized(todos: List<TodoEntity>) {
        todoDao.upsertAll(todos)
        todos.forEach { todo ->
            alarmScheduler.scheduleForTodo(todo)
            syncSafe { remote.upsertTodo(todo) }
        }
    }

    suspend fun deactivateTemplate(id: String) = todoTemplateDao.deactivate(id, System.currentTimeMillis())

    fun observeByDate(date: String): Flow<List<TodoEntity>> = todoDao.observeByDate(date)

    suspend fun create(
        userId: String,
        title: String,
        dueDate: String? = null,
        financeIsLinked: Boolean = false,
        financeType: String? = null,
        financeCategory: String? = null,
        financeAmount: Long? = null,
        templateId: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): TodoEntity {
        val now = System.currentTimeMillis()
        val entity = TodoEntity(
            id = id,
            userId = userId,
            templateId = templateId,
            title = title,
            isCompleted = false,
            dueDate = dueDate,
            completedAt = null,
            financeIsLinked = financeIsLinked,
            financeType = financeType,
            financeCategory = financeCategory,
            financeAmount = financeAmount,
            linkedFinanceId = null,
            createdAt = now,
            updatedAt = now,
        )
        todoDao.upsert(entity)
        syncSafe { remote.upsertTodo(entity) }
        alarmScheduler.scheduleForTodo(entity)
        return entity
    }

    // Todo 완료 처리 — Finance 연동 시 Batch Write로 원자적 처리
    suspend fun complete(todo: TodoEntity, amount: Long? = null): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val completedTodo = todo.copy(
            isCompleted = true,
            completedAt = now,
            updatedAt = now,
            financeAmount = amount ?: todo.financeAmount,
        )

        if (todo.financeIsLinked) {
            val finance = FinanceEntity(
                id = UUID.randomUUID().toString(),
                userId = todo.userId,
                type = todo.financeType ?: "EXPENSE",
                amount = completedTodo.financeAmount ?: 0L,
                category = todo.financeCategory ?: "미분류",
                date = todo.dueDate ?: today(),
                note = null,
                sourceTodoId = todo.id,
                isExcluded = false,
                createdAt = now,
                updatedAt = now,
            )
            val linkedTodo = completedTodo.copy(linkedFinanceId = finance.id)

            // 로컬 먼저 (Offline-First)
            todoDao.upsert(linkedTodo)
            financeDao.upsert(finance)

            // 원자적 원격 동기화
            syncSafe { remote.completeTodoWithFinance(linkedTodo, finance) }
        } else {
            todoDao.upsert(completedTodo)
            syncSafe { remote.upsertTodo(completedTodo) }
        }

        // 완료된 Todo는 리마인더가 필요 없으므로 알람 취소
        alarmScheduler.cancel(todo.id)
    }

    // Todo Uncheck — Finance Soft Delete (통계 제외)
    suspend fun uncheck(todo: TodoEntity) {
        val now = System.currentTimeMillis()
        val unchecked = todo.copy(isCompleted = false, completedAt = null, updatedAt = now)
        todoDao.upsert(unchecked)

        todo.linkedFinanceId?.let { financeId ->
            financeDao.excludeByTodoId(todo.id)
            syncSafe { remote.uncheckTodoWithFinance(todo.id, financeId, now) }
        } ?: syncSafe { remote.upsertTodo(unchecked) }

        // 다시 미완료가 됐으니 알람 복구 (마감일이 지났으면 내부에서 자동 스킵)
        alarmScheduler.scheduleForTodo(unchecked)
    }

    // Todo 삭제 — Finance는 연결 고리만 해제, 데이터 유지 (PRD 2.2 정책)
    suspend fun delete(todo: TodoEntity) {
        val now = System.currentTimeMillis()
        todoDao.softDelete(todo.id, now)
        financeDao.unlinkTodo(todo.id)
        syncSafe { remote.deleteTodoUnlinkFinance(todo.id, todo.linkedFinanceId, now) }
        alarmScheduler.cancel(todo.id)
    }

    private fun today(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    private suspend fun syncSafe(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("sync_target", "todos")
                recordException(e)
                log("sync_failed")
            }
        }
    }
}
