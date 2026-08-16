# Step 2: widget-report-deeplink

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — § 알림·위젯 딥링크 + 알림 완료 액션
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — **`navIntent(context, target)` + `actionStartActivity` 패턴의 기준. 수정 금지.**
- `app/src/main/java/com/lsync/app/ui/widget/ReportWidget.kt` — **step 1 산출물. 수정 대상.**
- `app/src/main/java/com/lsync/app/MainActivity.kt` — `EXTRA_NAV_TARGET`, `navTarget`, `onNewIntent`
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` — **`LaunchedEffect(authState.isSignedIn, navTarget)` 블록이 이 step의 핵심. 수정 대상.**

**step 1에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경 — 반드시 알아야 할 제약

현재 딥링크 처리는 `NavGraph.kt`의 `LaunchedEffect` 안에 있고, 이런 가드가 걸려 있다:

```kotlin
if (navTarget == null || bottomNavItems.none { it.route == navTarget }) return@LaunchedEffect
```

즉 **하단 탭에 없는 타깃은 조기 반환되어 무시된다.** 리포트는 하단 4-tab이 아니라 홈 헤더에서 진입하는 별도 화면(`composable("report")`, `NavGraph.kt:149`)이므로, `"report"`를 그대로 넘기면 **아무 일도 일어나지 않는다.** 분기를 추가해야 한다.

리포트 라우트는 이미 등록돼 있다 — 새로 만들 필요 없다(`NavGraph.kt:142`의 `onNavigateToReport = { navController.navigate("report") }`가 홈 헤더 진입 경로다).

## 작업

### 1. `ReportWidget.kt` — 탭 시 리포트로 이동

- `LSyncWidget.kt`의 `navIntent(context, target)` + `actionStartActivity` 패턴을 그대로 쓴다.
- `navIntent`는 `LSyncWidget.kt`의 **private top-level 함수**라 import할 수 없다. `ReportWidget.kt` 안에 동일한 형태로 다시 선언하라(`Intent(context, MainActivity::class.java)` + `putExtra(MainActivity.EXTRA_NAV_TARGET, target)` + `LSyncWidget.kt`가 쓰는 것과 같은 플래그). **`LSyncWidget.kt`를 수정해 public으로 바꾸지 마라.**
- 위젯 루트(또는 각 카드)에 `.clickable(actionStartActivity(navIntent(context, "report")))`를 건다.

### 2. `NavGraph.kt` — `"report"` 타깃 분기

`LaunchedEffect(authState.isSignedIn, navTarget)` 안에서 `"report"`를 별도로 처리한다.

- 로그인 가드(`if (!authState.isSignedIn) return@LaunchedEffect`)는 **그대로 유지한다.** 이유: 콜드스타트·미로그인 시 target이 보존됐다가 로그인 후 소비되는 동작이 Phase 13에서 의도적으로 만들어진 것이다.
- `navTarget == "report"`이면 하단 탭 이동 대신 `navController.navigate("report")`를 호출하고 `onTargetConsumed()`를 호출한다.
- 그 외에는 **기존 하단 탭 로직을 그대로 둔다.** `bottomNavItems.none { ... }` 가드도 유지한다.

**기존 4탭 딥링크 동작을 깨지 마라.** 알림(`AlarmReceiver`)과 기존 5×2 위젯이 `"schedule"`/`"finance"`/`"bible"`을 보내고 있고, 이 경로들이 계속 동일하게 동작해야 한다.

`MainActivity`는 `EXTRA_NAV_TARGET`을 문자열로 그대로 전달하므로 **수정이 필요 없을 가능성이 높다.** 코드를 읽고 실제로 필요할 때만 수정하라.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `NavGraph.kt`의 `LaunchedEffect`를 읽고 세 경로를 각각 추적하라:
   - `navTarget == "schedule"` → 기존대로 하단 탭 이동 (**회귀 없음 확인**)
   - `navTarget == "report"` → `navigate("report")`
   - `navTarget == null` 또는 미로그인 → 아무 일 없음, target 보존
3. `onTargetConsumed()`가 **모든 처리 경로에서** 호출되는지 확인하라. 누락되면 같은 타깃이 재실행되거나 다음 딥링크가 무시된다.
4. 아키텍처 체크리스트:
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가? (`collectAsStateWithLifecycle` 금지 등)
   - 하단 4-tab 구성이 그대로인가? (리포트는 탭이 아니다)
5. 결과에 따라 `phases/20-widget-report/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **리포트를 하단 탭에 추가하지 마라.** 이유: 하단 4-tab(홈·일정·가계부·성경) 구성은 확정된 IA다. 리포트·검색은 홈 헤더에서 진입하는 별도 화면이다.
- **`LSyncWidget.kt`를 수정하지 마라.** `navIntent`를 public으로 바꾸거나 공용화하지 마라 — 검증된 파일을 건드리지 않는다.
- **기존 `bottomNavItems` 가드를 제거하지 마라.** 이유: 그 가드가 없으면 미등록 라우트가 들어왔을 때 `navigate`가 예외를 던진다.
- **`AlarmReceiver`나 알림 인텐트를 수정하지 마라.** 범위 밖이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
