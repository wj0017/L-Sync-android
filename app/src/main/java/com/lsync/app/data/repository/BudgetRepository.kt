package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val remote: FirestoreDataSource,
    @ApplicationScope private val appScope: CoroutineScope,
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

    // 원격 push는 앱 스코프에서 비동기 실행 — 오프라인이면 write Task가 서버 ack까지 완료되지
    // 않으므로 호출부를 막지 않는다. 에러는 Crashlytics 기록 후 조용히 실패(로컬은 이미 저장됨).
    private fun syncSafe(block: suspend () -> Unit) {
        appScope.launch {
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
}
