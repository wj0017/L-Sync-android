# Step 2: login-screen

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/UI_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/ui/auth/AuthViewModel.kt`  ← step 1에서 생성됨
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt`
- `app/src/main/java/com/lsync/app/MainActivity.kt`
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt`

## BLOCKED 조건 확인 (반드시 선행)

이 step을 진행하기 전에 아래 리소스가 존재하는지 확인하라:

```bash
grep -r "default_web_client_id" app/src/main/res/
```

또는 파일 시스템에서 `app/src/main/res/values/google-services.xml` 또는 `app/google-services.json`이 Google Sign-In web client ID를 포함하는지 확인한다.

**`R.string.default_web_client_id`가 없으면 컴파일이 실패한다.** 이 경우:
- `phases/7-firebase-auth/index.json`의 step 2를 `"status": "blocked"`로 표시
- `"blocked_reason": "R.string.default_web_client_id 미생성. Firebase Console에서 Google Sign-In 활성화 + 디버그 SHA-1 등록 + google-services.json 재다운로드 필요"` 기록
- 즉시 중단

리소스가 존재하면 아래 작업을 진행한다.

## 이전 step 요약

Step 0: `di/Qualifiers.kt`, `data/repository/AuthRepository.kt`, `AppModule.kt`(FirebaseAuth + ApplicationScope providers), `play-services-auth` 의존성 추가
Step 1: `ui/auth/AuthViewModel.kt` — `AuthUiState`, `@HiltViewModel` 패턴, 초기값 즉시 계산

## 작업

### 1. `app/src/main/java/com/lsync/app/ui/auth/LoginScreen.kt` 신규 생성

UI_GUIDE.md 디자인 시스템을 따른다 (다크 미니멀, BgPrimary `#0A0A0A`, AccentBlue `#4F7EFF`, FgPrimary `#F5F5F5`, AccentRed `#E53935`).

```kotlin
package com.lsync.app.ui.auth

@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    // ... Google Sign-In launcher, UI
}
```

구현 지침:
- `GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(context.getString(R.string.default_web_client_id)).requestEmail().build()`
- `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` 패턴으로 Google 로그인 결과 처리
- 성공 시 `GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken`를 `viewModel.signInWithGoogle(idToken)`에 전달
- `uiState.isLoading == true`일 때 `CircularProgressIndicator` 표시 (색상: `AccentBlue`)
- `uiState.error != null`일 때 에러 텍스트 표시 (색상: `AccentRed`)
- 배경: `BgPrimary` (#0A0A0A), 앱 이름 "L-Sync" 표시 (32sp, FgPrimary)
- Google 로그인 버튼: `AccentBlue` 배경

### 2. `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` 수정

로그인 상태에 따라 분기하는 로직 추가.

```kotlin
@Composable
fun NavGraph(authViewModel: AuthViewModel = hiltViewModel()) {
    val authState by authViewModel.uiState.collectAsState()

    if (!authState.isSignedIn) {
        LoginScreen(viewModel = authViewModel)
        return
    }

    // 기존 Scaffold + BottomNavigationBar + NavHost 코드는 그대로 유지
}
```

**규칙:** `NavHost`에 로그인 라우트를 추가하지 않는다. `if (!isSignedIn) return` 패턴으로 NavHost 진입 전 차단한다. 이유: BottomBar가 LoginScreen 위에 노출되는 것을 방지.

### 3. `app/src/main/java/com/lsync/app/MainActivity.kt` 수정

권한 요청(POST_NOTIFICATIONS 등)을 로그인 이후로 지연한다.

기존 `LaunchedEffect` 권한 요청 블록을 `authState.isSignedIn` 조건부로 감싼다:

```kotlin
val authViewModel: AuthViewModel = hiltViewModel()
val authState by authViewModel.uiState.collectAsState()

LaunchedEffect(authState.isSignedIn) {
    if (!authState.isSignedIn) return@LaunchedEffect
    // 기존 권한 체크 로직 (POST_NOTIFICATIONS 등)
}
```

`authViewModel`은 `NavGraph`에도 전달해 동일 인스턴스를 재사용한다:
```kotlin
NavGraph(authViewModel = authViewModel)
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. BLOCKED 조건을 먼저 확인한다 (위 설명 참고).
2. 위 AC 커맨드를 실행한다.
3. 체크리스트를 확인한다:
   - `ui/auth/LoginScreen.kt`가 생성됐는가?
   - `NavGraph.kt`에서 `!authState.isSignedIn`이면 `LoginScreen`으로 early return하는가?
   - `MainActivity.kt` 권한 요청이 `isSignedIn` 조건부로 지연됐는가?
   - `collectAsStateWithLifecycle`를 사용하지 않았는가?
   - UI_GUIDE.md 색상 시스템을 따르는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
4. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - R.string.default_web_client_id 없음 → `"status": "blocked"`, `"blocked_reason": "R.string.default_web_client_id 미생성. Firebase Console에서 Google Sign-In 활성화 + 디버그 SHA-1 등록 + google-services.json 재다운로드 필요"` 즉시 중단

## 금지사항

- `NavHost`에 `loginRoute`를 추가하지 마라. 이유: BottomBar와 공존하면 로그인 화면에 탭 바가 노출된다. `if (!isSignedIn) return` 패턴을 사용한다.
- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md 금지 규칙.
- 라이트 모드 색상을 사용하지 마라. 이유: L-Sync는 다크 전용이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
