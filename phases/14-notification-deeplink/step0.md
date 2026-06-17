# Step 0: deeplink-infra

## 배경

이 task(`14-notification-deeplink`)는 "알림·위젯 탭 → 해당 화면 딥링크 + 알림 완료 액션"이다. 현재는:

- 알림(`AlarmReceiver`)을 탭해도 아무 일도 일어나지 않는다(`contentIntent` 없음).
- 위젯(`LSyncWidget`)을 탭해도 아무 일도 일어나지 않는다(클릭 없음).
- `MainActivity`는 `MAIN/LAUNCHER`만 있고 딥링크 인텐트를 처리하지 않는다. `launchMode` 미지정(standard).
- `NavGraph`는 `navController`를 내부에서 생성하므로 외부에서 특정 탭으로 이동시킬 진입점이 없다.

이 Step은 **딥링크 인프라(메커니즘)만** 만든다. 외부 인텐트의 extra(`nav_target`)를 읽어 해당 탭(`home`/`schedule`/`finance`/`bible`)으로 이동시키는 배선을 깐다. 알림·위젯 쪽 연결은 다음 Step(1·2)에서 이 메커니즘을 **소비**한다.

이 Step에서는 알림/위젯 코드를 건드리지 않는다. adb로 직접 인텐트를 쏴서 검증한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 기존 패턴(특히 `NavGraph`가 탭 이동을 어떻게 하는지)을 파악하라:

- `docs/ARCHITECTURE.md` — 레이어 구조, 디렉토리. `ui/navigation/NavGraph.kt` 항목.
- `CLAUDE.md` — CRITICAL 규칙(`collectAsState()` 사용, `collectAsStateWithLifecycle` 금지 등).
- `app/src/main/java/com/lsync/app/MainActivity.kt` — **수정 대상.** `setContent`, `AuthViewModel` 구독, `NavGraph(authViewModel)` 호출 위치. 권한 다이얼로그 `LaunchedEffect` 패턴.
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` — **수정 대상.** `sealed class Screen`(route 문자열: `home`/`schedule`/`finance`/`bible`), 내부 `rememberNavController()`, `bottomNavItems`, 탭 이동 시 `navigate(route){ popUpTo(...); launchSingleTop; restoreState }` 패턴, 미로그인 시 `LoginScreen` early return.
- `app/src/main/AndroidManifest.xml` — **수정 대상.** `<activity android:name=".MainActivity">` 블록.

## 작업

목표: 외부에서 `MainActivity`를 `nav_target` extra와 함께 띄우면, 로그인 상태가 되는 즉시 NavGraph가 해당 탭으로 이동한다. 앱이 이미 떠 있을 때(알림 재탭 등)도 동작한다.

### 1) Manifest

`MainActivity`에 `android:launchMode="singleTop"`을 추가한다. 이유: 앱이 떠 있을 때 알림/위젯을 다시 탭하면 새 인스턴스를 쌓지 않고 `onNewIntent`로 들어오게 하기 위함.

### 2) `MainActivity`

- 딥링크 타깃을 담을 상태를 Activity 레벨에 둔다(예: `private var navTarget by mutableStateOf<String?>(null)` — Compose가 읽고 재구성되도록 `mutableStateOf` 사용).
- `onCreate`에서 `intent`의 `nav_target` extra를 읽어 초기값으로 설정한다.
- `onNewIntent(intent)`를 override해서 `setIntent(intent)` 후 `nav_target`을 다시 읽어 상태를 갱신한다. 이유: singleTop이라 앱이 떠 있을 때 재진입은 `onCreate`가 아닌 `onNewIntent`로 들어온다.
- `NavGraph` 호출 시 현재 `navTarget`과, 소비 후 초기화할 콜백(`onTargetConsumed = { navTarget = null }`)을 함께 넘긴다. 시그니처는 아래 NavGraph 변경에 맞춘다.
- extra 키 상수는 한 곳에 정의한다(예: `MainActivity`의 `companion object`에 `const val EXTRA_NAV_TARGET = "nav_target"`). 다음 Step들이 이 상수를 참조한다.

### 3) `NavGraph`

- 시그니처를 확장한다(권장):
  ```kotlin
  @Composable
  fun NavGraph(
      authViewModel: AuthViewModel = hiltViewModel(),
      navTarget: String? = null,
      onTargetConsumed: () -> Unit = {},
  )
  ```
- 기존 미로그인 early return(`LoginScreen`)·`rememberNavController()`·`Scaffold` 구조는 그대로 둔다.
- `NavHost` 영역에서 `LaunchedEffect(authState.isSignedIn, navTarget)`를 추가한다:
  - 로그인 상태이고 `navTarget`이 유효한 탭 route(`home`/`schedule`/`finance`/`bible`)일 때만 기존 탭 이동 코드(`navController.navigate(target){ popUpTo(findStartDestination().id){ saveState=true }; launchSingleTop=true; restoreState=true }`)를 실행하고, 직후 `onTargetConsumed()`를 호출한다.
  - 유효하지 않은 값/`null`이면 아무것도 하지 않는다.
- 콜드 스타트 + 미로그인 시 target 유실 방지: target은 MainActivity 상태에 보존되고 `LaunchedEffect`의 key에 `isSignedIn`이 포함되므로, 로그인 완료(authState 변경) 시점에 자동으로 이동이 트리거된다. **target을 로그인 전에 소비하거나 버리지 마라.**

## 핵심 규칙 (반드시 지킬 것)

- **딥링크 타깃은 탭(화면) 레벨 문자열만.** 유효값: `home`/`schedule`/`finance`/`bible`. 특정 날짜/항목으로의 정밀 이동은 이번 task 범위가 아니다. 이유: `ScheduleScreen`에 selectedDate 파라미터 추가가 필요해 scope가 커진다.
- **`collectAsStateWithLifecycle`를 쓰지 마라. `collectAsState()`만 사용한다.** (CLAUDE.md CRITICAL)
- **기존 탭 이동 옵션(`popUpTo`/`launchSingleTop`/`restoreState`)을 그대로 재사용하라.** 새 네비 방식을 발명하지 마라.
- **알림(`AlarmReceiver`)·위젯(`LSyncWidget`) 코드를 이 Step에서 건드리지 마라.** 이유: 이 Step은 인프라만. 소비는 Step 1·2.
- target 소비 후 반드시 `onTargetConsumed()`로 초기화하라. 이유: 초기화하지 않으면 화면 회전 등 재구성 때 의도치 않게 재이동한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

설치 후 수동 검증(에이전트가 빌드까지만 보장, 아래는 참고):

```bash
adb shell am start -n com.lsync.app/.MainActivity --es nav_target finance   # 가계부 탭으로 열림
```

## 검증 절차

1. 위 AC 커맨드(`assembleDebug`, `lintDebug`)를 실행한다.
2. 아키텍처 체크리스트:
   - `nav_target` extra → 로그인 후 해당 탭 이동 배선이 들어갔는가?
   - `MainActivity`가 `singleTop` + `onNewIntent` 처리하는가?
   - `LaunchedEffect` key에 `isSignedIn`이 포함돼 콜드 스타트/미로그인에서도 target이 보존·소비되는가?
   - `collectAsState()`만 사용했는가? UI가 Firestore를 직접 구독하지 않는가?(이 Step은 네비만이라 무관해야 정상)
3. 결과에 따라 `phases/14-notification-deeplink/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "MainActivity(singleTop+onNewIntent)가 nav_target extra를 읽어 navTarget 상태로 보존, NavGraph(navTarget, onTargetConsumed)가 LaunchedEffect(isSignedIn,navTarget)로 해당 탭 이동·소비. EXTRA_NAV_TARGET 상수 정의. 유효값 home/schedule/finance/bible"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- 특정 날짜/항목으로의 정밀 딥링크를 구현하지 마라. 이유: scope 확대(ScheduleScreen 파라미터화 필요). 이번엔 탭 레벨만.
- `AlarmReceiver`/`LSyncWidget`을 수정하지 마라. 이유: Step 1·2의 범위다.
- 새 네비게이션 라이브러리/딥링크 라우팅 DSL을 도입하지 마라. 기존 `NavHost`+extra 방식으로 처리한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
