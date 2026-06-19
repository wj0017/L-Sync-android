package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val remote: FirestoreDataSource,
) {
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>> = dao.observeBudgets(userId)

    // 한도 설정/수정 — 결정론적 id로 upsert(멱등). 로컬 먼저 저장, 원격은 syncSafe.
    suspend fun setBudget(userId: String, category: String, limitAmount: Long) {
        val id = "${userId}_${category}"
        val now = System.currentTimeMillis()
        val existing = dao.getById(id)
        val entity = BudgetEntity(
            id = id,
            userId = userId,
            category = category,
            limitAmount = limitAmount,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.upsert(entity)
        syncSafe { remote.upsertBudget(entity) }
    }

    // soft delete + deletedAt 원격 전파 (pull upsert-only라 hard delete는 다른 기기에 반영 안 됨)
    suspend fun deleteBudget(id: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
        val deleted = dao.getById(id) ?: return
        syncSafe { remote.upsertBudget(deleted) }
    }

    // 네트워크 에러는 Crashlytics에 기록하고 로컬은 이미 저장됐으므로 조용히 실패
    private suspend fun syncSafe(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("sync_target", "budgets")
                recordException(e)
                log("sync_failed")
            }
        }
    }
}
