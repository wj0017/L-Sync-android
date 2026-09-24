package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// sync_log의 미해결 실패를 Room 현재 상태로 재전송. 남은 실패는 WorkManager 지수 백오프에 맡긴다.
@HiltWorker
class SyncRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 미로그인이면 재전송 대상이 없다 — retry()를 반환하면 로그인할 때까지 계속 깨어난다.
        val uid = authRepository.currentUserId ?: return Result.success()
        syncRepository.retryFailed(uid)
        // 시작할 때 읽은 목록 뒤에 기록된 실패도 확인한다. 마지막 조회 이후의 창은
        // 앱 시작 시 SyncRetryTrigger.schedule()로 회수한다(KEEP은 RUNNING 중 예약을 버림).
        val hasRetryable = syncRepository.hasRetryableFailures(uid)
        return if (shouldRetrySync(runAttemptCount, hasRetryable)) Result.retry() else Result.success()
    }
}

// 최초 실행을 포함해 최대 5회. 권한 오류 같은 영구 실패는 로그를 남기고 자동 시도를 멈춘다.
internal fun shouldRetrySync(runAttemptCount: Int, hasUnresolved: Boolean): Boolean =
    hasUnresolved && runAttemptCount + 1 < 5
