package com.lsync.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

// 정기 리마인더 트리거 시각 계산 검증.
// 경계 규칙: 대상 시각이 now보다 "엄격히 미래"일 때만 그대로 쓰고, 같거나 과거면 다음 회차로 넘긴다
// (과거/현재 시각 알람이 즉시 발화하는 것을 방지 — AlarmScheduler의 `trigger <= now` 스킵과 같은 방향).
class ReminderScheduleTest {

    // ── 매일 ──────────────────────────────────────────────────────────────────

    @Test fun `오늘 대상 시각이 아직 안 지났으면 오늘`() {
        val now = LocalDateTime.of(2026, 8, 5, 20, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 5, 21, 0),
            nextDailyTrigger(now, 21, 0),
        )
    }

    @Test fun `오늘 대상 시각이 지났으면 내일`() {
        val now = LocalDateTime.of(2026, 8, 5, 22, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 21, 0),
            nextDailyTrigger(now, 21, 0),
        )
    }

    @Test fun `정확히 같은 시각이면 내일 - 경계`() {
        val now = LocalDateTime.of(2026, 8, 5, 21, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 21, 0),
            nextDailyTrigger(now, 21, 0),
        )
    }

    @Test fun `분 단위도 반영된다`() {
        val now = LocalDateTime.of(2026, 8, 5, 21, 15)
        assertEquals(
            LocalDateTime.of(2026, 8, 5, 21, 30),
            nextDailyTrigger(now, 21, 30),
        )
    }

    @Test fun `월말에 날짜가 다음 달로 넘어간다`() {
        val now = LocalDateTime.of(2026, 8, 31, 23, 0)
        assertEquals(
            LocalDateTime.of(2026, 9, 1, 21, 0),
            nextDailyTrigger(now, 21, 0),
        )
    }

    // ── 주간 ──────────────────────────────────────────────────────────────────
    // 2026-08-05는 수요일. dayOfWeek는 java.time.DayOfWeek.value(1=월 … 7=일).

    @Test fun `수요일에 일요일 알림을 잡으면 이번 주 일요일`() {
        val now = LocalDateTime.of(2026, 8, 5, 12, 0) // 수
        assertEquals(
            LocalDateTime.of(2026, 8, 9, 20, 0), // 이번 주 일요일
            nextWeeklyTrigger(now, dayOfWeek = 7, hour = 20, minute = 0),
        )
    }

    @Test fun `일요일 대상 시각 전이면 오늘`() {
        val now = LocalDateTime.of(2026, 8, 9, 19, 0) // 일 19시
        assertEquals(
            LocalDateTime.of(2026, 8, 9, 20, 0),
            nextWeeklyTrigger(now, dayOfWeek = 7, hour = 20, minute = 0),
        )
    }

    @Test fun `일요일 대상 시각이 지났으면 다음 주 일요일`() {
        val now = LocalDateTime.of(2026, 8, 9, 21, 0) // 일 21시
        assertEquals(
            LocalDateTime.of(2026, 8, 16, 20, 0),
            nextWeeklyTrigger(now, dayOfWeek = 7, hour = 20, minute = 0),
        )
    }

    @Test fun `일요일 정확히 같은 시각이면 다음 주 - 경계`() {
        val now = LocalDateTime.of(2026, 8, 9, 20, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 16, 20, 0),
            nextWeeklyTrigger(now, dayOfWeek = 7, hour = 20, minute = 0),
        )
    }

    @Test fun `수요일에 월요일 알림을 잡으면 다음 주 월요일`() {
        val now = LocalDateTime.of(2026, 8, 5, 12, 0) // 수
        assertEquals(
            LocalDateTime.of(2026, 8, 10, 9, 0), // 다음 주 월요일
            nextWeeklyTrigger(now, dayOfWeek = 1, hour = 9, minute = 0),
        )
    }

    @Test fun `범위 밖 요일 값은 1~7로 보정된다`() {
        val now = LocalDateTime.of(2026, 8, 5, 12, 0) // 수
        assertEquals(
            nextWeeklyTrigger(now, dayOfWeek = 7, hour = 20, minute = 0),
            nextWeeklyTrigger(now, dayOfWeek = 99, hour = 20, minute = 0),
        )
    }
}
