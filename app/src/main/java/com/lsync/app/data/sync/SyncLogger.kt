package com.lsync.app.data.sync

import com.lsync.app.data.local.dao.SyncLogDao
import com.lsync.app.data.local.entity.SyncLogEntity
import com.lsync.app.worker.SyncRetryTrigger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// 동기화 대상 구분 — sync_log.target에 저장되는 문자열의 단일 출처.
enum class SyncTarget(val key: String) {
    EVENTS("events"),
    TODOS("todos"),
    FINANCE("finance"),
    BUDGETS("budgets"),
    PULL("pull"),
}

// 원격 동기화 시도를 Room에 기록한다. 로컬 전용 진단 — Firestore에 올리지 않는다.
// AuthRepository를 주입받지 않는다(DI 순환). userId는 호출부가 엔티티에서 넘긴다.
@Singleton
class SyncLogger @Inject constructor(
    private val dao: SyncLogDao,
    private val retryTrigger: SyncRetryTrigger,
) {
    // 가지치기 주기용 삽입 카운터 — 프로세스 수명 동안만 유지되면 충분하다.
    private val insertCount = AtomicInteger(0)

    suspend fun recordSuccess(target: SyncTarget, userId: String, entityId: String?) {
        record(target, userId, entityId, error = null)
    }

    suspend fun recordFailure(target: SyncTarget, userId: String, entityId: String?, error: Throwable) {
        record(target, userId, entityId, error)
        // 네트워크 연결 시 재전송 워커를 예약한다. 재시도 경로(SyncRepository.retryFailed)는 이 함수를 부르지 않는다.
        runCatching { retryTrigger.schedule() }
    }

    // 앱을 다시 켤 때도 만료 로그를 정리한다. 삽입이 50회 미만이면 주기적 가지치기만으로는
    // SourceMissing 배지가 기한 후에도 계속 남을 수 있다.
    suspend fun pruneExpired() {
        runCatching { dao.pruneOlderThan(System.currentTimeMillis() - RETENTION_MILLIS) }
    }

    // 기록 실패는 삼킨다 — 진단 기록 때문에 사용자 데이터 저장 흐름이 깨지면 안 된다.
    private suspend fun record(target: SyncTarget, userId: String, entityId: String?, error: Throwable?) {
        runCatching {
            val now = System.currentTimeMillis()
            dao.insert(
                SyncLogEntity(
                    userId = userId,
                    target = target.key,
                    entityId = entityId,
                    success = error == null,
                    errorType = error?.let { it::class.java.simpleName },
                    errorMessage = error?.message?.take(MAX_MESSAGE_LENGTH),
                    createdAt = now,
                    resolvedAt = null,
                )
            )
            // 매 삽입마다 DELETE를 돌리면 쓰기가 2배가 되므로 N회마다 1번만 정리한다.
            if (insertCount.incrementAndGet() % PRUNE_EVERY == 0) {
                dao.pruneOlderThan(now - RETENTION_MILLIS)
            }
        }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 200
        private const val PRUNE_EVERY = 50
        private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30)
    }
}
