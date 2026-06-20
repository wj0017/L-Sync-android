# Step 3: search-screen

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도, 디자인 시스템을 파악하라:

- `CLAUDE.md` (디자인 시스템·색상·폰트 섹션)
- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/ui/search/SearchViewModel.kt` — step 2 생성. `SearchUiState(query, results, hasSearched)`, `onQueryChange`, `clearQuery`
- `app/src/main/java/com/lsync/app/data/repository/SearchRepository.kt` — step 1 생성. `SearchResults(events, todos, finances)`, `isEmpty`, `totalCount`
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt` / `TodoEntity.kt` / `FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — 카드/행/색상 스타일 참고 (`HomeScheduleRow`, `HomeSectionHeader`, `BgCard`/`HairlineWhite`/`FgPrimary` 등)
- `app/src/main/java/com/lsync/app/ui/theme/` (Color.kt, Type.kt) — 색상·폰트 토큰

이전 step에서 만들어진 ViewModel/모델을 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 배경

통합 검색 task의 네 번째 단계다. step 2의 `SearchViewModel`을 구독하는 **전체화면 검색 Composable**을 만든다. NavGraph 배선과 홈 진입점은 다음 step에서 다룬다.

## 작업

`app/src/main/java/com/lsync/app/ui/search/SearchScreen.kt` 새 파일 생성.

시그니처:
```kotlin
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToSchedule: () -> Unit,   // 일정/할일 결과 탭 시
    onNavigateToFinance: () -> Unit,    // 가계부 결과 탭 시
    viewModel: SearchViewModel = hiltViewModel(),
)
```

레이아웃 요구사항:
1. **상단 검색 바**: 뒤로가기 아이콘(`Icons.AutoMirrored.Outlined.ArrowBack`, `onBack` 호출) + `TextField`/`BasicTextField`. 텍스트 값은 `uiState.query` 단일 소스, 변경 시 `viewModel.onQueryChange(it)`. 비어있지 않으면 우측에 지우기(X) 아이콘 → `viewModel.clearQuery()`. 진입 시 자동 포커스(`FocusRequester` + `LaunchedEffect`)와 키보드 노출이 바람직하다.
2. **결과 영역** (`LazyColumn`):
   - 섹션 헤더("일정", "할 일", "가계부")는 해당 리스트가 비어있지 않을 때만 노출. 헤더 스타일은 HomeScreen의 `HomeSectionHeader`(FgTertiary, 11sp, letterSpacing 0.12.em, uppercase 불필요)를 참고하되 이 파일 내 private 컴포넌트로 새로 만들어라(HomeScreen의 private 함수는 재사용 불가).
   - **일정 행**: 제목(`event.title`) + 종일이면 "종일", 아니면 `startDate`의 `THH:mm` 표시. 탭 시 `onNavigateToSchedule()`.
   - **할 일 행**: 제목(`todo.title`) + 완료 시 취소선/흐림. 연동 금액 있으면 `₩%,d`(InstrumentSerif Italic) 표시. 탭 시 `onNavigateToSchedule()`.
   - **가계부 행**: `category` + `note`(있으면) + 금액 `₩%,d`(EXPENSE는 FgSecondary, INCOME은 AccentGreen). 탭 시 `onNavigateToFinance()`.
   - 행 카드 스타일: `BgCard` 배경 + `HairlineWhite` 1dp 보더 + `RoundedCornerShape(10~12.dp)`. HomeScreen `HomeScheduleRow`와 시각적으로 일관되게.
3. **상태별 빈 화면**:
   - `uiState.query`가 비어 있음 → "검색어를 입력하세요" 류 안내(FgDisabled).
   - `uiState.hasSearched && uiState.results.isEmpty` → "검색 결과가 없어요"(FgDisabled).

디자인 규칙(CLAUDE.md):
- 다크 미니멀. 배경 `BgPrimary`, 카드 `BgCard`, 텍스트 `FgPrimary`/`FgSecondary`/`FgTertiary`/`FgDisabled`.
- 폰트: 본문/UI는 `Pretendard`, 금액은 `InstrumentSerif` Italic.
- 지출 금액에 `AccentRed`를 남발하지 마라(절제). EXPENSE 금액은 `FgSecondary`로(HomeScreen FinanceSummaryCard 관례와 동일).
- `collectAsState()`로 `uiState`를 구독한다. **`collectAsStateWithLifecycle` 금지.**

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처/디자인 체크리스트:
   - `collectAsState()`를 사용했는가? (`collectAsStateWithLifecycle` 금지 — CRITICAL)
   - 색상·폰트 토큰을 `ui/theme`에서 가져왔는가? (하드코딩 hex 금지)
   - 빈 쿼리 / 결과 없음 두 상태를 구분 표시하는가?
   - 결과 행 탭이 적절한 콜백(`onNavigateToSchedule`/`onNavigateToFinance`)을 호출하는가?
3. 결과에 따라 `phases/17-global-search/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "SearchScreen(상단 검색바+자동포커스, 섹션별 LazyColumn[일정/할일/가계부], 빈/결과없음 상태, 결과 탭→onNavigateToSchedule/Finance)"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `NavGraph`·`HomeScreen`·`MainActivity`를 건드리지 마라. 이 step의 범위는 `SearchScreen.kt` 한 파일뿐이다. (배선은 step 4)
- HomeScreen의 `private` 컴포저블을 import/재사용하려 하지 마라. 이유: private 가시성이라 컴파일 불가. 필요한 작은 컴포넌트는 이 파일 안에 새로 만들어라.
- 결과 행에서 항목 편집·삭제 기능을 넣지 마라. 이유: 이번 task 범위는 "검색 → 탭으로 해당 탭 이동"까지다. 항목 단위 딥링크/편집은 범위 밖.
- 색상 hex를 하드코딩하지 마라. `ui/theme` 토큰만 사용하라.
- 기존 화면을 리팩토링하지 마라.
