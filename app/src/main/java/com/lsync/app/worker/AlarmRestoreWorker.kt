package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.notification.AlarmScheduler
import com.lsync.app.notification.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

// 재부팅·앱 업데이트 후 Room에서 미래 알람이 설정된 항목들을 읽어 AlarmManager에 재등록
@HiltWorker
class AlarmRestoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val alarmScheduler: AlarmScheduler,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fromDate = LocalDate.now().toString()

        eventDao.getFutureAlarmedEvents(fromDate).forEach { alarmScheduler.scheduleForEvent(it) }
        todoDao.getFutureAlarmedTodos(fromDate).forEach { alarmScheduler.scheduleForTodo(it) }

        // 정기 리마인더(통독·소비 요약)도 함께 복원. 설정 기반이라 멱등하다.
        reminderScheduler.syncAll()

        return Result.success()
    }
}
