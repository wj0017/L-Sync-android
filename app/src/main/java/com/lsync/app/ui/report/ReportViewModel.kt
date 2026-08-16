package com.lsync.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.recurrence.expandEvents
import com.lsync.app.data.report.CategoryAmount
import com.lsync.app.data.report.financeTotals
import com.lsync.app.data.report.topExpenseCategories
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

// 월간 리포트 카테고리별 지출 — EXPENSE 원금 기준 합산(FinanceDashboard와 동일 규칙).
// 집계 공식은 data/report/MonthlyAggregate.kt 단일 출처를 쓰므로 그 타입에 별칭을 건다.
typealias CategorySlice = CategoryAmount

data class ReportUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    // 할일
    val todoTotal: Int = 0,
    val todoCompleted: Int = 0,
    // 일정 — 반복 마스터를 전개한 실제 발생 건수
    val eventCount: Int = 0,
    // 가계부 (정산 규칙 PRD 2.4 적용)
    val expense: Long = 0,        // 순지출 = expense − reimbursed (음수 방지)
    val income: Long = 0,         // 정산 입금 제외 수입
    val topCategories: List<CategorySlice> = emptyList(),  // 내림차순 상위 N개
    // 통독
    val chaptersRead: Int = 0,    // 해당 월 읽은 챕터 수
    val daysRead: Int = 0,        // 해당 월 읽은 날 수(distinct date)
) {
    val todoCompletionRate: Float get() = if (todoTotal == 0) 0f else todoCompleted.toFloat() / todoTotal
    val net: Long get() = income - expense
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val todoRepository: TodoRepository,
    private val financeRepository: FinanceRepository,
    private val readingPlanRepository: ReadingPlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    // 월이 가변이므로 이전 구독을 취소하고 새 범위로 재구독한다(FinanceViewModel monthJob 패턴).
    private var monthJob: Job? = null

    init {
        observeMonth(_uiState.value.yearMonth)
    }

    fun previousMonth() = moveTo(_uiState.value.yearMonth.minusMonths(1))

    // 미래 월은 데이터가 없으므로 현재 월을 넘어가지 못하게 가드.
    fun nextMonth() {
        val next = _uiState.value.yearMonth.plusMonths(1)
        if (next.isAfter(YearMonth.now())) return
        moveTo(next)
    }

    private fun moveTo(target: YearMonth) {
        if (target == _uiState.value.yearMonth) return
        // 이전 월 집계값이 잠깐 남지 않도록 초기화 후 재구독.
        _uiState.value = ReportUiState(yearMonth = target)
        observeMonth(target)
    }

    private fun observeMonth(yearMonth: YearMonth) {
        // Floating Date(YYYY-MM-DD) 문자열 범위 — 타임존 변환 없음.
        val from = "%04d-%02d-01".format(yearMonth.year, yearMonth.monthValue)
        val to = "%04d-%02d-%02d".format(yearMonth.year, yearMonth.monthValue, yearMonth.lengthOfMonth())

        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            combine(
                // 과거 시작 반복 마스터까지 포함 조회 → EventRecurrence로 월 범위 발생 전개(PRD 2.5)
                eventRepository.observeForExpansion(from, to),
                todoRepository.observeByDueDateRange(from, to),
                financeRepository.observeByDateRange(from, to),
                readingPlanRepository.observeReadInRange(from, to),
            ) { events, todos, finances, readEntries ->
                // 정산 규칙(PRD 2.4) 집계는 MonthlyAggregate 단일 출처를 그대로 사용한다.
                val totals = financeTotals(finances)
                ReportUiState(
                    yearMonth = yearMonth,
                    todoTotal = todos.size,
                    todoCompleted = todos.count { it.isCompleted },
                    eventCount = expandEvents(events, from, to).size,
                    expense = totals.expense,
                    income = totals.income,
                    topCategories = topExpenseCategories(finances, TOP_CATEGORY_COUNT),
                    chaptersRead = readEntries.size,
                    daysRead = readEntries.map { it.date }.distinct().size,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    private companion object {
        const val TOP_CATEGORY_COUNT = 5
    }
}
