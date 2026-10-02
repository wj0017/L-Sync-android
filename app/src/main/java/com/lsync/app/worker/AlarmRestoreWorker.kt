package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.lsync.app.data.alarm.AlarmCoverageManager
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.notification.AlarmScheduler
import com.lsync.app.notification.ReminderScheduler
import com.lsync.app.notification.ReminderType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class AlarmRestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val alarmScheduler: AlarmScheduler,
    private val reminderScheduler: ReminderScheduler,
    private val coverageManager: AlarmCoverageManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val startedAt = System.currentTimeMillis()
        return try {
            // Preserve the uncovered interval before any scheduler can close overdue rows.
            coverageManager.beforeRestore(startedAt)

            // A local date may lag an occurrence's trigger date across time zones.
            val fromDate = LocalDate.now().minusDays(2).toString()
            eventDao.getFutureAlarmedEvents(fromDate).forEach {
                alarmScheduler.scheduleForEventAndAwait(it.id)
            }
            todoDao.getFutureAlarmedTodos(fromDate).forEach {
                alarmScheduler.scheduleForTodoAndAwait(it.id)
            }
            ReminderType.values().forEach { reminderScheduler.scheduleNextAndAwait(it) }

            coverageManager.afterRestore(startedAt, System.currentTimeMillis())
            Result.success()
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            Result.retry()
        }
    }

    companion object {
        fun enqueueOnAppStart(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork(
                "alarm_restore",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AlarmRestoreWorker>().build(),
            )
            manager.enqueueUniquePeriodicWork(
                "alarm_coverage_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlarmRestoreWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
