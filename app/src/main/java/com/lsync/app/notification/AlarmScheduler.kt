package com.lsync.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lsync.app.data.alarm.AlarmCommandQueue
import com.lsync.app.data.alarm.AlarmRegistrar
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.alarm.AlarmReliabilityPolicy
import com.lsync.app.data.recurrence.expandEvent
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.TodoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandQueue: AlarmCommandQueue,
    private val registrar: AlarmRegistrar,
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 엔티티 단위 등록 — 트리거 시각 계산은 이 메서드들 안에만 존재한다.
    // 라이브 등록(Step 1)과 재부팅 복원(Step 2)이 이 메서드를 공유해 로직 드리프트를 방지한다.
    fun scheduleForEvent(event: EventEntity) {
        commandQueue.submit { applyEvent(event.id) }
    }

    suspend fun scheduleForEventAndAwait(id: String) = commandQueue.submitAndAwait { applyEvent(id) }

    private suspend fun applyEvent(id: String) {
        val key = "$TYPE_EVENT:$id"
        val event = eventDao.getById(id)
        val next = event?.let { nextAlarmForEvent(it, Instant.now(), ZoneId.systemDefault()) }
        if (next == null) {
            registrar.cancel(key, AlarmReceiver::class.java, requestCode(TYPE_EVENT, id))
            cancelCodes(requestCode(TYPE_EVENT, id), id.hashCode())
        } else {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val expected = event?.let {
                expandEvent(it, today.minusDays(2).toString(), today.plusDays(3).toString())
                    .filter { occurrence -> occurrence.hasAlarm }
                    .map { occurrence -> occurrenceMillis(occurrence, zone) }
                    .filter { at -> at >= now && at < now + AlarmReliabilityPolicy.EXPECTATION_HORIZON_MS }
            }.orEmpty()
            registrar.replace(key, next.triggerAtMillis, event?.userId, AlarmReceiver::class.java,
                requestCode(TYPE_EVENT, id), mapOf(EXTRA_ID to id, EXTRA_TITLE to next.title, EXTRA_TYPE to TYPE_EVENT), expected)
            cancelCodes(requestCode(TYPE_EVENT, id), id.hashCode())
        }
    }

    fun scheduleForTodo(todo: TodoEntity) {
        commandQueue.submit { applyTodo(todo.id) }
    }

    suspend fun scheduleForTodoAndAwait(id: String) = commandQueue.submitAndAwait { applyTodo(id) }

    private suspend fun applyTodo(id: String) {
        val key = "$TYPE_TODO:$id"
        val todo = todoDao.getById(id)
        val triggerAtMillis = todo?.dueDate?.let(::dateToReminderMillis)
        if (todo == null || todo.deletedAt != null || todo.isCompleted || triggerAtMillis == null ||
            triggerAtMillis <= System.currentTimeMillis()) {
            registrar.cancel(key, AlarmReceiver::class.java, requestCode(TYPE_TODO, id))
            cancelCodes(requestCode(TYPE_TODO, id), id.hashCode())
        } else {
            registrar.replace(key, triggerAtMillis, todo.userId, AlarmReceiver::class.java,
                requestCode(TYPE_TODO, id), mapOf(EXTRA_ID to id, EXTRA_TITLE to todo.title, EXTRA_TYPE to TYPE_TODO))
            cancelCodes(requestCode(TYPE_TODO, id), id.hashCode())
        }
    }

    // 로그아웃은 원격 엔티티를 삭제하지 않으므로 별도 취소 명령이 필요하다.
    fun cancelForSignOut(type: String, id: String) {
        require(type == TYPE_EVENT || type == TYPE_TODO)
        commandQueue.submit { applySignOutCancel(type, id) }
    }

    suspend fun cancelForSignOutAndAwait(type: String, id: String) {
        require(type == TYPE_EVENT || type == TYPE_TODO)
        commandQueue.submitAndAwait { applySignOutCancel(type, id) }
    }

    private suspend fun applySignOutCancel(type: String, id: String) {
        registrar.cancel("$type:$id", AlarmReceiver::class.java, requestCode(type, id))
        cancelCodes(requestCode(type, id), id.hashCode())
    }

    // Event/Todo가 같은 requestCode 공간을 쓰면 hashCode 충돌 시 서로의 알람을 덮어쓴다 — 타입 프리픽스로 분리.
    private fun requestCode(type: String, id: String) = "$type:$id".hashCode()

    // 레거시 코드(id.hashCode())도 함께 취소 — 타입 프리픽스 도입 전 알람 잔존 방지.
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

    // YYYY-MM-DD → 그 날 09:00(시스템 타임존) epoch millis. Floating Time 유지.
    private fun dateToReminderMillis(dateStr: String): Long =
        LocalDate.parse(dateStr)
            .atTime(REMINDER_HOUR, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

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
