package com.lsync.app.ui.widget

// 월간 리포트 위젯(4×2) 상태. 지표 정의는 ui/report/ReportViewModel과 동일해야 한다
// (어긋나면 위젯과 리포트 화면 숫자가 갈라진다 — TechSpec §9).
data class ReportWidgetState(
    val yearMonth: String,      // 표시 라벨 (예: "2026.08")
    val todoTotal: Int,         // 마감일 있는 Todo만
    val todoCompleted: Int,
    val eventCount: Int,        // 반복 마스터를 전개한 발생 수(행 수 아님)
    val expense: Long,          // 순지출 = (Σ EXPENSE − Σ 정산입금).coerceAtLeast(0)
    val chaptersRead: Int,      // 읽은 챕터 수(행 수)
    val daysRead: Int,          // 읽은 날 수(distinct date)
) {
    companion object {
        fun empty() = ReportWidgetState(
            yearMonth = "",
            todoTotal = 0,
            todoCompleted = 0,
            eventCount = 0,
            expense = 0,
            chaptersRead = 0,
            daysRead = 0,
        )
    }
}
