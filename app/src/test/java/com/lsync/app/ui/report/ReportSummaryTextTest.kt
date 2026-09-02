package com.lsync.app.ui.report

import com.lsync.app.data.report.CategoryAmount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ReportSummaryTextTest {

    @Test fun `정상 달 - 완료율·건수·순지출이 포함된다`() {
        val text = buildReportSummaryText(
            ReportUiState(
                yearMonth = YearMonth.of(2026, 8),
                todoTotal = 20, todoCompleted = 12,
                eventCount = 34,
                expense = 1_234_000, income = 2_000_000,
                topCategories = listOf(CategoryAmount("식비", 450_000)),
                chaptersRead = 23, daysRead = 15,
            ),
        )
        assertTrue(text, text.contains("2026년 8월"))
        assertTrue(text, text.contains("12/20 완료 (60%)"))
        assertTrue(text, text.contains("34건"))
        assertTrue(text, text.contains("−₩1,234,000"))
        assertTrue(text, text.contains("+₩2,000,000"))
        // 잔액 = income − expense = 766,000 (여기서 재계산하지 않고 state.net을 그대로 쓴다)
        assertTrue(text, text.contains("+₩766,000"))
        assertTrue(text, text.contains("1. 식비"))
        assertTrue(text, text.contains("23장 · 15일 읽음"))
    }

    @Test fun `빈 달 - 예외 없이 반환되고 카테고리 블록이 없다`() {
        val text = buildReportSummaryText(ReportUiState(yearMonth = YearMonth.of(2026, 1)))
        assertTrue(text, text.contains("2026년 1월"))
        assertTrue(text, text.contains("0/0 완료 (0%)"))
        assertTrue(text, text.contains("0건"))
        assertFalse(text, text.contains("1."))
    }

    @Test fun `잔액 음수 - 마이너스 부호와 절댓값을 표시한다`() {
        val text = buildReportSummaryText(
            ReportUiState(expense = 500_000, income = 200_000),
        )
        assertTrue(text, text.contains("잔액 −₩300,000"))
    }

    @Test fun `카테고리 공백 - 미분류로 표시한다`() {
        val text = buildReportSummaryText(
            ReportUiState(topCategories = listOf(CategoryAmount("   ", 10_000))),
        )
        assertTrue(text, text.contains("1. 미분류"))
    }
}
