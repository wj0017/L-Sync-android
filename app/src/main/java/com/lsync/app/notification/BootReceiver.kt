package com.lsync.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.lsync.app.worker.AlarmRestoreWorker
import java.util.concurrent.TimeUnit

// 재부팅 시 AlarmManager에 등록된 알람이 초기화되므로 WorkManager로 복구
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val restoreRequest = OneTimeWorkRequestBuilder<AlarmRestoreWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS) // 부팅 직후 약간 대기
            .setConstraints(Constraints.Builder().build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "alarm_restore",
            ExistingWorkPolicy.REPLACE,
            restoreRequest,
        )
    }
}
