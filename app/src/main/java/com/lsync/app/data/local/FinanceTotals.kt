package com.lsync.app.data.local

import com.lsync.app.data.local.entity.FinanceEntity

// 정산(Settlement) 집계 규칙 — PRD 2.4.
// 정산 입금(INCOME + settlementGroupId != null)은 실제 수입이 아니므로 수입에 합산하지 않고,
// 표시용 지출에서 차감한 "순지출"을 사용한다.
//
// 주의: 카테고리별 집계는 EXPENSE 원금을 그대로 쓴다(정산분을 카테고리에서 빼지 않는다).
// FinanceDashboardViewModel의 카테고리 집계와 같은 규칙이다.

fun List<FinanceEntity>.expenseSum(): Long =
    filter { it.type == "EXPENSE" }.sumOf { it.amount }

fun List<FinanceEntity>.incomeSum(): Long =
    filter { it.type == "INCOME" && it.settlementGroupId == null }.sumOf { it.amount }

fun List<FinanceEntity>.reimbursedSum(): Long =
    filter { it.type == "INCOME" && it.settlementGroupId != null }.sumOf { it.amount }

// 표시용 순지출 = 지출 − 정산 받은 금액 (음수 방지)
fun List<FinanceEntity>.netExpense(): Long =
    (expenseSum() - reimbursedSum()).coerceAtLeast(0)
