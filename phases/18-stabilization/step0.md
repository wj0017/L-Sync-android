# Step 0: home-finance-aggregation

홈 화면 ViewModel의 두 가지 버그를 고친다. **수정 대상은 `HomeViewModel.kt` 단일 파일이다.**

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` (특히 "정산 추적" / "홈 위젯" 섹션의 정산 집계 규칙)
- `docs/PRD.md` (2.4 정산 정책)
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt` ← **수정 대상**
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` (수정 금지 — `monthIncome`/`monthExpense`/`monthNet` 소비 방식만 확인)
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` (`loadWidgetState` 의 가계부 집계 공식 — **이것이 정답 기준이다**)
- `app/src/main/java/com/lsync/app/ui/finance/FinanceViewModel.kt` (`FinanceUiState` 의 income/expense/reimbursed 정의 — 기준 일치 확인)

이전에 만들어진 코드를 꼼꼼히 읽고, 정산 집계 규칙을 이해한 뒤 작업하라.

## 작업

### 버그 1 — 홈 가계부 집계가 정산 정책을 위반 (앱 전체와 불일치)

현재 `HomeViewModel.observeMonthFinance` 의 집계가 잘못되어 있다:

```kotlin
monthIncome  = list.filter { f -> f.type == "INCOME" }.sumOf { f -> f.amount }   // 정산 입금까지 수입에 포함 (X)
monthExpense = list.filter { f -> f.type == "EXPENSE" }.sumOf { f -> f.amount }  // 총지출 (순지출 미적용) (X)
```

이를 **위젯(`LSyncWidget.loadWidgetState`)·가계부 화면(`FinanceUiState`)과 동일한 공식**으로 교체하라:

- `monthReimbursed` = `type == "INCOME" && settlementGroupId != null` 항목의 amount 합 (정산으로 돌려받은 돈)
- `monthExpense` = (`type == "EXPENSE"` 항목 amount 합 − `monthReimbursed`) 를 **0 미만 방지**(`coerceAtLeast(0)`)한 값 = 순지출
- `monthIncome` = `type == "INCOME" && settlementGroupId == null` 항목의 amount 합 (정산 입금 제외)

**핵심 규칙 (이탈 금지):**
- 정산 입금(`INCOME` + `settlementGroupId != null`)은 수입에도 지출에도 합산하지 않는다. PRD 2.4 정책이다.
- 지출은 반드시 순지출(`expense − reimbursed`, 음수 방지)로 노출한다. 위젯 `LSyncWidget.kt:119-127` 과 숫자가 정확히 일치해야 한다.
- `HomeUiState.monthNet` 의 정의(`monthIncome - monthExpense`)나 `HomeScreen` 표시 코드는 건드리지 마라. ViewModel의 집계만 고친다.

### 버그 2 — `applySettings()` 가 통독 Flow 컬렉터를 중복 누적 (코루틴 누수)

`init` 에서 이미 `observeReadingPlan(today)` 로 통독 Flow를 구독 중인데, `applySettings()` 안에서 `observeReadingPlan(today)` 를 **다시 호출**해 기존 컬렉터를 취소하지 않고 영구 컬렉터를 쌓는다. 설정을 적용할 때마다 누적된다.

`applySettings()` 의 `viewModelScope.launch { ... }` 블록에서 **중복 `observeReadingPlan(today)` 호출 한 줄만 제거**하라.

**이 제거가 안전한 근거 (반드시 이해하고 적용하라):**
- `applySettings` → `resetFromToday()` 는 `ReadingPlanDao.deleteFromDate` + `upsertAll` 로 `reading_plan` 테이블에 쓰기를 발생시킨다.
- Room InvalidationTracker가 `init` 에서 이미 돌고 있는 `observeForDate` 컬렉터를 자동 재방출시킨다.
- 재방출된 collect 람다는 `getSettings()`/`getStartDate()`/`getTotalRead()` 등을 다시 읽으므로, SharedPreferences에 저장된 새 설정·시작일까지 정상 반영된다.
- 따라서 재구독은 불필요하며 누수만 유발한다.

**금지: "안전을 위해" `observeReadingPlan` 호출을 되살리거나, 기존 컬렉터를 Job으로 추적해 취소·재구독하는 식으로 구조를 바꾸지 마라.** 단순히 중복 호출 한 줄을 지우는 것이 정답이다. `widgetRefreshHelper.requestUpdate()` 호출과 `_uiState.update { it.copy(showSetupSheet = false) }` 는 그대로 둔다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - 홈 `monthExpense`/`monthIncome` 집계가 `LSyncWidget.kt` 의 공식과 1:1로 동일한가? (정산 입금 제외 + 순지출)
   - `collectAsState()` 만 사용했는가? (`collectAsStateWithLifecycle` 금지)
   - Room이 SSOT인가? (UI가 직접 Firestore를 구독하지 않는가?)
3. 결과에 따라 `phases/18-stabilization/index.json` 의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `HomeScreen.kt`, `LSyncWidget.kt`, `FinanceViewModel.kt`, `ReadingPlanRepository.kt` 를 수정하지 마라. 이유: 이 step의 범위는 `HomeViewModel.kt` 단일 파일이며, 나머지는 정답 기준(읽기 전용)이다.
- 버그 2를 고칠 때 통독 관련 다른 메서드(`observeReadingPlan`, `markChapterRead`, `openSetupSheet` 등)의 구조를 바꾸지 마라. 이유: 중복 호출 한 줄 제거만으로 충분하며, 추가 변경은 회귀 위험을 만든다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
