package com.lsync.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lsync.app.data.repository.NotificationSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderType(val key: String) {
    BIBLE("bible"),
    SPENDING_DAILY("spending_daily"),
    SPENDING_WEEKLY("spending_weekly"),
    ;

    companion object {
        fun fromKey(key: String?): ReminderType? = values().firstOrNull { it.key == key }
    }
}

// 정기 리마인더(통독·소비 요약)의 AlarmManager 등록/취소.
// 트리거 시각 계산은 이 클래스의 nextDailyTrigger/nextWeeklyTrigger에만 존재한다 —
// 앱 시작(LSyncApplication)·재부팅 복원(AlarmRestoreWorker)·발화 후 재무장(ReminderReceiver)이
// 모두 같은 계산을 공유해 시각 드리프트를 방지한다(AlarmScheduler와 같은 원칙).
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: NotificationSettingsRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 설정을 읽어 3종을 전부 등록/취소한다. 몇 번을 호출해도 결과가 같다(멱등).
    fun syncAll() {
        ReminderType.values().forEach { scheduleNext(it) }
    }

    // 해당 리마인더의 "다음 1회"를 등록한다. off면 취소한다.
    // AlarmManager는 one-shot이므로 발화할 때마다 리시버가 이 메서드로 재무장한다.
    fun scheduleNext(type: ReminderType) {
        runCatching {
            val s = settingsRepository.current()
            val now = LocalDateTime.now()

            val next: LocalDateTime? = when (type) {
                ReminderType.BIBLE ->
                    if (!s.bibleEnabled) null
                    else nextDailyTrigger(now, s.bibleHour, s.bibleMinute)

                ReminderType.SPENDING_DAILY ->
                    if (!s.dailySpendingEnabled) null
                    else nextDailyTrigger(now, s.dailySpendingHour, s.dailySpendingMinute)

                ReminderType.SPENDING_WEEKLY ->
                    if (!s.weeklySpendingEnabled) null
                    else nextWeeklyTrigger(
                        now, s.weeklySpendingDayOfWeek, s.weeklySpendingHour, s.weeklySpendingMinute,
                    )
            }

            if (next == null) {
                cancel(type)
                return
            }

            val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(type),
                buildIntent(type),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmCompat(triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(type: ReminderType) {
        runCatching {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(type),
                buildIntent(type),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    // AlarmScheduler와 같은 네임스페이스 방식 — 타입별로 requestCode 공간을 분리한다.
    private fun requestCode(type: ReminderType) = "reminder:${type.key}".hashCode()

    // data Uri를 타입별로 고유화 — filterEquals는 extra를 무시하므로, 등록/취소가 항상 같은
    // 슬롯을 가리키도록 명시적으로 구분한다(AlarmReceiver의 딥링크와 같은 이유).
    private fun buildIntent(type: ReminderType) =
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TYPE, type.key)
            data = Uri.parse("lsync://reminder/${type.key}")
        }

    companion object {
        const val EXTRA_REMINDER_TYPE = "reminder_type"

        const val CHANNEL_BIBLE = "lsync_bible_reminder"
        const val CHANNEL_SPENDING = "lsync_spending_digest"

        // 고정 notify 슬롯 — AlarmReceiver의 id.hashCode() 공간, 결제(987_001)와 겹치지 않게 분리.
        const val NOTIFY_BIBLE = 991_001
        const val NOTIFY_SPENDING_DAILY = 991_002
        const val NOTIFY_SPENDING_WEEKLY = 991_003
    }
}

// ── 트리거 시각 계산 (순수 함수 — ReminderScheduleTest에서 검증) ──────────────────
//
// 규칙: 대상 시각이 now보다 "엄격히 미래"면 그대로, 아니면 다음 회차(+1일 / +7일).
// 경계(정확히 같은 시각)를 다음 회차로 넘기는 것은 AlarmScheduler가 `trigger <= now`를
// 스킵하는 것과 같은 방향이다 — 과거/현재 시각 알람이 즉시 발화하는 것을 막는다.

fun nextDailyTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
    val candidate = now.toLocalDate().atTime(hour, minute)
    return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
}

fun nextWeeklyTrigger(
    now: LocalDateTime,
    dayOfWeek: Int, // java.time.DayOfWeek.value — 1=월 … 7=일
    hour: Int,
    minute: Int,
): LocalDateTime {
    val target = dayOfWeek.coerceIn(1, 7)
    val daysAhead = Math.floorMod(target - now.dayOfWeek.value, 7)
    val candidate = now.toLocalDate().plusDays(daysAhead.toLong()).atTime(hour, minute)
    return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
}
