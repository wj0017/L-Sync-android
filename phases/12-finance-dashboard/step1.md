# Step 1: dashboard-viewmodel

## 배경

이 task(`12-finance-dashboard`)는 "가계부 통계 대시보드"다.

**이전 Step(Step 0)에서** `FinanceRepository.observeByDateRange(from, to): Flow<List<FinanceEntity>>`를 추가했다(다월 범위 거래 원천).

이 Step은 그 Flow를 구독해 **대시보드 집계 상태**를 만드는 ViewModel을 작성한다. 시각화(Compose)는 Step 2가 한다 — 이 Step은 숫자(상태)만 만든다.

집계 규칙은 PRD 2.4 정산 정책을 따른다:
- 지출 = `EXPENSE` 합. 단 표시 지출은 **순지출**(`expense − reimbursed`).
- 수입 = `INCOME` 중 `settlementGroupId == null` 합(정산 입금 제외).
- 정산 받음(`reimbursed`) = `INCOME` 중 `settlementGroupId != null` 합.

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 정산 집계(`net`, reimbursed), Finance ViewModel의 월 단위 집계 패턴, `collectAsState`
- `docs/PRD.md` 2.4 / `docs/TechSpec.md` 1.3 — 정산·순지출 규칙
- `CLAUDE.md` — `@HiltViewModel`+`@Inject`, `collectAsStateWithLifecycle` 금지
- `app/src/main/java/com/lsync/app/ui/finance/FinanceViewModel.kt` — **본보기.** 기존 월 집계(수입/지출/순지출/정산)를 어떻게 계산하는지 그대로 참고해 일관된 규칙 적용. (새 ViewModel을 만들지, 기존 VM에 상태를 더할지는 아래 작업 참고)
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` — `observeByDateRange`(Step 0)

## 작업

대시보드 상태를 제공하는 ViewModel을 만든다. **새 `FinanceDashboardViewModel`**(권장, `@HiltViewModel`)을 만들거나, 기존 `FinanceViewModel`에 대시보드 상태를 추가해도 된다. 새 ViewModel을 권장하는 이유: 기존 화면 로직과 분리해 응집도를 높임.

### 상태 모델(예시 — 필드는 재량)

```kotlin
data class MonthlyPoint(val yearMonth: String, val expense: Long, val income: Long) // 추세용
data class CategorySlice(val category: String, val amount: Long)                     // 카테고리별 지출
data class DashboardUiState(
    val monthly: List<MonthlyPoint> = emptyList(),   // 최근 N개월
    val categoryBreakdown: List<CategorySlice> = emptyList(), // 선택 기간 카테고리별 순지출
    val totalExpense: Long = 0,   // 순지출
    val totalIncome: Long = 0,
    val totalReimbursed: Long = 0,
)
```

### 집계 로직

- 기간: 최근 N개월(예: 6개월). `from = N개월 전 1일`, `to = 이번 달 말일`(LocalDate/YearMonth로 계산).
- `repository.observeByDateRange(from, to)`를 `viewModelScope`에서 구독.
- 받은 리스트로:
  - **월별 추세:** `date.take(7)`(YYYY-MM)로 그룹핑 → 각 달의 순지출·수입.
  - **카테고리별 순지출:** `EXPENSE`를 `category`로 그룹핑해 합산. (정산 받음은 카테고리 분해에서 별도 처리하거나 전체 순지출에만 반영 — 단순화를 위해 카테고리 분해는 `EXPENSE` 원금 기준으로 하되, 주석으로 명시.)
  - 합계는 위 정산 규칙대로.
- 결과를 `StateFlow<DashboardUiState>`로 노출.

## 핵심 규칙 (반드시 지킬 것)

- **정산 규칙 일관성.** 순지출 = `expense − reimbursed`, 수입에서 정산 입금 제외. 기존 `FinanceViewModel`/위젯과 **동일한 규칙**을 써라. 규칙이 갈라지면 화면 간 숫자가 어긋난다.
- **`@HiltViewModel`+`@Inject` 유지.** userId는 Repository가 처리(Step 0에서 `observeByDateRange`가 내부적으로 currentUserId 사용).
- **`collectAsStateWithLifecycle` 금지**, ViewModel 내부는 `viewModelScope.launch { flow.collect { } }`.
- UI/Compose를 만들지 마라. 이 Step은 상태 산출까지.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - 대시보드 ViewModel이 `@HiltViewModel`이고 `observeByDateRange`를 구독하는가?
   - 순지출·수입·정산 규칙이 기존 Finance 로직과 일치하는가?
   - 월별 추세·카테고리별 집계가 상태로 노출되는가?
3. 결과에 따라 `phases/12-finance-dashboard/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FinanceDashboardViewModel 추가 — observeByDateRange 구독, 최근 N개월 추세·카테고리별 순지출·정산 합계 집계(기존 정산 규칙 일관). Step2 UI가 렌더"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Compose를 수정/추가하지 마라. 이유: Step 2의 범위다.
- 정산 규칙을 새로 정의하지 마라. 기존 규칙(순지출·정산 입금 제외)을 재사용하라.
- `FinanceRepository`/DAO를 수정하지 마라. 이유: Step 0에서 원천 Flow가 준비됐다.
- 기존 `FinanceViewModel`의 동작을 바꾸지 마라(새 ViewModel 권장).
