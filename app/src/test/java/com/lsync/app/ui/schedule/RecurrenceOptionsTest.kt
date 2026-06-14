package com.lsync.app.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceOptionsTest {

    // ── buildRrule ────────────────────────────────────────────────────────────
    @Test fun `DAILY - FREQ만`() {
        assertEquals("FREQ=DAILY", buildRrule(RecurrenceOption(Frequency.DAILY)))
    }

    @Test fun `WEEKLY interval2 - 요일 정렬 (입력 역순이어도 MO,WE)`() {
        val rrule = buildRrule(
            RecurrenceOption(Frequency.WEEKLY, interval = 2, weekdays = setOf(Weekday.WED, Weekday.MON)),
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", rrule)
    }

    @Test fun `MONTHLY interval1 - INTERVAL 생략`() {
        assertEquals("FREQ=MONTHLY", buildRrule(RecurrenceOption(Frequency.MONTHLY, interval = 1)))
    }

    @Test fun `YEARLY interval3`() {
        assertEquals("FREQ=YEARLY;INTERVAL=3", buildRrule(RecurrenceOption(Frequency.YEARLY, interval = 3)))
    }

    @Test fun `WEEKLY 외 frequency는 weekdays 무시 (BYDAY 미포함)`() {
        val rrule = buildRrule(
            RecurrenceOption(Frequency.DAILY, weekdays = setOf(Weekday.MON, Weekday.FRI)),
        )
        assertEquals("FREQ=DAILY", rrule)
    }

    @Test fun `WEEKLY 요일 비어있으면 BYDAY 생략`() {
        assertEquals("FREQ=WEEKLY", buildRrule(RecurrenceOption(Frequency.WEEKLY)))
    }

    @Test fun `WEEKLY 전체 요일 - 표준 순서`() {
        val rrule = buildRrule(
            RecurrenceOption(
                Frequency.WEEKLY,
                weekdays = setOf(
                    Weekday.SUN, Weekday.SAT, Weekday.FRI, Weekday.THU,
                    Weekday.WED, Weekday.TUE, Weekday.MON,
                ),
            ),
        )
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU", rrule)
    }

    // ── describeRrule ─────────────────────────────────────────────────────────
    @Test fun `describe - 매일`() {
        assertEquals("매일", describeRrule("FREQ=DAILY"))
    }

    @Test fun `describe - 매주`() {
        assertEquals("매주", describeRrule("FREQ=WEEKLY"))
    }

    @Test fun `describe - 2주마다 월,수`() {
        assertEquals("2주마다 월,수", describeRrule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"))
    }

    @Test fun `describe - 매월`() {
        assertEquals("매월", describeRrule("FREQ=MONTHLY"))
    }

    @Test fun `describe - 매년`() {
        assertEquals("매년", describeRrule("FREQ=YEARLY"))
    }

    @Test fun `describe - RRULE 접두사도 허용`() {
        assertEquals("매일", describeRrule("RRULE:FREQ=DAILY"))
    }

    @Test fun `describe - 깨진 입력은 반복 반환`() {
        assertEquals("반복", describeRrule("쓰레기값"))
    }

    @Test fun `describe - 빈 문자열은 반복 반환`() {
        assertEquals("반복", describeRrule(""))
    }
}
