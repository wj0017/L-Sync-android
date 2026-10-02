package com.lsync.app.notification

import com.lsync.app.data.local.entity.EventEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun event(
        startDate: String = "2024-05-01T10:00:00+09:00",
        isAllDay: Boolean = false,
        rrule: String? = "FREQ=DAILY",
        hasAlarm: Boolean = true,
        exdatesJson: String? = null,
        overridesJson: String? = null,
        deletedAt: Long? = null,
    ) = EventEntity(
        id = "event", userId = "user", title = "원본", isAllDay = isAllDay,
        startDate = startDate, endDate = null, timezone = "Asia/Seoul", rrule = rrule,
        exdatesJson = exdatesJson, overridesJson = overridesJson, hasAlarm = hasAlarm,
        createdAt = 0, updatedAt = 0, deletedAt = deletedAt,
    )

    @Test fun `today occurrence remains eligible before trigger and advances after trigger`() {
        val before = nextAlarmForEvent(event(), Instant.parse("2024-05-01T00:00:00Z"), zone)
        val after = nextAlarmForEvent(event(), Instant.parse("2024-05-01T02:00:00Z"), zone)
        assertEquals(Instant.parse("2024-05-01T01:00:00Z").toEpochMilli(), before?.triggerAtMillis)
        assertEquals(Instant.parse("2024-05-02T01:00:00Z").toEpochMilli(), after?.triggerAtMillis)
    }

    @Test fun `override suppresses today and applies tomorrow title and time`() {
        val overrides = """{"2024-05-01":{"hasAlarm":false},"2024-05-02":{"title":"변경","startDate":"2024-05-02T14:00:00+09:00"}}"""
        val next = nextAlarmForEvent(event(overridesJson = overrides), Instant.parse("2024-05-01T00:00:00Z"), zone)
        assertEquals("변경", next?.title)
        assertEquals(Instant.parse("2024-05-02T05:00:00Z").toEpochMilli(), next?.triggerAtMillis)
    }

    @Test fun `override may enable alarm when master is disabled`() {
        val next = nextAlarmForEvent(
            event(hasAlarm = false, overridesJson = """{"2024-05-03":{"hasAlarm":true}}"""),
            Instant.parse("2024-05-01T00:00:00Z"), zone,
        )
        assertEquals(Instant.parse("2024-05-03T01:00:00Z").toEpochMilli(), next?.triggerAtMillis)
    }

    @Test fun `deleted or expired series has no next alarm`() {
        val now = Instant.parse("2024-05-01T02:00:00Z")
        assertNull(nextAlarmForEvent(event(deletedAt = 1), now, zone))
        assertNull(nextAlarmForEvent(event(rrule = "FREQ=DAILY;UNTIL=20240501"), now, zone))
    }

    @Test fun `excluded occurrence advances to next date`() {
        val next = nextAlarmForEvent(
            event(exdatesJson = """["2024-05-01"]"""),
            Instant.parse("2024-05-01T00:00:00Z"), zone,
        )
        assertEquals(Instant.parse("2024-05-02T01:00:00Z").toEpochMilli(), next?.triggerAtMillis)
    }

    @Test fun `single event in the past is not rearmed`() {
        assertNull(nextAlarmForEvent(
            event(rrule = null), Instant.parse("2024-05-01T02:00:00Z"), zone,
        ))
    }

    @Test fun `all day event uses device date at nine`() {
        val next = nextAlarmForEvent(
            event(startDate = "2024-05-01", isAllDay = true, rrule = null),
            Instant.parse("2024-04-30T23:00:00Z"), zone,
        )
        assertEquals(Instant.parse("2024-05-01T00:00:00Z").toEpochMilli(), next?.triggerAtMillis)
    }

    @Test fun `yesterday in saved offset may still be in the future`() {
        val next = nextAlarmForEvent(
            event(startDate = "2024-05-01T23:00:00-12:00", rrule = null),
            Instant.parse("2024-05-02T00:00:00Z"), ZoneId.of("Pacific/Kiritimati"),
        )
        assertEquals(Instant.parse("2024-05-02T11:00:00Z").toEpochMilli(), next?.triggerAtMillis)
    }
}
