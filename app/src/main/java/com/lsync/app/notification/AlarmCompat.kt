package com.lsync.app.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

// 정확 알람 권한(Android 12+) 미허용 시 inexact 폴백 — 알람이 조용히 사라지는 것을 방지.
// AlarmScheduler(일정·할 일)와 ReminderScheduler(정기 리마인더)가 공유한다.
// 폴백 규칙의 사본이 갈라지지 않도록 이 한 곳에만 둔다.
@SuppressLint("ScheduleExactAlarm")
internal fun AlarmManager.setAlarmCompat(triggerAtMillis: Long, pendingIntent: PendingIntent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()) {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
        setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}
