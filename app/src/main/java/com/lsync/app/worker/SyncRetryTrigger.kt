package com.lsync.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// 동기화 실패가 기록되면 네트워크 연결 시 재전송하도록 일회성 Work를 예약한다.
@Singleton
class SyncRetryTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncRetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // KEEP: 실패가 연달아 기록돼도 대기·실행 중인 워커를 유지해 백오프가 리셋되지 않게 한다.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "sync_retry"
        private const val BACKOFF_SECONDS = 30L
    }
}
