package com.lsync.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lsync.app.notification.ReminderScheduler
import com.lsync.app.worker.EventAlarmRefreshWorker
import com.lsync.app.worker.TodoMaterializerWorker
import com.lsync.app.worker.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LSyncApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderScheduler: ReminderScheduler

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
    }
}
