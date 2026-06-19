package com.lsync.app.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.TodoEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun events() = firestore.collection("events")
    private fun todos() = firestore.collection("todos")
    private fun finance() = firestore.collection("finance")
    private fun budgets() = firestore.collection("budgets")

    suspend fun upsertEvent(entity: EventEntity) {
        events().document(entity.id).set(entity.toMap(), SetOptions.merge()).await()
    }

    suspend fun deleteEvent(id: String) {
        events().document(id).delete().await()
    }

    suspend fun upsertTodo(entity: TodoEntity) {
        todos().document(entity.id).set(entity.toMap(), SetOptions.merge()).await()
    }

    suspend fun softDeleteTodo(id: String, now: Long) {
        todos().document(id).update("deletedAt", now, "updatedAt", now).await()
    }

    // Todo 완료 + Finance 생성을 원자적으로 처리 (PRD 트랜잭션 정책)
    suspend fun completeTodoWithFinance(
        todo: TodoEntity,
        finance: FinanceEntity,
    ) {
        firestore.runBatch { batch ->
            batch.set(todos().document(todo.id), todo.toMap(), SetOptions.merge())
            batch.set(finance().document(finance.id), finance.toMap(), SetOptions.merge())
        }.await()
    }

    // Todo Uncheck → Finance Soft Delete 원자적 처리
    suspend fun uncheckTodoWithFinance(todoId: String, financeId: String, now: Long) {
        firestore.runBatch { batch ->
            batch.update(todos().document(todoId), mapOf("isCompleted" to false, "completedAt" to null, "updatedAt" to now))
            batch.update(finance().document(financeId), mapOf("isExcluded" to true, "updatedAt" to now))
        }.await()
    }

    // Todo 삭제 → Finance sourceTodoId 해제 원자적 처리
    suspend fun deleteTodoUnlinkFinance(todoId: String, financeId: String?, now: Long) {
        firestore.runBatch { batch ->
            batch.update(todos().document(todoId), mapOf("deletedAt" to now, "updatedAt" to now))
            if (financeId != null) {
                batch.update(finance().document(financeId), mapOf("sourceTodoId" to null, "updatedAt" to now))
            }
        }.await()
    }

    suspend fun upsertFinance(entity: FinanceEntity) {
        finance().document(entity.id).set(entity.toMap(), SetOptions.merge()).await()
    }

    suspend fun deleteFinance(id: String) {
        finance().document(id).delete().await()
    }

    // 예산은 soft delete(deletedAt)만 사용 — upsert로 한도 설정·삭제 모두 전파.
    suspend fun upsertBudget(entity: BudgetEntity) {
        budgets().document(entity.id).set(entity.toMap(), SetOptions.merge()).await()
    }

    // ── 복원 동기화(pull): 원격 컬렉션을 userId로 필터해 엔티티로 복원 ──
    // 개별 문서 매핑 실패는 건너뛰어(전체 복원이 한 문서 스키마 불일치로 막히지 않게) 한다.

    suspend fun fetchEvents(userId: String): List<EventEntity> =
        events().whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { it.toEventEntity() }

    suspend fun fetchTodos(userId: String): List<TodoEntity> =
        todos().whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { it.toTodoEntity() }

    suspend fun fetchFinance(userId: String): List<FinanceEntity> =
        finance().whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { it.toFinanceEntity() }

    suspend fun fetchBudgets(userId: String): List<BudgetEntity> =
        budgets().whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { it.toBudgetEntity() }

    private fun EventEntity.toMap() = mapOf(
        "id" to id, "userId" to userId, "title" to title, "isAllDay" to isAllDay,
        "startDate" to startDate, "endDate" to endDate, "timezone" to timezone,
        "rrule" to rrule, "exdatesJson" to exdatesJson, "overridesJson" to overridesJson,
        "hasAlarm" to hasAlarm, "createdAt" to createdAt, "updatedAt" to updatedAt,
        "deletedAt" to deletedAt,
    )

    private fun TodoEntity.toMap() = mapOf(
        "id" to id, "userId" to userId, "templateId" to templateId, "title" to title,
        "isCompleted" to isCompleted, "dueDate" to dueDate, "completedAt" to completedAt,
        "financeIsLinked" to financeIsLinked, "financeType" to financeType,
        "financeCategory" to financeCategory, "financeAmount" to financeAmount,
        "linkedFinanceId" to linkedFinanceId, "createdAt" to createdAt,
        "updatedAt" to updatedAt, "deletedAt" to deletedAt,
    )

    private fun FinanceEntity.toMap() = mapOf(
        "id" to id, "userId" to userId, "type" to type, "amount" to amount,
        "category" to category, "date" to date, "note" to note,
        "sourceTodoId" to sourceTodoId, "isExcluded" to isExcluded,
        "settlementGroupId" to settlementGroupId,
        "createdAt" to createdAt, "updatedAt" to updatedAt,
    )

    private fun BudgetEntity.toMap() = mapOf(
        "id" to id, "userId" to userId, "category" to category,
        "limitAmount" to limitAmount, "createdAt" to createdAt,
        "updatedAt" to updatedAt, "deletedAt" to deletedAt,
    )

    // toMap()과 1:1 대응하는 역방향(문서→엔티티) 매핑.
    private fun DocumentSnapshot.toEventEntity(): EventEntity? = runCatching {
        EventEntity(
            id = getString("id") ?: return null,
            userId = getString("userId") ?: return null,
            title = getString("title") ?: "",
            isAllDay = getBoolean("isAllDay") ?: false,
            startDate = getString("startDate") ?: "",
            endDate = getString("endDate"),
            timezone = getString("timezone"),
            rrule = getString("rrule"),
            exdatesJson = getString("exdatesJson"),
            overridesJson = getString("overridesJson"),
            hasAlarm = getBoolean("hasAlarm") ?: false,
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            deletedAt = getLong("deletedAt"),
        )
    }.getOrNull()

    private fun DocumentSnapshot.toTodoEntity(): TodoEntity? = runCatching {
        TodoEntity(
            id = getString("id") ?: return null,
            userId = getString("userId") ?: return null,
            templateId = getString("templateId"),
            title = getString("title") ?: "",
            isCompleted = getBoolean("isCompleted") ?: false,
            dueDate = getString("dueDate"),
            completedAt = getLong("completedAt"),
            financeIsLinked = getBoolean("financeIsLinked") ?: false,
            financeType = getString("financeType"),
            financeCategory = getString("financeCategory"),
            financeAmount = getLong("financeAmount"),
            linkedFinanceId = getString("linkedFinanceId"),
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            deletedAt = getLong("deletedAt"),
        )
    }.getOrNull()

    private fun DocumentSnapshot.toFinanceEntity(): FinanceEntity? = runCatching {
        FinanceEntity(
            id = getString("id") ?: return null,
            userId = getString("userId") ?: return null,
            type = getString("type") ?: "",
            amount = getLong("amount") ?: 0L,
            category = getString("category") ?: "",
            date = getString("date") ?: "",
            note = getString("note"),
            sourceTodoId = getString("sourceTodoId"),
            isExcluded = getBoolean("isExcluded") ?: false,
            settlementGroupId = getString("settlementGroupId"),
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }.getOrNull()

    private fun DocumentSnapshot.toBudgetEntity(): BudgetEntity? = runCatching {
        BudgetEntity(
            id = getString("id") ?: return null,
            userId = getString("userId") ?: return null,
            category = getString("category") ?: "",
            limitAmount = getLong("limitAmount") ?: 0L,
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            deletedAt = getLong("deletedAt"),
        )
    }.getOrNull()
}
