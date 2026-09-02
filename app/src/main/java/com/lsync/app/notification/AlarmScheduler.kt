package com.lsync.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.recurrence.nextOccurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 엔티티 단위 등록 — 트리거 시각 계산은 이 메서드들 안에만 존재한다.
    // 라이브 등록(Step 1)과 재부팅 복원(Step 2)이 이 메서드를 공유해 로직 드리프트를 방지한다.
    fun scheduleForEvent(event: EventEntity) {
        runCatching {
            if (!event.hasAlarm) {
                cancelEvent(event.id)
                return
            }
            // 반복 일정은 항상 "다음 1개" 발생만 등록(AlarmManager는 one-shot).
            // rrule 직접 파싱 없이 Step 0 엔진(nextOccurrence)으로 다음 발생을 구한다.
            // 다음 발생이 없으면(UNTIL 지남 등) 취소. 시각 계산은 아래 단일 식 재사용.
            val effectiveStart = if (event.rrule == null) {
                event.startDate
            } else {
                val next = nextOccurrence(event, afterDate = LocalDate.now().toString())
                if (next == null) {
                    cancelEvent(event.id)
                    return
                }
                next.startDate
            }
            val triggerAtMillis = if (event.isAllDay) {
                dateToReminderMillis(effectiveStart)
            } else {
                OffsetDateTime.parse(effectiveStart).toInstant().toEpochMilli()
            }
            if (triggerAtMillis <= System.currentTimeMillis()) return
            scheduleEventAlarm(event.id, event.title, triggerAtMillis)
        }
    }

    fun scheduleForTodo(todo: TodoEntity) {
        runCatching {
            if (todo.isCompleted || todo.dueDate == null) {
                cancelTodo(todo.id)
                return
            }
            val triggerAtMillis = dateToReminderMillis(todo.dueDate)
            if (triggerAtMillis <= System.currentTimeMillis()) return
            scheduleTodoAlarm(todo.id, todo.title, triggerAtMillis)
        }
    }

    fun scheduleEventAlarm(eventId: String, title: String, triggerAtMillis: Long) {
        val intent = buildIntent(eventId, title, TYPE_EVENT)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(TYPE_EVENT, eventId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(triggerAtMillis, pendingIntent)
    }

    fun scheduleTodoAlarm(todoId: String, title: String, triggerAtMillis: Long) {
        val intent = buildIntent(todoId, title, TYPE_TODO)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(TYPE_TODO, todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(triggerAtMillis, pendingIntent)
    }

    // 레거시 코드(id.hashCode())도 함께 취소 — 타입 프리픽스 도입 전에 등록된 알람 잔존 방지.
    fun cancelEvent(id: String) = cancelCodes(requestCode(TYPE_EVENT, id), id.hashCode())

    fun cancelTodo(id: String) = cancelCodes(requestCode(TYPE_TODO, id), id.hashCode())

    // Event/Todo가 같은 requestCode 공간을 쓰면 hashCode 충돌 시 서로의 알람을 덮어쓴다 — 타입 프리픽스로 분리.
    private fun requestCode(type: String, id: String) = "$type:$id".hashCode()

    private fun cancelCodes(vararg codes: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        codes.forEach { code ->
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    // 정확 알람 권한(Android 12+) 미허용 시 inexact 폴백 — AlarmCompat.setAlarmCompat에 단일화.
    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) =
        alarmManager.setAlarmCompat(triggerAtMillis, pendingIntent)

    // YYYY-MM-DD → 그 날 09:00(시스템 타임존) epoch millis. Floating Time 유지.
    private fun dateToReminderMillis(dateStr: String): Long =
        LocalDate.parse(dateStr)
            .atTime(REMINDER_HOUR, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun buildIntent(id: String, title: String, type: String) =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TYPE, type)
        }

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_TYPE = "alarm_type"
        const val TYPE_EVENT = "event"
        const val TYPE_TODO = "todo"
        const val CHANNEL_ID = "lsync_alarms"
        const val REMINDER_HOUR = 9
    }
}
