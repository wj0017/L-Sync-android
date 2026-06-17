# Step 2: budget-viewmodel

## 배경

이 task(`16-budget`)는 "가계부 예산 — 카테고리별 + 전체 월 한도, 매월 반복, 초과 경고"를 구현한다.

**이전 Step에서 만든 것:**
- (Step 0) `BudgetEntity`, `BudgetDao.observeBudgets(userId)`, `TOTAL_CATEGORY="__TOTAL__"`.
- (Step 1) `BudgetRepository.observeBudgets/setBudget/deleteBudget`.

이 Step은 **예산 대비 실적(이번 달)** 을 계산하는 ViewModel 로직을 추가한다. 기존 `FinanceDashboardViewModel`을 확장하거나(권장) 별도 ViewModel을 만든다.

## 읽어야 할 파일

- `docs/PRD.md` 2.4, `docs/ARCHITECTURE.md`(통계 대시보드 — 정산 규칙 일관).
- `CLAUDE.md` — `collectAsState()`, userId 규칙.
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — **수정 대상(또는 참고).**
  - 이미 `FinanceRepository.observeByDateRange(from, to)`로 **최근 6개월** 범위를 구독해 `MonthlyPoint`·`CategorySlice`를 집계.
  - **카테고리별 지출 = EXPENSE 원금 기준**(`filter type==EXPENSE`, 정산 받음 차감 안 함 — L83 주석).
  - **표시 지출(totalExpense) = 순지출**(`expenseSum − reimbursedSum`, `coerceAtLeast(0)`).
  - `DashboardUiState` 구조.
- `app/src/main/java/com/lsync/app/data/repository/BudgetRepository.kt` — `observeBudgets(userId)`.
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt` — `currentUserId`.

## 작업

이번 달 예산 대비 실적을 계산해 UI state로 노출한다.

### 1) 이번 달 실적 집계 (6개월 추세와 분리)

- **예산은 "이번 달" 기준**이므로, 6개월 추세 리스트를 재사용하지 말고 **이번 달 범위**(`monthStart = YYYY-MM-01`, `monthEnd = 말일`)의 거래를 별도로 집계한다. `FinanceRepository.observeByDateRange(monthStart, monthEnd)` 구독.
- **카테고리별 실적 = 해당 카테고리 EXPENSE 원금 합**(기존 `categoryBreakdown` 규칙과 동일). 정산 받음을 카테고리에서 차감하지 않는다.
- **전체 실적(TOTAL) = 순지출**(`expense − reimbursed`, `coerceAtLeast(0)`) — 기존 SummaryCard `totalExpense`와 동일 정의. 사용자가 화면에서 보는 "이달 지출"과 일치시킨다.

### 2) 예산 + 실적 결합

- `budgetRepository.observeBudgets(currentUserId)`와 이번 달 거래를 `combine`해 카테고리별/전체 예산 항목을 만든다:

```kotlin
data class BudgetProgress(
    val category: String,       // FinanceCategory 값 또는 TOTAL_CATEGORY
    val limit: Long,
    val spent: Long,            // 카테고리=EXPENSE 원금, TOTAL=순지출
    val ratio: Float,           // spent / limit (limit<=0 가드)
    val isOver: Boolean,        // spent > limit
)
```

- 한도가 설정된 카테고리만 노출(미설정 카테고리는 진행률 표시 안 함). 전체 한도가 설정돼 있으면 TOTAL 항목도 포함.
- `ratio`는 `limit <= 0`이면 0(0 나눗셈 가드). UI 진행바는 1.0 초과를 클램프하되 `isOver`로 초과를 별도 표시.

### 3) UI state 노출

- `DashboardUiState`에 `budgets: List<BudgetProgress>`(또는 별도 state)를 추가한다. `collectAsState()`로 구독 가능하게.
- 기존 monthly/categoryBreakdown 집계는 그대로 둔다(회귀 금지).

## 핵심 규칙 (반드시 지킬 것)

- **예산 실적은 "이번 달"만.** 6개월 추세 리스트를 실적으로 쓰지 마라. 이유: 예산은 매월 반복 단일 한도라 당월 실적과 비교해야 한다.
- **카테고리 실적=EXPENSE 원금, 전체 실적=순지출.** 이유: 기존 대시보드(카테고리=원금, 요약=순지출)와 일관. 둘을 섞지 마라.
- **0 나눗셈 가드**(`limit<=0`). 이유: 크래시/NaN 방지.
- **`TOTAL_CATEGORY`는 카테고리별 목록과 구분.** 전체 한도 실적은 순지출로 계산하고, 카테고리 슬라이스에 섞지 마라.
- **userId는 `AuthRepository.currentUserId`.** `collectAsState()`만(ViewModel은 Flow 노출).
- 이 Step은 **계산/state**. UI 컴포저블·편집은 Step 3·4.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 이번 달 범위로 실적을 집계하는가(6개월 추세 재사용 아님)?
   - 카테고리 실적=원금, 전체 실적=순지출인가?
   - `BudgetProgress`(limit/spent/ratio/isOver)를 0나눗셈 가드와 함께 노출하는가?
   - 기존 monthly/categoryBreakdown 회귀 없음?
3. 결과에 따라 `phases/16-budget/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FinanceDashboardViewModel에 예산 대비 실적 추가 — 이번 달 범위 거래 + observeBudgets combine로 BudgetProgress(category/limit/spent/ratio/isOver). 카테고리=EXPENSE원금, TOTAL=순지출, 0나눗셈 가드. 기존 6개월 집계 보존"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 6개월 추세 합으로 예산 실적을 계산하지 마라. 이유: 당월 비교가 맞다.
- 카테고리 실적에 순지출(정산 차감)을 쓰지 마라. 이유: 기존 카테고리 집계는 원금 기준.
- UI 컴포저블/편집 시트를 만들지 마라(Step 3·4).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
