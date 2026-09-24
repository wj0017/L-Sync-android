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

data class RetryResult(val attempted: Int, val resolved: Int, val failed: Int, val missing: Int)

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
    // 엔티티 그룹별로 syncSafe를 나눈다 — 한 그룹의 실패가 나머지 복원을 막지 않는다.
    suspend fun pullAll(userId: String) {
        syncSafe(SyncTarget.PULL, userId, entityId = null) { mergeEvents(userId) }
        syncSafe(SyncTarget.PULL, userId, entityId = null) { mergeTodos(userId) }
        syncSafe(SyncTarget.PULL, userId, entityId = null) { mergeFinance(userId) }
        syncSafe(SyncTarget.PULL, userId, entityId = null) { mergeBudgets(userId) }
    }

    // ── LWW 머지 단일 출처 ────────────────────────────────────────────────────
    // pullAll(로깅 있음)과 pullWithoutLogging(재시도 경로)이 함께 호출한다.
    // 사본을 만들지 마라 — 두 경로의 머지 기준이 갈라지면 "재시도로 받은 데이터"와
    // "평소 pull로 받은 데이터"가 조용히 달라진다.

    private suspend fun mergeEvents(userId: String) {
        for (event in remote.fetchEvents(userId)) {
            val local = eventDao.getById(event.id)
            if (local == null || event.updatedAt > local.updatedAt) {
                eventDao.upsert(event)
            }
        }
    }

    private suspend fun mergeTodos(userId: String) {
        for (todo in remote.fetchTodos(userId)) {
            val local = todoDao.getById(todo.id)
            if (local == null || todo.updatedAt > local.updatedAt) {
                todoDao.upsert(todo)
            }
        }
    }

    private suspend fun mergeFinance(userId: String) {
        for (finance in remote.fetchFinance(userId)) {
            val local = financeDao.getById(finance.id)
            if (local == null || finance.updatedAt > local.updatedAt) {
                financeDao.upsert(finance)
            }
        }
    }

    private suspend fun mergeBudgets(userId: String) {
        // deletedAt이 채워져 오면 upsert로 soft delete 복원.
        for (budget in remote.fetchBudgets(userId)) {
            val local = budgetDao.getById(budget.id)
            if (local == null || budget.updatedAt > local.updatedAt) {
                budgetDao.upsert(budget)
            }
        }
    }

    // 미해결 실패를 Room 현재 상태로 재전송한다.
    // 이 경로는 sync_log에 새 행을 남기지 않는다(재시도 루프·통계 오염 방지).
    // 원래 연산(batch.update)을 재현하지 않고 Room 현재 행을 조건부 트랜잭션으로 전송한다.
    suspend fun retryFailed(userId: String): RetryResult {
        val logs = syncLogDao.getUnresolvedFailures(userId)
        var resolved = 0
        var failed = 0
        var missing = 0

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
            val result = runCatching { resend(log.target, log.entityId) }
            val missingReason = result.getOrNull()
            if (result.isFailure) {
                failed++
            } else if (missingReason == null) {
                syncLogDao.markResolved(log.id, System.currentTimeMillis())
                resolved++
            } else {
                // 로그아웃 후 복원 전 등 원본이 없을 수 있다. 원격 성공으로 해소하지 않는다.
                syncLogDao.markSourceMissing(log.id, missingReason)
                missing++
            }
        }
        return RetryResult(attempted = logs.size, resolved = resolved, failed = failed, missing = missing)
    }

    // null이면 두 문서 모두 쓰였거나 최신 원격 문서 때문에 건너뛴 것. 문자열이면 로컬 원본 부재.
    // 원본 부재는 로그아웃 후 pull이 아직 끝나지 않은 상태일 수 있으므로 해소하지 않는다.
    private suspend fun resend(target: String, entityId: String?): String? {
        if (entityId == null) return "재전송 키 없음"
        return when (target) {
            SyncTarget.EVENTS.key -> {
                val event = eventDao.getByIdIncludingDeleted(entityId) ?: return "일정 원본 없음"
                remote.retryEvent(event)
                null
            }
            SyncTarget.TODOS.key -> {
                val todo = todoDao.getByIdIncludingDeleted(entityId) ?: return "할 일 원본 없음"
                val linkedFinance = todo.linkedFinanceId?.let { financeDao.getByIdIncludingDeleted(it) }
                if (todo.linkedFinanceId != null && linkedFinance == null) return "연결된 가계부 원본 없음"
                remote.retryTodo(todo, linkedFinance)
                null
            }
            SyncTarget.FINANCE.key -> {
                val finance = financeDao.getByIdIncludingDeleted(entityId) ?: return "가계부 원본 없음"
                remote.retryFinance(finance)
                null
            }
            SyncTarget.BUDGETS.key -> {
                val budget = budgetDao.getById(entityId) ?: return "예산 원본 없음"
                remote.retryBudget(budget)
                null
            }
            else -> "알 수 없는 동기화 대상: $target"
        }
    }

    suspend fun hasUnresolvedFailures(userId: String): Boolean = syncLogDao.getUnresolvedCount(userId) > 0

    // pullAll과 같은 머지를 syncSafe(로깅) 없이 수행하고 예외를 그대로 던진다.
    // pullAll은 실패를 삼키고 새 로그를 남기므로 재시도 경로에서 호출하면 성공 여부를 알 수 없고 루프가 생긴다.
    private suspend fun pullWithoutLogging(userId: String) {
        mergeEvents(userId)
        mergeTodos(userId)
        mergeFinance(userId)
        mergeBudgets(userId)
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
