package com.lsync.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lsync.app.notification.ReminderScheduler
import com.lsync.app.worker.EventAlarmRefreshWorker
import com.lsync.app.worker.TodoMaterializerWorker
import com.lsync.app.worker.WidgetRefreshWorker
import com.lsync.app.worker.SyncRetryTrigger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LSyncApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var syncRetryTrigger: SyncRetryTrigger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        TodoMaterializerWorker.enqueuePeriodicWork(this)
        EventAlarmRefreshWorker.enqueuePeriodicWork(this)
        WidgetRefreshWorker.enqueuePeriodicWork(this)
        // 강제 종료·앱 업데이트로 알람이 유실돼도 앱 실행 시 복구한다(멱등).
        reminderScheduler.syncAll()
        // KEEP이 실행 중 버린 예약은 다음 앱 시작에 회수한다. 미해결이 없으면 워커가 즉시 종료한다.
        syncRetryTrigger.schedule()
    }
}
