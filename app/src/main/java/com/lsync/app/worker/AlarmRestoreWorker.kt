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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
        val now = System.currentTimeMillis()
        val todayStr = LocalDate.now().toString()

        // 미래 알람이 설정된 이벤트 복구
        val futureEvents = eventDao.getById("") // TODO: 미래 알람 전용 쿼리로 교체
        // 실제 구현: eventDao.observeByDateRange(todayStr, "+30일").first()
        // 각 event.hasAlarm == true 이고 startDate > now 인 것들을 재등록

        return Result.success()
    }

    private fun String.toEpochMillis(): Long? = runCatching {
        LocalDateTime.parse(this.replace("+09:00", ""))
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
