package com.lsync.app.notification

import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.recurrence.EventOccurrence
import com.lsync.app.data.recurrence.nextOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class NextEventAlarm(val title: String, val triggerAtMillis: Long)

// 발생일은 시스템의 floating date, 시간 지정 일정은 저장된 offset을 따른다.
fun nextAlarmForEvent(event: EventEntity, now: Instant, zone: ZoneId): NextEventAlarm? {
    if (event.deletedAt != null) return null
    val today = LocalDate.ofInstant(now, zone)
    // 시간 지정 일정은 저장된 offset과 기기 시간대가 달라 어제 발생분이 아직 미래일 수 있다.
    val occurrence = nextOccurrence(event, today.minusDays(2).toString()) { candidate ->
        candidate.hasAlarm && occurrenceMillis(candidate, zone) > now.toEpochMilli()
    } ?: return null
    return NextEventAlarm(occurrence.title, occurrenceMillis(occurrence, zone))
}

internal fun occurrenceMillis(occurrence: EventOccurrence, zone: ZoneId): Long =
    if (occurrence.isAllDay) {
        LocalDate.parse(occurrence.startDate).atTime(AlarmScheduler.REMINDER_HOUR, 0)
            .atZone(zone).toInstant().toEpochMilli()
    } else {
        OffsetDateTime.parse(occurrence.startDate).toInstant().toEpochMilli()
    }
