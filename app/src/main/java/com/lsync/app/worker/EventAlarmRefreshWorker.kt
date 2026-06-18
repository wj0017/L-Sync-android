package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.notification.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// 반복 일정 알람 갱신 (Phase 15). AlarmManager는 one-shot이라 한 발생이 울린 뒤
// 다음 발생이 자동 등록되지 않는다. 일 1회 미래 알람 항목을 다시 scheduleForEvent에 흘려
// "다음 1개" 발생으로 알람을 갱신한다. 단발 일정은 같은 트리거로 재등록되어 무해(멱등).
@HiltWorker
class EventAlarmRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fromDate = LocalDate.now().toString()
        eventDao.getFutureAlarmedEvents(fromDate).forEach { alarmScheduler.scheduleForEvent(it) }
        return Result.success()
    }

    companion object {
        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<EventAlarmRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "event_alarm_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
