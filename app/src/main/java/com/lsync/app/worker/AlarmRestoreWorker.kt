package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.notification.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// 재부팅 후 Room에서 미래 알람이 설정된 항목들을 읽어 AlarmManager에 재등록
@HiltWorker
class AlarmRestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // TODO: eventDao에 미래 알람 전용 쿼리 추가 후 구현
        // eventDao.observeByDateRange(LocalDate.now().toString(), ...).first()
        //   .filter { it.hasAlarm }
        //   .forEach { alarmScheduler.scheduleEventAlarm(...) }
        return Result.success()
    }
}
