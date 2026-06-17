# Step 4: budget-edit-ui

## 배경

이 task(`16-budget`)는 "가계부 예산 — 카테고리별 + 전체 월 한도, 매월 반복, 초과 경고"를 구현한다.

**이전 Step에서 만든 것:**
- (Step 0) `BudgetEntity`, `TOTAL_CATEGORY`, `FinanceCategory.all`.
- (Step 1) `BudgetRepository.setBudget(userId, category, limitAmount)` / `deleteBudget(id)`.
- (Step 2) `FinanceDashboardViewModel.budgets: List<BudgetProgress>`.
- (Step 3) 대시보드 예산 섹션(진행바, 읽기 전용).

이 Step은 **예산을 설정·수정·삭제하는 UI**와 ViewModel 액션을 추가해 기능을 완성한다.

## 읽어야 할 파일

- `docs/UI_GUIDE.md`, `CLAUDE.md` — 디자인 시스템, userId 규칙.
- `app/src/main/java/com/lsync/app/ui/finance/FinanceScreen.kt` — **수정 대상.** 거래↔통계 ghost chip 토글, `TransactionFormSheet` 호출 패턴(입력 시트의 레퍼런스), `FinanceViewModel`/`FinanceDashboard` 배치.
- `app/src/main/java/com/lsync/app/ui/finance/TransactionFormSheet.kt` — **입력 시트 레퍼런스.** 금액 입력·카테고리 선택·확인 UI 패턴(다크 토큰, Pretendard) 재사용.
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboard.kt` — Step 3 예산 섹션. 편집 진입점(예: 섹션 헤더 옆 "설정" 액션, 또는 진행바 항목 탭) 추가 위치.
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — **수정 대상.** Step 2의 `budgets` state에 더해 예산 설정/삭제 액션 추가.
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` — `FinanceCategory.all` 카테고리 선택지.
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt` — `currentUserId`.

## 작업

### 1) ViewModel 액션

`FinanceDashboardViewModel`에 예산 설정/삭제 액션을 추가한다:

```kotlin
fun setBudget(category: String, limitAmount: Long)   // viewModelScope에서 repository.setBudget(currentUserId, category, limitAmount)
fun deleteBudget(category: String)                   // id = "${currentUserId}_${category}" → repository.deleteBudget(id)
```

- `currentUserId`는 `AuthRepository.currentUserId`(주입). 설정 후 별도 새로고침 불필요 — `observeBudgets` Flow가 자동 재방출.
- 입력 검증(예: 0 이하 입력은 삭제로 처리하거나 거부)은 UI 또는 액션에서 일관되게.

### 2) 예산 편집 UI

- 대시보드 예산 섹션에 **편집 진입점**(예: 섹션 헤더의 "설정"/연필 아이콘, 또는 진행바 탭)을 둔다.
- 편집 시트/다이얼로그(`TransactionFormSheet` 스타일 또는 `LSyncInputDialog`)에서:
  - **카테고리 선택**: `FinanceCategory.all` + "전체"(= `TOTAL_CATEGORY`). 전체는 목록 상단에 "전체 월 한도"로 명확히.
  - **금액 입력**: 한도(원). 기존 한도가 있으면 prefill.
  - 확인 → `viewModel.setBudget(category, amount)`. 삭제 옵션 → `viewModel.deleteBudget(category)`.
- 입력 금액 포맷은 기존 금액 입력(천 단위 콤마 등) 패턴을 따른다.

## 핵심 규칙 (반드시 지킬 것)

- **설정/삭제는 `BudgetRepository`를 통하라**(ViewModel→Repository). DAO/Firestore를 UI에서 직접 호출하지 마라.
- **userId는 `AuthRepository.currentUserId`.** `"local_user"` 하드코딩 금지.
- **저장 후 수동 새로고침 금지** — `observeBudgets` Flow 자동 재방출에 의존(기존 Finance 패턴 일관).
- **`TOTAL_CATEGORY`는 "전체 월 한도"로 라벨링**하고 일반 카테고리와 명확히 구분. sentinel 문자열을 사용자에게 그대로 노출하지 마라.
- **디자인 토큰 준수**(Pretendard, 다크, Ghost chip, 그림자 금지, 새 색상 금지). 기존 시트/다이얼로그 컴포넌트 재사용.
- `collectAsState()`만.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 예산 설정/수정/삭제 UI가 있고 `setBudget`/`deleteBudget`로 Repository를 통하는가?
   - 카테고리 선택에 `FinanceCategory.all` + "전체 월 한도"(TOTAL)가 있는가?
   - 저장 후 Flow 자동 갱신(수동 새로고침 없음)인가?
   - userId가 `currentUserId`인가? 디자인 토큰·`collectAsState()` 준수?
3. 결과에 따라 `phases/16-budget/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "예산 편집 UI 완성 — FinanceDashboardViewModel setBudget/deleteBudget(currentUserId, Repository 경유), 대시보드 편집 진입점+입력 시트(FinanceCategory.all+전체 월 한도, 금액 prefill). Flow 자동 갱신. Phase 16 완료"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- UI에서 BudgetDao/FirestoreDataSource를 직접 호출하지 마라. 반드시 Repository 경유.
- 저장 후 수동 재조회하지 마라. Flow 자동 재방출 사용.
- sentinel `"__TOTAL__"`를 사용자에게 그대로 보여주지 마라. "전체 월 한도"로 라벨.
- 기존 거래 입력(TransactionFormSheet)·통계 카드 로직을 바꾸지 마라. 예산 편집만 추가.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
