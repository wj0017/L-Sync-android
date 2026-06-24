# Step 3: navigation-entry

도메인 횡단 **월간 리포트** 화면(Step 2의 `ReportScreen`)을 앱에 연결하는 마지막 단계다. NavGraph에 라우트를 추가하고, 홈 화면 헤더에서 진입할 수 있게 한다. `search` 화면과 동일한 "홈에서 진입하는 별도 화면" 패턴을 따른다.

## 읽어야 할 파일

먼저 아래 파일들을 읽고 기존 진입 패턴을 파악하라:

- `docs/ARCHITECTURE.md` — "통합 검색" 절(`composable("search")` 진입 패턴이 이 화면의 모델)
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` — **수정 대상**. `composable("search") { SearchScreen(onBack = { navController.popBackStack() }, ...) }` 패턴과 `onNavigateToSearch = { navController.navigate("search") }` 와이어링을 그대로 따라하라. 하단 탭(`bottomNavItems`)에는 추가하지 않는다
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — **수정 대상**. 헤더 Row(파일 상단, 약 127행)에 검색 `IconButton`(약 144행, `Icons.Outlined.Search`, `onClick = onNavigateToSearch`)이 있다. 그 옆에 리포트 진입 아이콘을 추가한다. `HomeScreen`의 파라미터 목록에 `onNavigateToSearch: () -> Unit`가 있는 형태를 참고
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — **Step 2 산출물**. `ReportScreen(onBack: () -> Unit, ...)` 시그니처 확인
- `app/src/main/java/com/lsync/app/ui/search/SearchScreen.kt` — `onBack` 콜백 사용 확인(레퍼런스)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. `NavGraph.kt` — report 라우트 추가

`composable("search") { ... }` 블록과 같은 레벨에 추가한다:

```kotlin
composable("report") {
    ReportScreen(onBack = { navController.popBackStack() })
}
```

- `import com.lsync.app.ui.report.ReportScreen` 추가.
- **하단 탭(`bottomNavItems`)에 추가하지 마라.** 4-tab 구조(홈/일정/가계부/성경)를 유지하고, 리포트는 search처럼 홈에서 진입하는 별도 화면이다.
- `HomeScreen(...)` 호출부에 `onNavigateToReport = { navController.navigate("report") }`를 전달한다(아래 2번에서 `HomeScreen`이 이 파라미터를 받도록 수정).

### 2. `HomeScreen.kt` — 진입 아이콘 추가

- `HomeScreen` Composable 파라미터에 `onNavigateToReport: () -> Unit`를 추가한다(기존 `onNavigateToSearch: () -> Unit` 옆).
- 헤더 Row(검색 `IconButton`이 있는 곳)에 리포트 진입 `IconButton`을 하나 더 추가하고 `onClick = onNavigateToReport`로 연결한다.
- 아이콘은 통계/리포트 의미에 맞는 Material 아이콘을 사용하라(예: `Icons.Outlined.BarChart`, `Icons.Outlined.Assessment`, `Icons.Outlined.InsertChart` 등 — import 필요). 검색 아이콘과 동일한 크기·tint 관례를 따르라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

빌드 통과 후, 가능하면 홈 화면 헤더에 리포트 아이콘이 보이고 탭하면 리포트 화면으로 이동하는지 논리적으로 확인하라(NavGraph 라우트 ↔ HomeScreen 콜백 ↔ ReportScreen `onBack` 연결).

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - ARCHITECTURE.md 디렉토리 구조/패턴을 따르는가? (search와 동일한 별도 화면 진입, 하단 탭 미추가)
   - CLAUDE.md CRITICAL 규칙 위반 없는가?
   - 4-tab 하단 네비게이션 구조를 깨지 않았는가?
3. 결과에 따라 `phases/19-monthly-report/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "NavGraph report 라우트 + HomeScreen 진입 아이콘 연결"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- 리포트를 하단 탭(`bottomNavItems`)에 추가하지 마라. 이유: 4-tab 구조 유지가 설계 결정이며, 리포트는 search처럼 홈 진입 별도 화면이다.
- `ReportScreen`/`ReportViewModel`의 내부를 수정하지 마라. 이유: 화면·집계는 이전 step에서 확정됐다. 이 step은 진입 배선만 한다.
- 새 ViewModel이나 Repository를 만들지 마라. 이 step은 네비게이션 배선뿐이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
