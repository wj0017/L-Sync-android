package com.lsync.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.lsync.app.worker.AlarmRestoreWorker
import java.util.concurrent.TimeUnit

// 재부팅·앱 업데이트 시 AlarmManager에 등록된 알람이 초기화되므로 WorkManager로 복구.
// MY_PACKAGE_REPLACED를 함께 받는 이유: 업데이트 후 사용자가 앱을 열지 않으면
// 정기 리마인더가 영영 재등록되지 않는다.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED" &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

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
