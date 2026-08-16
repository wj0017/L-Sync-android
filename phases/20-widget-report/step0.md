# Step 0: aggregate-util

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — §9 월간 리포트
- `docs/PRD.md` — §2.4 정산 추적 정책
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — **기준 공식(`aggregateFinance`). 수정 대상.**
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — 같은 공식의 다른 사본(읽기만, 수정 금지)
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt` — `observeMonthFinance`(읽기만, 수정 금지)
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — `loadWidgetState` 안의 4번째 사본(읽기만, 수정 금지)
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — 수정 대상
- `app/src/main/java/com/lsync/app/data/local/dao/ReadingPlanDao.kt` — 수정 대상

## 배경 — 왜 이 작업이 필요한가

가계부 월간 집계 공식(정산 정책)이 현재 **4곳에 복제**돼 있다:

1. `HomeViewModel.observeMonthFinance`
2. `LSyncWidget.loadWidgetState`
3. `FinanceDashboardViewModel.aggregate`
4. `ReportViewModel.aggregateFinance`

다음 step에서 리포트 위젯을 추가하면 **5번째 사본**이 생긴다. 사본이 늘어날수록 정책을 바꿀 때 갈라지고, 화면마다 다른 숫자가 나온다(실제로 Phase 17에서 홈 집계가 어긋나 수정한 이력이 있다).

이 step은 공용 함수를 뽑아 **다음 step이 5번째 사본을 만들지 않도록** 준비한다.

## 작업

### 1. `app/src/main/java/com/lsync/app/data/report/MonthlyAggregate.kt` 신규

**순수 Kotlin 파일. DI 없음, Android 의존 없음, 클래스 아닌 top-level 함수로 작성한다.** (`data/recurrence/EventRecurrence.kt`가 같은 스타일이니 참고하라.)

```kotlin
package com.lsync.app.data.report

data class FinanceTotals(
    val expense: Long,   // 순지출 = (Σ EXPENSE − Σ 정산입금).coerceAtLeast(0)
    val income: Long,    // 정산 입금 제외 INCOME
)

fun financeTotals(items: List<FinanceEntity>): FinanceTotals

data class CategoryAmount(val category: String, val amount: Long)

fun topExpenseCategories(items: List<FinanceEntity>, limit: Int = 5): List<CategoryAmount>
```

**공식은 `ReportViewModel.aggregateFinance`를 그대로 옮긴다 (PRD 2.4):**

- `expenseSum` = `type == "EXPENSE"`의 `amount` 합
- `incomeSum` = `type == "INCOME" && settlementGroupId == null`의 합 → 반환 `income`
- `reimbursedSum` = `type == "INCOME" && settlementGroupId != null`의 합
- 반환 `expense` = `(expenseSum - reimbursedSum).coerceAtLeast(0)`
- `topExpenseCategories` = EXPENSE **원금** 기준 `groupBy { category }` → 내림차순 → `take(limit)`. **정산 받음을 카테고리에서 차감하지 마라** — 의도된 설계다.

`ReportViewModel`이 이미 쓰는 `CategorySlice`가 있다면 그 타입을 재사용해도 좋다. 새 타입을 만들 경우 `ReportViewModel` 쪽 변환이 자연스러운지 확인하라.

### 2. `ReportViewModel.aggregateFinance`를 위 함수로 교체

- private `aggregateFinance`가 하던 계산을 `financeTotals` + `topExpenseCategories` 호출로 바꾼다.
- **동작·출력이 완전히 동일해야 한다.** 이 step은 리팩토링일 뿐 기능 변경이 아니다. UI 상태(`ReportUiState`)의 필드나 화면은 건드리지 않는다.

### 3. 위젯용 suspend 조회 메서드 추가

위젯은 Flow가 아니라 **일회성 suspend 조회**가 필요하다(Glance `provideGlance`는 suspend 함수이며 구독을 유지하지 않는다).

- `TodoDao` — 기존 `observeByDueDateRange(from, to): Flow<List<TodoEntity>>`와 **같은 WHERE 절**을 쓰는 `suspend fun getByDueDateRange(from: String, to: String): List<TodoEntity>` 추가.
- `ReadingPlanDao` — 기존 `observeReadInRange(from, to): Flow<List<ReadingPlanEntity>>`와 **같은 WHERE 절**을 쓰는 `suspend fun getReadInRange(from: String, to: String): List<ReadingPlanEntity>` 추가.

**기존 `observe*` 쿼리 문자열을 그대로 복사해 `suspend fun`으로만 바꿔라.** 조건을 다시 쓰지 마라 — `deletedAt IS NULL AND dueDate IS NOT NULL`(Todo), `isRead = 1`(ReadingPlan) 같은 조건이 어긋나면 위젯과 리포트 화면의 숫자가 달라진다.

DAO 메서드 추가일 뿐 **새 DAO·Repository 클래스가 아니므로 `di/AppModule.kt`는 수정하지 않는다.** Room 스키마 변경이 없으므로 **마이그레이션도 불필요**하다(`@Query`만 추가).

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

- 컴파일·린트 통과, 기존 유닛 테스트 전부 통과.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `MonthlyAggregate.financeTotals`를 `ReportViewModel`의 교체 전 `aggregateFinance`와 대조한다. 세 가지를 확인하라:
   - 순지출에 `.coerceAtLeast(0)`가 걸려 있는가?
   - 수입에서 `settlementGroupId != null`이 제외되는가?
   - 카테고리 집계가 EXPENSE 원금 기준인가?
3. 새 DAO 메서드의 SQL을 대응하는 `observe*` 쿼리와 문자열 단위로 대조한다. WHERE 절이 동일해야 한다.
4. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`data/report/`)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
   - 새 클래스가 아니므로 `AppModule` 변경이 없는가?
5. 결과에 따라 `phases/20-widget-report/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`HomeViewModel` / `FinanceDashboardViewModel` / `LSyncWidget`을 이 함수로 리팩토링하지 마라.** 이유: 셋 다 동작 검증이 끝난 코드이고, 이 phase의 목적은 **새 사본을 막는 것**이지 기존 코드를 손대는 게 아니다. 건드리면 회귀 위험만 늘고 리뷰 범위가 커진다.
- **집계 공식을 "개선"하지 마라.** 카테고리 집계가 정산을 차감하지 않는 것은 버그가 아니라 의도된 설계다.
- **위젯 코드를 만들지 마라.** 이유: step 1의 작업이다. 이 step은 유틸과 DAO 메서드까지만 한다.
- **`AppModule`을 수정하지 마라.** 새 DAO·Repository 클래스를 추가하지 않으므로 등록할 것이 없다. 수정이 필요하다고 느껴지면 설계를 잘못 잡은 것이다.
- **AppDatabase 버전을 올리지 마라.** `@Query` 추가는 스키마 변경이 아니다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
