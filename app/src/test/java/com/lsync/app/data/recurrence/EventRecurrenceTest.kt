package com.lsync.app.data.recurrence

import com.lsync.app.data.local.entity.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecurrenceTest {

    private fun event(
        id: String = "e1",
        title: String = "회의",
        isAllDay: Boolean = false,
        startDate: String = "2024-05-01T09:00:00+09:00",
        rrule: String? = null,
        exdatesJson: String? = null,
        overridesJson: String? = null,
        hasAlarm: Boolean = true,
    ) = EventEntity(
        id = id,
        userId = "u1",
        title = title,
        isAllDay = isAllDay,
        startDate = startDate,
        endDate = null,
        timezone = if (isAllDay) null else "Asia/Seoul",
        rrule = rrule,
        exdatesJson = exdatesJson,
        overridesJson = overridesJson,
        hasAlarm = hasAlarm,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // ── 비반복 ────────────────────────────────────────────────────────────────
    @Test fun `비반복 - 범위 안이면 1개`() {
        val occ = expandEvent(event(), "2024-05-01", "2024-05-31")
        assertEquals(1, occ.size)
        assertEquals("2024-05-01", occ[0].date)
        assertFalse(occ[0].isRecurring)
    }

    @Test fun `비반복 - 범위 밖이면 0개`() {
        assertTrue(expandEvent(event(), "2024-06-01", "2024-06-30").isEmpty())
    }

    // ── 매주 반복 ──────────────────────────────────────────────────────────────
    @Test fun `매주 반복 4주 전개 개수`() {
        val occ = expandEvent(
            event(rrule = "FREQ=WEEKLY"),
            "2024-05-01", "2024-05-28",
        )
        // 5/1, 5/8, 5/15, 5/22 (5/29는 범위 밖)
        assertEquals(4, occ.size)
        assertEquals(listOf("2024-05-01", "2024-05-08", "2024-05-15", "2024-05-22"), occ.map { it.date })
        assertTrue(occ.all { it.isRecurring })
    }

    @Test fun `RRULE 접두사도 허용`() {
        val occ = expandEvent(event(rrule = "RRULE:FREQ=WEEKLY"), "2024-05-01", "2024-05-14")
        assertEquals(2, occ.size)
    }

    // ── 시간지정 vs 종일 startDate 형식 ────────────────────────────────────────
    @Test fun `시간지정 발생은 날짜만 치환 시각 offset 보존`() {
        val occ = expandEvent(event(rrule = "FREQ=WEEKLY"), "2024-05-08", "2024-05-08")
        assertEquals("2024-05-08T09:00:00+09:00", occ.single().startDate)
        assertFalse(occ.single().isAllDay)
    }

    @Test fun `종일 발생은 Floating Date`() {
        val occ = expandEvent(
            event(isAllDay = true, startDate = "2024-05-01", rrule = "FREQ=WEEKLY"),
            "2024-05-08", "2024-05-08",
        )
        assertEquals("2024-05-08", occ.single().startDate)
        assertTrue(occ.single().isAllDay)
    }

    // ── exdate 제외 ────────────────────────────────────────────────────────────
    @Test fun `exdate 1개 제외`() {
        val occ = expandEvent(
            event(rrule = "FREQ=WEEKLY", exdatesJson = """["2024-05-08"]"""),
            "2024-05-01", "2024-05-28",
        )
        assertEquals(3, occ.size)
        assertFalse(occ.any { it.date == "2024-05-08" })
    }

    // ── override 반영 ──────────────────────────────────────────────────────────
    @Test fun `override title 반영하되 날짜는 그대로`() {
        val occ = expandEvent(
            event(rrule = "FREQ=WEEKLY", overridesJson = """{"2024-05-08":{"title":"수정회의"}}"""),
            "2024-05-08", "2024-05-08",
        ).single()
        assertEquals("수정회의", occ.title)
        assertEquals("2024-05-08", occ.date)
        assertTrue(occ.isOverridden)
    }

    @Test fun `override startDate는 시각만 바꾸고 날짜는 발생일 유지`() {
        val occ = expandEvent(
            event(
                rrule = "FREQ=WEEKLY",
                overridesJson = """{"2024-05-08":{"startDate":"2024-05-08T14:00:00+09:00"}}""",
            ),
            "2024-05-08", "2024-05-08",
        ).single()
        assertEquals("2024-05-08T14:00:00+09:00", occ.startDate)
    }

    @Test fun `override hasAlarm 반영`() {
        val occ = expandEvent(
            event(rrule = "FREQ=WEEKLY", hasAlarm = true, overridesJson = """{"2024-05-08":{"hasAlarm":false}}"""),
            "2024-05-08", "2024-05-08",
        ).single()
        assertFalse(occ.hasAlarm)
    }

    // ── nextOccurrence ─────────────────────────────────────────────────────────
    @Test fun `nextOccurrence - 다음 발생 1개`() {
        val next = nextOccurrence(event(rrule = "FREQ=WEEKLY"), "2024-05-08")
        assertEquals("2024-05-15", next?.date)
    }

    @Test fun `nextOccurrence - exdate 건너뜀`() {
        val next = nextOccurrence(
            event(rrule = "FREQ=WEEKLY", exdatesJson = """["2024-05-15"]"""),
            "2024-05-08",
        )
        assertEquals("2024-05-22", next?.date)
    }

    @Test fun `nextOccurrence - 비반복 과거면 null`() {
        assertNull(nextOccurrence(event(), "2024-05-02"))
    }

    // ── 깨진 입력 방어 ──────────────────────────────────────────────────────────
    @Test fun `깨진 rrule은 예외 없이 빈 결과`() {
        assertTrue(expandEvent(event(rrule = "쓰레기값"), "2024-05-01", "2024-12-31").isEmpty())
    }

    @Test fun `깨진 JSON exdate는 무시`() {
        assertEquals(emptySet<String>(), parseExdates("not-json"))
    }

    // ── JSON 헬퍼 라운드트립 ────────────────────────────────────────────────────
    @Test fun `withExdate 추가 후 parse 라운드트립 (정렬)`() {
        val json = withExdate(withExdate(null, "2024-05-08"), "2024-05-01")
        assertEquals("""["2024-05-01","2024-05-08"]""", json)
        assertEquals(setOf("2024-05-01", "2024-05-08"), parseExdates(json))
    }

    @Test fun `withOverride 병합 후 parse 라운드트립`() {
        val json = withOverride(null, "2024-05-08", EventOverride(title = "A", hasAlarm = false))
        val parsed = parseOverrides(json)
        assertEquals("A", parsed["2024-05-08"]?.title)
        assertEquals(false, parsed["2024-05-08"]?.hasAlarm)
    }

    @Test fun `withOverride - 따옴표 포함 제목 이스케이프`() {
        val json = withOverride(null, "2024-05-08", EventOverride(title = "회의 \"중요\""))
        assertEquals("회의 \"중요\"", parseOverrides(json)["2024-05-08"]?.title)
    }
}
