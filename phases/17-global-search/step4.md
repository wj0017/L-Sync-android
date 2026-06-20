# Step 4: search-nav

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` — 라우트 정의·`composable`·하단 네비게이션·딥링크 `LaunchedEffect`
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — 헤더 `Row`(앱 이름 L·SYNC + 날짜/메뉴), `HomeScreen` 시그니처
- `app/src/main/java/com/lsync/app/ui/search/SearchScreen.kt` — step 3 생성. 시그니처 `SearchScreen(onBack, onNavigateToSchedule, onNavigateToFinance, viewModel)`

이전 step에서 만들어진 `SearchScreen`을 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 배경

통합 검색 task의 마지막 단계다. step 3까지 만든 검색 화면을 **네비게이션에 배선**하고 **홈 헤더에 검색 진입점**을 추가한다.

확정된 설계:
- 진입점: 홈(`HomeScreen`) 헤더의 검색 아이콘 → `search` 라우트로 전체화면 이동.
- `search`는 **하단 네비게이션 탭이 아니다**. `bottomNavItems`에 넣지 마라.
- 검색 결과 탭 → 해당 탭(일정/가계부)으로 이동만 한다(항목 딥링크 없음).

## 작업

### 1. NavGraph에 search 라우트 추가

`NavGraph.kt`:
- `NavHost` 블록 안에 `composable("search") { … }`를 추가하고 `SearchScreen`을 배치한다.
  - `onBack = { navController.popBackStack() }`
  - `onNavigateToSchedule = { navController.navigate(Screen.Schedule.route){ … } }`
  - `onNavigateToFinance = { navController.navigate(Screen.Finance.route){ … } }`
  - 탭 이동 시 기존 `bottomNavItems` 클릭과 동일한 navigate 옵션(`popUpTo(findStartDestination){saveState=true}`, `launchSingleTop=true`, `restoreState=true`)을 사용해 하단 탭 선택 상태가 올바르게 갱신되도록 한다. 결과로 이동한 뒤 검색 화면은 백스택에서 자연히 벗어난다.
- `HomeScreen(...)` 호출부에 `onNavigateToSearch = { navController.navigate("search") }` 인자를 추가한다.

CRITICAL: `search` 라우트를 `bottomNavItems` 리스트에 추가하지 마라. 이유: 하단 바에 5번째 탭이 생기고, 딥링크 `LaunchedEffect`의 `bottomNavItems.none { it.route == navTarget }` 가드 로직과 충돌한다.

### 2. HomeScreen에 검색 진입점 추가

`HomeScreen.kt`:
- `HomeScreen` 컴포저블 시그니처에 `onNavigateToSearch: () -> Unit` 파라미터를 추가한다(기존 `onNavigateToSchedule`, `onNavigateToBible`와 같은 위치).
- 헤더 `Row`(앱 이름 L·SYNC와 날짜/메뉴가 있는 곳, 약 124~210행)의 우측 영역에 검색 `IconButton`을 추가한다. `Icons.Outlined.Search` 사용, `contentDescription = "검색"`, `tint = FgSecondary`, 클릭 시 `onNavigateToSearch()`. 기존 `MoreVert` 메뉴 아이콘 옆(왼쪽)에 배치해 시각적 균형을 맞춘다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `search` 라우트가 `composable("search")`로 등록되고 `SearchScreen`이 배치됐는가?
   - `search`가 `bottomNavItems`에 **들어가지 않았는가**? (CRITICAL)
   - `HomeScreen` 시그니처에 `onNavigateToSearch`가 추가되고 헤더에 검색 아이콘이 노출되는가?
   - 결과 탭 이동이 기존 하단 탭 navigate 옵션과 일관된가?
   - 딥링크 `LaunchedEffect` 로직을 깨뜨리지 않았는가? (navTarget 가드는 `bottomNavItems` 기준이므로 search 추가가 영향 주지 않아야 함)
3. 결과에 따라 `phases/17-global-search/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "NavGraph composable(\"search\")+SearchScreen 배선, HomeScreen onNavigateToSearch+헤더 검색 아이콘. 통합 검색 task 완료"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `search`를 하단 네비게이션 탭으로 추가하지 마라. 이유: 위 CRITICAL 참조(딥링크 가드 충돌 + UX 의도 위반).
- `SearchScreen`/`SearchViewModel`/`SearchRepository`/DAO를 수정하지 마라. 이 step의 범위는 NavGraph + HomeScreen 배선뿐이다.
- 결과 항목별 딥링크(특정 날짜·항목으로 포커스)를 구현하지 마라. 이유: 확정된 범위는 "해당 탭 이동"까지다.
- 기존 라우트·하단 탭 구조를 리팩토링하지 마라.
