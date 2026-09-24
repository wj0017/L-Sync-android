package com.lsync.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lsync.app.notification.ReminderScheduler
import com.lsync.app.data.sync.SyncLogger
import com.lsync.app.di.ApplicationScope
import com.lsync.app.worker.EventAlarmRefreshWorker
import com.lsync.app.worker.TodoMaterializerWorker
import com.lsync.app.worker.WidgetRefreshWorker
import com.lsync.app.worker.SyncRetryTrigger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LSyncApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var syncRetryTrigger: SyncRetryTrigger
    @Inject lateinit var syncLogger: SyncLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

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
        // 새 기록이 없어도 만료된 원본 없음 로그가 다음 앱 시작에 정리되도록 한다.
        appScope.launch { syncLogger.pruneExpired() }
    }
}
