package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.SyncLogDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.data.sync.SyncLogger
import com.lsync.app.data.sync.SyncTarget
import com.lsync.app.notification.AlarmScheduler
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RetryResult(val attempted: Int, val resolved: Int, val failed: Int)

@Singleton
class SyncRepository @Inject constructor(
    private val remote: FirestoreDataSource,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
    private val budgetDao: BudgetDao,
    private val todoTemplateDao: TodoTemplateDao,
    private val alarmScheduler: AlarmScheduler,
    private val syncLogger: SyncLogger,
    private val syncLogDao: SyncLogDao,
) {
    // 원격 전체를 pull해 last-write-wins로 Room에 머지(복원).
    // upsert-only: 로컬이 같거나 더 최신이면 보존, 로컬 전용 데이터는 삭제하지 않는다.
    // 삭제는 모든 컬렉션이 tombstone(deletedAt)으로 전파된다 — deletedAt이 채워진 문서를
    // 그대로 upsert하면 로컬 쿼리(deletedAt IS NULL 필터)에서 사라져 삭제가 반영된다.
    suspend fun pullAll(userId: String) {
        syncSafe(SyncTarget.PULL, userId, entityId = null) {
            for (event in remote.fetchEvents(userId)) {
                val local = eventDao.getById(event.id)
                if (local == null || event.updatedAt > local.updatedAt) {
                    eventDao.upsert(event)
                }
            }
        }
        syncSafe(SyncTarget.PULL, userId, entityId = null) {
            for (todo in remote.fetchTodos(userId)) {
                val local = todoDao.getById(todo.id)
                if (local == null || todo.updatedAt > local.updatedAt) {
                    todoDao.upsert(todo)
                }
            }
        }
        syncSafe(SyncTarget.PULL, userId, entityId = null) {
            for (finance in remote.fetchFinance(userId)) {
                val local = financeDao.getById(finance.id)
                if (local == null || finance.updatedAt > local.updatedAt) {
                    financeDao.upsert(finance)
                }
            }
        }
        syncSafe(SyncTarget.PULL, userId, entityId = null) {
            // deletedAt이 채워져 오면 upsert로 soft delete 복원.
            for (budget in remote.fetchBudgets(userId)) {
                val local = budgetDao.getById(budget.id)
                if (local == null || budget.updatedAt > local.updatedAt) {
                    budgetDao.upsert(budget)
                }
            }
        }
    }

    // 미해결 실패를 Room 현재 상태로 재전송한다.
    // 이 경로는 sync_log에 새 행을 남기지 않는다(재시도 루프·통계 오염 방지).
    // 원래 연산(batch.update)을 재현하지 않고 Room 행을 그대로 set(merge)한다 — 문서가 없어도 성공하고,
    // updatedAt을 새로 찍지 않으므로 원격 LWW 기준이 보존된다.
    suspend fun retryFailed(userId: String): RetryResult {
        val logs = syncLogDao.getUnresolvedFailures(userId)
        var resolved = 0
        var failed = 0

        // pull 행은 여러 개여도 pullAll 1회로 전부 해소한다.
        val (pullLogs, pushLogs) = logs.partition { it.target == SyncTarget.PULL.key }
        if (pullLogs.isNotEmpty()) {
            val ok = runCatching { pullWithoutLogging(userId) }.isSuccess
            if (ok) {
                val now = System.currentTimeMillis()
                pullLogs.forEach { syncLogDao.markResolved(it.id, now) }
                resolved += pullLogs.size
            } else {
                failed += pullLogs.size
            }
        }

        // 한 건의 실패가 나머지 재전송을 막지 않도록 항목별로 예외를 삼킨다.
        for (log in pushLogs) {
            val ok = runCatching { resend(log.target, log.entityId) }.isSuccess
            if (ok) {
                syncLogDao.markResolved(log.id, System.currentTimeMillis())
                resolved++
            } else {
                failed++
            }
        }
        return RetryResult(attempted = logs.size, resolved = resolved, failed = failed)
    }

    // Room 행이 없거나 entityId가 없으면 되살릴 원본이 없으므로 해소된 것으로 본다(경고 영구 잔류 방지).
    private suspend fun resend(target: String, entityId: String?) {
        if (entityId == null) return
        when (target) {
            SyncTarget.EVENTS.key -> eventDao.getById(entityId)?.let { remote.upsertEvent(it) }
            SyncTarget.TODOS.key -> todoDao.getById(entityId)?.let { todo ->
                remote.upsertTodo(todo)
                // 완료·미완료 연동 배치의 Finance 쪽도 Room 현재 상태로 함께 올린다.
                todo.linkedFinanceId
                    ?.let { financeDao.getById(it) }
                    ?.let { remote.upsertFinance(it) }
            }
            SyncTarget.FINANCE.key -> financeDao.getById(entityId)?.let { remote.upsertFinance(it) }
            SyncTarget.BUDGETS.key -> budgetDao.getById(entityId)?.let { remote.upsertBudget(it) }
        }
    }

    // pullAll과 같은 LWW 머지지만 syncSafe(로깅)를 거치지 않고 예외를 그대로 던진다.
    // pullAll은 실패를 삼키고 새 로그를 남기므로 재시도 경로에서 호출하면 성공 여부를 알 수 없고 루프가 생긴다.
    private suspend fun pullWithoutLogging(userId: String) {
        for (event in remote.fetchEvents(userId)) {
            val local = eventDao.getById(event.id)
            if (local == null || event.updatedAt > local.updatedAt) eventDao.upsert(event)
        }
        for (todo in remote.fetchTodos(userId)) {
            val local = todoDao.getById(todo.id)
            if (local == null || todo.updatedAt > local.updatedAt) todoDao.upsert(todo)
        }
        for (finance in remote.fetchFinance(userId)) {
            val local = financeDao.getById(finance.id)
            if (local == null || finance.updatedAt > local.updatedAt) financeDao.upsert(finance)
        }
        for (budget in remote.fetchBudgets(userId)) {
            val local = budgetDao.getById(budget.id)
            if (local == null || budget.updatedAt > local.updatedAt) budgetDao.upsert(budget)
        }
    }

    // 로그아웃 시 동기화 대상 로컬 데이터를 비운다(계정 전환 시 이전 사용자 데이터 노출 차단).
    // Offline-First라 재로그인 시 pullAll(uid)로 Firestore에서 복원되므로 데이터 손실이 없다.
    // reading_plan/memos는 로컬 전용(미동기화·복원 불가)이라 절대 비우지 않는다.
    suspend fun clearLocalUserData() {
        // 삭제보다 먼저 예약된 알람을 취소한다 — 삭제 후엔 ID를 조회할 수 없어 알람이 영구 누수된다.
        // 한 항목 실패가 전체 정리를 막지 않도록 runCatching으로 감싼다.
        runCatching {
            val today = LocalDate.now().toString()
            eventDao.getFutureAlarmedEvents(today).forEach { alarmScheduler.cancelEvent(it.id) }
            todoDao.getFutureAlarmedTodos(today).forEach { alarmScheduler.cancelTodo(it.id) }
        }
        // 동기화로 복원 가능한 테이블만 비운다.
        eventDao.clearAll()
        todoDao.clearAll()
        financeDao.clearAll()
        budgetDao.clearAll()
        todoTemplateDao.clearAll()
    }

    // 네트워크/권한 에러는 Crashlytics에 기록하고 조용히 통과 (앱 흐름을 막지 않음).
    private suspend fun syncSafe(
        target: SyncTarget,
        userId: String,
        entityId: String?,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            syncLogger.recordSuccess(target, userId, entityId)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("sync_target", "pull")
                recordException(e)
                log("sync_failed")
            }
            syncLogger.recordFailure(target, userId, entityId, e)
        }
    }
}
