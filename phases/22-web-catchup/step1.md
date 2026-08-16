# Step 1: web-finance-policy

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/PRD.md` — §2.4 정산 추적 정책
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — **기준 공식(`aggregateFinance`)**
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — 같은 공식의 다른 사본
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `web/app/finance/page.tsx` — 수정 대상
- `web/types/models.ts` — step 0에서 `settlementGroupId`가 추가되어 있다. 반드시 확인하라.
- `web/hooks/useFinance.ts` — step 0에서 tombstone 필터가 추가되어 있다.

**step 0에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경 — 왜 이 작업이 필요한가

앱은 가계부를 **정산(settlement) 정책**에 따라 집계한다. `FinanceEntity.settlementGroupId`는 정산 추적 그룹 ID로, EXPENSE가 리더이고 INCOME이 "정산 받음(정산 입금)"이다.

- **표시 지출 = 순지출** = `Σ EXPENSE − Σ 정산입금`. 즉 남에게 대신 낸 돈은 돌려받은 만큼 지출에서 차감한다.
- **표시 수입** = 정산 입금을 **제외한** INCOME만. 이유: 정산 입금은 이미 지출에서 차감했으므로 수입으로 또 세면 이중 계상이다.

이 공식은 앱의 홈·위젯·가계부 대시보드·월간 리포트 4곳에서 일관되게 쓰인다. 그런데 웹 `app/finance/page.tsx`는 `f.type === 'INCOME' ? income += f.amount : expense += f.amount` 로 단순 합산한다. **같은 달을 앱과 웹에서 열면 숫자가 다르다.**

## 작업

### 1. `web/lib/finance.ts` 신규 — 집계 공식 단일화

앱 `ReportViewModel.aggregateFinance`와 **완전히 동일한 결과**를 내는 순수 함수를 만든다. Firebase나 React에 의존하지 않는다(순수 계산 모듈).

```ts
import { LSyncFinance } from '@/types/models';

export interface FinanceTotals {
  expense: number;   // 순지출 (표시용)
  income: number;    // 정산 입금 제외 수입
}

export function financeTotals(items: LSyncFinance[]): FinanceTotals;

export interface CategorySlice {
  category: string;
  amount: number;
}

export function topExpenseCategories(items: LSyncFinance[], limit?: number): CategorySlice[];
```

**`financeTotals` 규칙 (앱 공식과 1:1 대응 — 벗어나지 마라):**

- `expenseSum` = `type === 'EXPENSE'` 인 항목의 `amount` 합
- `incomeSum` = `type === 'INCOME' && settlementGroupId == null` 인 항목의 합 → 이것이 반환하는 `income`
- `reimbursedSum` = `type === 'INCOME' && settlementGroupId != null` 인 항목의 합
- 반환 `expense` = `Math.max(0, expenseSum - reimbursedSum)` — 앱의 `.coerceAtLeast(0)`에 대응. **음수를 그대로 반환하지 마라.**
- `settlementGroupId`가 `undefined`인 경우와 `null`인 경우를 **둘 다 "정산 아님"으로** 취급하라(`== null` 느슨한 비교 또는 명시적 `?? null` 정규화). 이유: 앱이 쓴 문서에는 `null`이, 필드를 안 쓴 옛 문서에는 `undefined`가 온다.

**`topExpenseCategories` 규칙:**

- `type === 'EXPENSE'` 만 대상, `category`로 groupBy, `amount` 합, **내림차순** 정렬, 상위 `limit`개(기본 5).
- **정산 받음을 카테고리에서 차감하지 마라.** 즉 EXPENSE **원금** 기준이다. 이유: 앱 `ReportViewModel`의 주석에 명시된 의도적 설계다("카테고리별 지출 — EXPENSE 원금 기준(정산 받음은 카테고리에서 차감하지 않음)"). 여기만 다르게 하면 카테고리 합과 총지출이 안 맞는 게 정상이다.

### 2. `web/app/finance/page.tsx` — 집계 교체

- `useMemo`로 income/expense를 계산하는 블록(현재 `items.forEach(f => { f.type === 'INCOME' ? ... })`)을 `financeTotals(items)` 호출로 교체한다.
- `balance`는 기존대로 `income - expense`를 유지한다(순지출 기준이 되므로 의미가 더 정확해진다).
- **화면 라벨을 순지출임이 드러나게 조정한다.** 지출 항목 라벨을 `지출` → `순지출`로 바꾸고, 정산 차감이 일어났을 때(= `reimbursedSum > 0`) 사용자가 혼란스럽지 않도록 앱과 동일한 표현을 쓴다. 앱 `FinanceScreen.kt`의 SummaryCard 문구를 확인해 톤을 맞춰라.
- 이 화면은 상위 카테고리를 표시하지 않는다. `topExpenseCategories`는 step 3의 리포트에서 쓰이므로 **여기서 UI를 추가하지 마라.** 이 step에서는 export만 해두면 된다.

## Acceptance Criteria

```bash
cd web && pnpm build
```

- 타입 에러 없이 빌드 성공.
- `web/app/finance/page.tsx`에 직접 합산 로직이 남아 있지 않다:

```bash
cd web && grep -n "income += \|expense += " app/finance/page.tsx
```

위 grep이 **아무것도 출력하지 않아야** 한다.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `web/lib/finance.ts`의 `financeTotals`를 `ReportViewModel.aggregateFinance`와 줄 단위로 대조한다. 특히 세 가지를 확인하라:
   - 순지출에 `Math.max(0, ...)` 하한이 걸려 있는가?
   - 수입에서 정산 입금이 제외되는가?
   - 카테고리 집계가 EXPENSE 원금 기준인가(정산 미차감)?
3. 손계산으로 한 번 검증하라. 예: EXPENSE 30000(식비) + EXPENSE 10000(교통) + INCOME 12000(settlementGroupId 있음) + INCOME 50000(급여, settlementGroupId 없음)
   → `expense = 40000 - 12000 = 28000`, `income = 50000`, `topExpenseCategories = [식비 30000, 교통 10000]`
4. 아키텍처 체크리스트:
   - Android 코드를 건드리지 않았는가?
   - step 0의 쓰기 계약을 되돌리지 않았는가?
5. 결과에 따라 `phases/22-web-catchup/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **Android(`app/`) 코드를 수정하지 마라.** 특히 앱의 집계 공식을 웹에 맞추려 하지 마라. 이유: 앱 공식이 정답이고 4곳에서 검증된 상태다.
- **앱 공식을 "개선"하지 마라.** 카테고리 집계가 정산을 차감하지 않는 것은 버그가 아니라 의도된 설계다.
- **`web/lib/db.ts` / `web/hooks/*`를 수정하지 마라.** 이유: step 0의 산출물이고 이 step의 범위가 아니다. 단, step 0이 만든 `settlementGroupId` 타입이 실제로 없으면 그때만 `web/types/models.ts`에 추가하라.
- **리포트 페이지나 차트 UI를 만들지 마라.** 이유: step 3의 작업이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
