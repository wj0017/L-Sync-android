// 가계부 집계 공식 단일화 — 앱 ReportViewModel.aggregateFinance / FinanceDashboardViewModel.aggregate와
// 동일한 결과를 내야 한다(PRD 2.4 정산 정책). 어긋나면 같은 달을 앱과 웹에서 열었을 때 숫자가 달라진다.
// Firebase·React에 의존하지 않는 순수 계산 모듈.

import { LSyncFinance } from '@/types/models';

const TOP_CATEGORY_COUNT = 5;

// 앱이 쓴 문서에는 null이, 필드가 없던 옛 문서에는 undefined가 온다 — 둘 다 "정산 아님".
function settlementGroupOf(item: LSyncFinance): string | null {
  return item.settlementGroupId ?? null;
}

export interface FinanceTotals {
  expense: number;      // 순지출 (표시용) = Σ EXPENSE − Σ 정산입금, 음수 방지
  income: number;       // 정산 입금 제외 수입
  reimbursed: number;   // 정산 받음 — 수입·지출 어디에도 합산하지 않고 별도 표기
}

export function financeTotals(items: LSyncFinance[]): FinanceTotals {
  let expenseSum = 0;
  let incomeSum = 0;
  let reimbursedSum = 0;

  for (const item of items) {
    if (item.type === 'EXPENSE') {
      expenseSum += item.amount;
    } else if (item.type === 'INCOME') {
      if (settlementGroupOf(item) === null) incomeSum += item.amount;
      else reimbursedSum += item.amount;
    }
  }

  return {
    // 앱 aggregateFinance의 .coerceAtLeast(0)에 대응
    expense: Math.max(0, expenseSum - reimbursedSum),
    income: incomeSum,
    reimbursed: reimbursedSum,
  };
}

export interface CategorySlice {
  category: string;
  amount: number;
}

// 카테고리별 지출 — EXPENSE 원금 기준(정산 받음을 차감하지 않는 것은 앱과 동일한 의도된 설계).
// 따라서 카테고리 합계와 위 순지출이 일치하지 않는 것이 정상이다.
export function topExpenseCategories(
  items: LSyncFinance[],
  limit: number = TOP_CATEGORY_COUNT,
): CategorySlice[] {
  const byCategory = new Map<string, number>();

  for (const item of items) {
    if (item.type !== 'EXPENSE') continue;
    byCategory.set(item.category, (byCategory.get(item.category) ?? 0) + item.amount);
  }

  return Array.from(byCategory, ([category, amount]) => ({ category, amount }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, limit);
}
