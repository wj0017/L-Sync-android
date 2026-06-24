# Step 1: report-viewmodel

도메인 횡단 **월간 리포트**의 ViewModel을 만든다. 선택된 월(YearMonth)의 일정·할일·가계부·통독 데이터를 Room Flow로 구독·집계해 UI 상태로 노출한다. UI(Compose)는 다음 step에서 만든다.

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 레이어 구조, Offline-First, Room=SSOT, "통계 대시보드" 절
- `docs/PRD.md` — **2.4 정산 정책**(집계 핵심 규칙). 순지출 = `expense − reimbursed`, 정산 입금(`settlementGroupId != null`인 INCOME)은 수입에 합산하지 않음
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — **집계 패턴의 레퍼런스**. 정산 규칙 적용 방식(`expenseSum`/`incomeSum`/`reimbursedSum`), 월 범위 계산(`YearMonth`, `"%04d-%02d-01".format(...)`), `combine`으로 다중 Flow 합성, `viewModelScope.launch { flow.collect { ... } }` 패턴을 그대로 따르라
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt` — **횡단 조합 레퍼런스**. `eventRepository.observeForExpansion` 사용법, `expandEvents`로 발생 전개, `MutableStateFlow` + `update` 패턴
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `expandEvents(events, from, to): List<EventOccurrence>` 시그니처 확인. 반복 일정을 월 범위로 전개해 발생 건수를 센다
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `observeForExpansion(from, to): Flow<List<EventEntity>>` (이미 존재, 재사용)
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` — `observeByDateRange(from, to): Flow<List<FinanceEntity>>` (이미 존재, 재사용)
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **Step 0에서 추가된** `observeByDueDateRange(from, to): Flow<List<TodoEntity>>`
- `app/src/main/java/com/lsync/app/data/repository/ReadingPlanRepository.kt` — **Step 0에서 추가된** `observeReadInRange(from, to): Flow<List<ReadingPlanEntity>>`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt` — `type`("EXPENSE"/"INCOME"), `amount`, `category`, `settlementGroupId` 필드
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` — 카테고리 표시명 매핑(상위 카테고리 라벨용)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

새 패키지 `app/src/main/java/com/lsync/app/ui/report/`에 `ReportViewModel.kt`를 만든다.

### UI 상태 모델

```kotlin
data class CategorySlice(val category: String, val amount: Long)   // 가계부 상위 카테고리(EXPENSE 원금 기준)

data class ReportUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    // 할일
    val todoTotal: Int = 0,
    val todoCompleted: Int = 0,
    // 일정
    val eventCount: Int = 0,
    // 가계부 (정산 규칙 적용)
    val expense: Long = 0,        // 순지출 = expense − reimbursed (음수 방지)
    val income: Long = 0,         // 정산 입금 제외 수입
    val topCategories: List<CategorySlice> = emptyList(),  // 내림차순, 상위 N개
    // 통독
    val chaptersRead: Int = 0,    // 해당 월 읽은 챕터 수
    val daysRead: Int = 0,        // 해당 월 읽은 날 수(distinct date)
) {
    val todoCompletionRate: Float get() = if (todoTotal == 0) 0f else todoCompleted.toFloat() / todoTotal
    val net: Long get() = income - expense
}
```

> `CategorySlice`는 `FinanceDashboardViewModel.kt`에도 동명 클래스가 있지만 **다른 패키지**(`ui.finance`)이므로 충돌하지 않는다. `ui.report` 패키지에 별도 정의하라(또는 import해도 무방하나, 자기완결성을 위해 report 패키지 정의를 권장).

### ViewModel

```kotlin
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val todoRepository: TodoRepository,
    private val financeRepository: FinanceRepository,
    private val readingPlanRepository: ReadingPlanRepository,
) : ViewModel() {
    // _uiState: MutableStateFlow<ReportUiState> / uiState: StateFlow<ReportUiState>
    // fun previousMonth()
    // fun nextMonth()   // 현재 월(YearMonth.now())을 넘어가지 못하게 가드
}
```

구현 요구사항:

1. **월 범위 계산:** 선택된 `YearMonth`에서 `from = "yyyy-MM-01"`, `to = "yyyy-MM-{lengthOfMonth}"`. `FinanceDashboardViewModel`의 `"%04d-%02d-%02d".format(...)` 방식을 그대로 사용하라.

2. **4개 Flow를 `combine`해 단일 상태로 합성:**
   - `eventRepository.observeForExpansion(from, to)` → `expandEvents(events, from, to).size`로 발생 건수. **이유:** 반복 일정은 마스터 한 행만 저장되므로 단순 행 개수가 아니라 전개해야 실제 월간 발생 수가 나온다(PRD 2.5 읽기-전개).
   - `todoRepository.observeByDueDateRange(from, to)` → `todoTotal = size`, `todoCompleted = count { it.isCompleted }`.
   - `financeRepository.observeByDateRange(from, to)` → 정산 규칙으로 집계(아래).
   - `readingPlanRepository.observeReadInRange(from, to)` → `chaptersRead = size`, `daysRead = map { it.date }.distinct().size`.

3. **가계부 집계 — 정산 규칙(PRD 2.4)을 반드시 지켜라.** `FinanceDashboardViewModel.aggregate`와 동일하게:
   - 순지출(`expense`) = (EXPENSE 합) − (INCOME & `settlementGroupId != null` 합), `coerceAtLeast(0)`.
   - 수입(`income`) = INCOME & `settlementGroupId == null` 합.
   - 상위 카테고리(`topCategories`) = EXPENSE 원금을 category로 groupBy → 합산 → 내림차순. 상위 5개 정도로 제한(`take(5)`).
   - **이유:** 정산 입금은 실제 수입이 아니므로 수입에 합산하면 가계부 화면·대시보드와 수치가 어긋난다.

4. **월 이동:** `previousMonth()`는 `yearMonth.minusMonths(1)`, `nextMonth()`는 `yearMonth.plusMonths(1)`로 변경하되 **`YearMonth.now()`를 초과하면 무시**(미래 월 데이터는 없음). 월이 바뀌면 새 범위로 다시 구독해야 한다. `FinanceDashboardViewModel`은 init에서 한 번만 구독하지만, 이 ViewModel은 월이 가변이므로 **현재 구독 Job을 취소하고 새 범위로 재구독**하는 방식을 쓰라(예: `monthJob?.cancel()` 후 `monthJob = viewModelScope.launch { combine(...).collect { ... } }`). HomeViewModel/FinanceViewModel의 Job 취소 재구독 패턴을 참고하라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (ViewModel은 `ui/report/`)
   - CLAUDE.md CRITICAL 규칙 위반 없는가? — `@HiltViewModel` + `@Inject constructor` 사용했는가? `collectAsStateWithLifecycle` 미사용? Floating Date(YYYY-MM-DD) 문자열을 타임존 변환 없이 사용했는가?
   - Room이 SSOT인가? ViewModel이 Firestore를 직접 구독하지 않고 Repository의 Room Flow만 구독하는가?
   - 정산 규칙(순지출·정산 입금 제외)이 `FinanceDashboardViewModel`과 일치하는가?
3. 결과에 따라 `phases/19-monthly-report/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ReportViewModel + ReportUiState 생성, 집계 필드 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- Firestore를 직접 구독하지 마라. 이유: Offline-First — UI/ViewModel은 Room Flow만 구독한다(ARCHITECTURE.md).
- 정산 입금(`settlementGroupId != null`인 INCOME)을 수입에 합산하지 마라. 이유: PRD 2.4 — 실제 수입이 아니며 다른 화면과 수치가 어긋난다.
- 반복 일정을 행 개수로 세지 마라. `expandEvents`로 전개해서 세라. 이유: 마스터 한 행이 월 내 여러 발생을 가질 수 있다(PRD 2.5).
- `collectAsStateWithLifecycle`를 사용하지 마라(이 step은 ViewModel이지만 규칙 인지). 이유: CLAUDE.md CRITICAL.
- `AppModule.kt`를 수정하지 마라. 이유: `@HiltViewModel`은 Hilt가 자동 처리하며, 주입하는 Repository는 모두 이미 등록되어 있다.
- UI(Composable) 코드를 작성하지 마라. 화면은 다음 step의 범위다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
