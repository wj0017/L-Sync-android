package com.lsync.app.ui.report

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// 월간 리포트 텍스트 요약 — Intent.EXTRA_TEXT로 공유할 순수 문자열을 만든다.
// Android 의존 없음(Context·Resources 사용 금지) — JVM 단위 테스트가 가능해야 한다.
//
// 규칙:
//  - 집계를 다시 하지 않는다. state.expense(순지출)·income·net·topCategories는 이미
//    data/report/MonthlyAggregate.kt(단일 출처)가 확정한 값이다. 여기서 더하거나 빼면
//    화면·위젯과 숫자가 갈라진다(Phase 17 이력).
//  - 라벨·부호는 ReportScreen과 일치시킨다(같은 달을 보다가 공유했을 때 말이 달라지면 안 된다).
//  - 로케일 의존 포매팅 금지. 월 이름은 직접 조립하고 금액은 Locale.US 고정 그룹 구분자를 쓴다.

fun buildReportSummaryText(state: ReportUiState): String = buildString {
    val ym = state.yearMonth
    appendLine("L-Sync 월간 리포트 · ${ym.year}년 ${ym.monthValue}월")
    appendLine()

    val percent = (state.todoCompletionRate.coerceIn(0f, 1f) * 100).roundToInt()
    appendLine(row("할 일", "${state.todoCompleted}/${state.todoTotal} 완료 ($percent%)"))
    appendLine(row("일정", "${state.eventCount}건"))
    appendLine(
        row(
            "가계부",
            "순지출 ${money(state.expense, negative = true)}" +
                " / 수입 ${money(state.income, negative = false)}" +
                " / 잔액 ${money(abs(state.net), negative = state.net < 0)}",
        ),
    )
    // 카테고리는 이미 내림차순 상위 N개다. 재정렬·재계산하지 않고 그대로 나열한다.
    state.topCategories.forEachIndexed { index, slice ->
        appendLine("  ${index + 1}. ${slice.category.ifBlank { "미분류" }}  ${money(slice.amount, negative = true)}")
    }
    append(row("통독", "${state.chaptersRead}장 · ${state.daysRead}일 읽음"))
}

// 라벨 열 폭을 맞춰 붙여넣었을 때도 읽히게 한다.
private fun row(label: String, value: String): String = "${label.padEnd(4)} $value"

private fun money(amount: Long, negative: Boolean): String =
    (if (negative) "−₩" else "+₩") + String.format(Locale.US, "%,d", amount)
