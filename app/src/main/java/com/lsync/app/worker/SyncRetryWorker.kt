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
        val result = syncRepository.retryFailed(uid)
        return if (result.failed > 0) Result.retry() else Result.success()
    }
}
