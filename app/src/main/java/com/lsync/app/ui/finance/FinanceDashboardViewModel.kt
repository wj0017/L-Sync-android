package com.lsync.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

// 최근 N개월 추세용 — 각 달의 순지출(expense − reimbursed)·수입.
data class MonthlyPoint(val yearMonth: String, val expense: Long, val income: Long)

// 카테고리별 지출 — EXPENSE 원금 기준 합산.
data class CategorySlice(val category: String, val amount: Long)

data class DashboardUiState(
    val monthly: List<MonthlyPoint> = emptyList(),       // 최근 N개월 추세
    val categoryBreakdown: List<CategorySlice> = emptyList(), // 선택 기간 카테고리별 지출
    val totalExpense: Long = 0,   // 순지출 (expense − reimbursed)
    val totalIncome: Long = 0,    // 정산 입금 제외 수입
    val totalReimbursed: Long = 0,
)

@HiltViewModel
class FinanceDashboardViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRange()
    }

    private fun observeRange() {
        val thisMonth = YearMonth.now()
        // 최근 6개월: from = 5개월 전 1일, to = 이번 달 말일.
        val fromMonth = thisMonth.minusMonths(MONTHS - 1L)
        val from = "%04d-%02d-01".format(fromMonth.year, fromMonth.monthValue)
        val to = "%04d-%02d-%02d".format(thisMonth.year, thisMonth.monthValue, thisMonth.lengthOfMonth())

        viewModelScope.launch {
            repository.observeByDateRange(from, to).collect { list ->
                _uiState.update { aggregate(list, fromMonth, thisMonth) }
            }
        }
    }

    private fun aggregate(
        list: List<FinanceEntity>,
        fromMonth: YearMonth,
        toMonth: YearMonth,
    ): DashboardUiState {
        // 정산 규칙(PRD 2.4): 수입은 정산 입금(settlementGroupId != null) 제외,
        // 정산 받음(reimbursed)은 별도 집계, 표시 지출은 순지출(expense − reimbursed).
        fun List<FinanceEntity>.expenseSum() =
            filter { it.type == "EXPENSE" }.sumOf { it.amount }
        fun List<FinanceEntity>.incomeSum() =
            filter { it.type == "INCOME" && it.settlementGroupId == null }.sumOf { it.amount }
        fun List<FinanceEntity>.reimbursedSum() =
            filter { it.type == "INCOME" && it.settlementGroupId != null }.sumOf { it.amount }

        // 월별 추세 — date.take(7)(YYYY-MM)로 그룹핑. 거래 없는 달도 0으로 채운다.
        val byMonth = list.groupBy { it.date.take(7) }
        val monthly = buildList {
            var ym = fromMonth
            while (!ym.isAfter(toMonth)) {
                val key = "%04d-%02d".format(ym.year, ym.monthValue)
                val items = byMonth[key].orEmpty()
                val netExpense = (items.expenseSum() - items.reimbursedSum()).coerceAtLeast(0)
                add(MonthlyPoint(yearMonth = key, expense = netExpense, income = items.incomeSum()))
                ym = ym.plusMonths(1)
            }
        }

        // 카테고리별 지출 — EXPENSE 원금 기준(정산 받음은 카테고리에서 차감하지 않음).
        val categoryBreakdown = list
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .map { (category, items) -> CategorySlice(category, items.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        return DashboardUiState(
            monthly = monthly,
            categoryBreakdown = categoryBreakdown,
            totalExpense = (list.expenseSum() - list.reimbursedSum()).coerceAtLeast(0),
            totalIncome = list.incomeSum(),
            totalReimbursed = list.reimbursedSum(),
        )
    }

    private companion object {
        const val MONTHS = 6
    }
}
