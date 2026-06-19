package com.lsync.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.BudgetRepository
import com.lsync.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

// 최근 N개월 추세용 — 각 달의 순지출(expense − reimbursed)·수입.
data class MonthlyPoint(val yearMonth: String, val expense: Long, val income: Long)

// 카테고리별 지출 — EXPENSE 원금 기준 합산.
data class CategorySlice(val category: String, val amount: Long)

// 예산 대비 실적(이번 달) — 카테고리=EXPENSE 원금, TOTAL=순지출.
data class BudgetProgress(
    val category: String,   // FinanceCategory 값 또는 BudgetEntity.TOTAL_CATEGORY
    val limit: Long,
    val spent: Long,
    val ratio: Float,       // spent / limit (limit<=0 가드 → 0)
    val isOver: Boolean,    // spent > limit
)

data class DashboardUiState(
    val monthly: List<MonthlyPoint> = emptyList(),       // 최근 N개월 추세
    val categoryBreakdown: List<CategorySlice> = emptyList(), // 선택 기간 카테고리별 지출
    val totalExpense: Long = 0,   // 순지출 (expense − reimbursed)
    val totalIncome: Long = 0,    // 정산 입금 제외 수입
    val totalReimbursed: Long = 0,
    val budgets: List<BudgetProgress> = emptyList(), // 이번 달 예산 대비 실적
)

@HiltViewModel
class FinanceDashboardViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val budgetRepository: BudgetRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRange()
        observeBudgetProgress()
    }

    private fun observeRange() {
        val thisMonth = YearMonth.now()
        // 최근 6개월: from = 5개월 전 1일, to = 이번 달 말일.
        val fromMonth = thisMonth.minusMonths(MONTHS - 1L)
        val from = "%04d-%02d-01".format(fromMonth.year, fromMonth.monthValue)
        val to = "%04d-%02d-%02d".format(thisMonth.year, thisMonth.monthValue, thisMonth.lengthOfMonth())

        viewModelScope.launch {
            repository.observeByDateRange(from, to).collect { list ->
                // 예산 실적(budgets)은 별도 Flow가 채우므로 보존하고 6개월 집계만 갱신.
                _uiState.update { aggregate(list, fromMonth, thisMonth).copy(budgets = it.budgets) }
            }
        }
    }

    // 예산 대비 실적은 "이번 달"만 집계(6개월 추세 재사용 아님). 예산은 매월 반복 단일 한도.
    private fun observeBudgetProgress() {
        val userId = authRepository.currentUserId ?: return
        val thisMonth = YearMonth.now()
        val monthStart = "%04d-%02d-01".format(thisMonth.year, thisMonth.monthValue)
        val monthEnd = "%04d-%02d-%02d".format(thisMonth.year, thisMonth.monthValue, thisMonth.lengthOfMonth())

        viewModelScope.launch {
            combine(
                budgetRepository.observeBudgets(userId),
                repository.observeByDateRange(monthStart, monthEnd),
            ) { budgets, transactions -> buildBudgetProgress(budgets, transactions) }
                .collect { progress -> _uiState.update { it.copy(budgets = progress) } }
        }
    }

    // 예산 한도 설정/수정 — Repository 경유(멱등 upsert). observeBudgets Flow가 자동 재방출.
    fun setBudget(category: String, limitAmount: Long) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            budgetRepository.setBudget(userId, category, limitAmount)
        }
    }

    // 예산 삭제 — id는 결정론적("${userId}_${category}"). soft delete + 원격 전파.
    fun deleteBudget(category: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            budgetRepository.deleteBudget("${userId}_${category}")
        }
    }

    private fun buildBudgetProgress(
        budgets: List<BudgetEntity>,
        transactions: List<FinanceEntity>,
    ): List<BudgetProgress> {
        // 카테고리 실적 = EXPENSE 원금(정산 받음 차감 안 함, 기존 categoryBreakdown 규칙과 동일).
        val expenseByCategory = transactions
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }

        // 전체 실적 = 순지출(expense − reimbursed, 음수 방지) — SummaryCard totalExpense와 동일.
        val expenseSum = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val reimbursedSum = transactions
            .filter { it.type == "INCOME" && it.settlementGroupId != null }.sumOf { it.amount }
        val netExpense = (expenseSum - reimbursedSum).coerceAtLeast(0)

        return budgets.map { budget ->
            val spent = if (budget.category == BudgetEntity.TOTAL_CATEGORY) {
                netExpense
            } else {
                expenseByCategory[budget.category] ?: 0L
            }
            val limit = budget.limitAmount
            val ratio = if (limit <= 0) 0f else spent.toFloat() / limit.toFloat()
            BudgetProgress(
                category = budget.category,
                limit = limit,
                spent = spent,
                ratio = ratio,
                isOver = spent > limit,
            )
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
