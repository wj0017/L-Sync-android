package com.lsync.app.data.report

// 가계부 월간 집계(정산 정책 PRD 2.4)의 단일 출처. 순수 Kotlin — DI·Android 의존 없음.
// 같은 공식이 화면마다 복제되면 홈·가계부·리포트·위젯의 숫자가 갈라진다(Phase 17 이력).
// 새로 집계가 필요한 곳은 여기를 호출하고 공식을 다시 쓰지 않는다.
//
// 규칙(핵심):
//  - 순지출 = (Σ EXPENSE − Σ 정산입금).coerceAtLeast(0) — 돌려받은 금액을 미리 차감.
//  - 수입은 정산 입금(settlementGroupId != null)을 제외한 INCOME만.
//  - 카테고리별 지출은 EXPENSE "원금" 기준 — 정산 받음을 차감하지 않는다(의도된 설계).

import com.lsync.app.data.local.entity.FinanceEntity

private const val EXPENSE = "EXPENSE"
private const val INCOME = "INCOME"

data class FinanceTotals(
    val expense: Long,   // 순지출 = (Σ EXPENSE − Σ 정산입금).coerceAtLeast(0)
    val income: Long,    // 정산 입금 제외 INCOME
)

data class CategoryAmount(val category: String, val amount: Long)

// 기간 내 거래 목록의 순지출·수입 집계. 기간 필터링은 호출자(DAO 쿼리)가 담당한다.
fun financeTotals(items: List<FinanceEntity>): FinanceTotals {
    val expenseSum = items.filter { it.type == EXPENSE }.sumOf { it.amount }
    val incomeSum = items
        .filter { it.type == INCOME && it.settlementGroupId == null }.sumOf { it.amount }
    val reimbursedSum = items
        .filter { it.type == INCOME && it.settlementGroupId != null }.sumOf { it.amount }

    return FinanceTotals(
        expense = (expenseSum - reimbursedSum).coerceAtLeast(0),
        income = incomeSum,
    )
}

// 카테고리별 지출 상위 N개(내림차순). EXPENSE 원금 기준 — 정산 받음은 차감하지 않는다.
fun topExpenseCategories(items: List<FinanceEntity>, limit: Int = 5): List<CategoryAmount> =
    items
        .filter { it.type == EXPENSE }
        .groupBy { it.category }
        .map { (category, group) -> CategoryAmount(category, group.sumOf { it.amount }) }
        .sortedByDescending { it.amount }
        .take(limit)
