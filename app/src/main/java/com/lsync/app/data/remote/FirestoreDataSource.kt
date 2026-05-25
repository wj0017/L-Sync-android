package com.lsync.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
}
