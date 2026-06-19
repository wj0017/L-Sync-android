package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.remote.FirestoreDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val remote: FirestoreDataSource,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
    private val budgetDao: BudgetDao,
) {
    // 원격 전체를 pull해 last-write-wins로 Room에 머지(복원).
    // upsert-only: 로컬이 같거나 더 최신이면 보존, 로컬 전용 데이터는 삭제하지 않는다.
    // 삭제는 별도 tombstone 없이 처리된다 — Event/Finance는 원격 hard delete라 fetch에 없고,
    // Todo는 deletedAt이 채워진 채 와서 그대로 복원되므로 fetch한 것을 upsert하면 삭제도 반영된다.
    suspend fun pullAll(userId: String) {
        syncSafe {
            for (event in remote.fetchEvents(userId)) {
                val local = eventDao.getById(event.id)
                if (local == null || event.updatedAt > local.updatedAt) {
                    eventDao.upsert(event)
                }
            }
        }
        syncSafe {
            for (todo in remote.fetchTodos(userId)) {
                val local = todoDao.getById(todo.id)
                if (local == null || todo.updatedAt > local.updatedAt) {
                    todoDao.upsert(todo)
                }
            }
        }
        syncSafe {
            for (finance in remote.fetchFinance(userId)) {
                val local = financeDao.getById(finance.id)
                if (local == null || finance.updatedAt > local.updatedAt) {
                    financeDao.upsert(finance)
                }
            }
        }
        syncSafe {
            // deletedAt이 채워져 오면 upsert로 soft delete 복원.
            for (budget in remote.fetchBudgets(userId)) {
                val local = budgetDao.getById(budget.id)
                if (local == null || budget.updatedAt > local.updatedAt) {
                    budgetDao.upsert(budget)
                }
            }
        }
    }

    // 네트워크/권한 에러는 Crashlytics에 기록하고 조용히 통과 (앱 흐름을 막지 않음).
    private suspend fun syncSafe(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("sync_target", "pull")
                recordException(e)
                log("sync_failed")
            }
        }
    }
}
