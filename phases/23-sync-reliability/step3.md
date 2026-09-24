# Step 3: sync-status-screen

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/UI_GUIDE.md` — **디자인 시스템 전체**(색·폰트·ghost chip·카드 테두리)
- `docs/PRD.md` — **3. 마일스톤 Phase 1 게이트**("알림 누락 및 동기화 에러율 5% 미만")
- `app/src/main/java/com/lsync/app/data/local/dao/SyncLogDao.kt` — **step 0에서 생성됨**
- `app/src/main/java/com/lsync/app/data/sync/SyncLogger.kt` — **step 1에서 생성됨**(`SyncTarget` enum)
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` — **step 2에서 `retryFailed(userId): RetryResult` 추가됨**
- `app/src/main/java/com/lsync/app/ui/settings/NotificationSettingsScreen.kt` — **이 화면이 본뜰 원형**(헤더 메뉴로 진입하는 보조 화면, `onBack` 패턴)
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — 카드 레이아웃·수치 표기 스타일
- `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` — `composable("notifications")` 등록부와 `HomeScreen(...)` 호출부
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — `HomeScreen` 시그니처와 헤더 `DropdownMenu` 블록

step 0~2에서 만들어진 코드를 꼼꼼히 읽고, 각 로그 필드의 의미를 이해한 뒤 작업하라.

## 배경

step 0~2로 동기화 시도가 기록되고 실패가 자동 재전송된다. 하지만 사용자는 아직 그것을 **볼 수 없다.**

이 화면의 목적은 단 하나다 — **PRD Phase 1 게이트("동기화 에러율 5% 미만")를 앱 안에서 직접 판정하는 것.** 예쁜 대시보드가 아니라 판정 도구다.

## 작업

### 1. 파일: `app/src/main/java/com/lsync/app/ui/sync/SyncStatusViewModel.kt` (신규)

```kotlin
data class SyncStatusUiState(
    val windowDays: Int = 7,           // 집계 구간 — 7 또는 30
    val attempts: Int = 0,             // 구간 내 전체 시도 수(에러율 분모)
    val failures: Int = 0,             // 구간 내 실패 수(분자)
    val unresolved: Int = 0,           // 미해결 실패 건수(구간 무관)
    val logs: List<SyncLogEntity> = emptyList(),
    val isRetrying: Boolean = false,
) {
    val errorRate: Float   // attempts == 0이면 0f (0나눗셈 가드)
    val hasData: Boolean   // attempts > 0
    val passesGate: Boolean // errorRate < 0.05f
}

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncLogDao: SyncLogDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val uiState: StateFlow<SyncStatusUiState>

    fun setWindow(days: Int)   // 7 ↔ 30 전환. 전환 시 기존 구독을 취소하고 재구독한다
    fun retryNow()             // syncRepository.retryFailed(uid) 실행. 진행 중 isRetrying = true
}
```

구현 규칙:

- `authRepository.currentUserId`가 `null`이면 빈 상태를 유지한다(로그인 화면이 먼저 뜨므로 실사용 경로는 아니지만 크래시는 막는다).
- `since` 경계는 `windowDays`만큼 **일 단위로 뺀 epoch millis**다. 구간 전환 시 `ReportViewModel.previousMonth`가 `monthJob.cancel()` 후 재구독하는 것과 같은 방식으로 처리한다.
- 목록은 최근 50건이면 충분하다.
- `retryNow()`는 `viewModelScope`에서 실행하고, 끝나면 `isRetrying = false`. 결과 수치는 Room Flow가 알아서 갱신하므로 따로 상태로 들고 있지 않아도 된다.

### 2. 파일: `app/src/main/java/com/lsync/app/ui/sync/SyncStatusScreen.kt` (신규)

```kotlin
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
)
```

구성 요소:

1. **뒤로가기 + 제목** — `NotificationSettingsScreen`과 동일한 헤더 패턴
2. **구간 ghost chip** — `7일` / `30일`. active = `FgPrimary` solid (UI_GUIDE의 ghost chip 패턴)
3. **게이트 카드** — 에러율 퍼센트(소수 1자리), `실패 N / 시도 M` 병기, 게이트 통과 여부 표시. `attempts == 0`이면 수치 대신 "기록 없음" 안내
4. **미해결 실패 카드** — 건수 + `지금 재시도` 버튼. `unresolved == 0`이면 버튼을 비활성화하거나 숨긴다. `isRetrying` 중에는 중복 실행을 막는다
5. **최근 로그 목록** — 한 줄에 `시각 · target · 성공/실패`, 실패 행은 `errorType`(+ `errorMessage` 한 줄)까지. 해소된 실패(`resolvedAt != null`)는 실패와 구분되게 표시한다

**색 규칙:** `AccentRed`는 **게이트를 넘겼을 때(`errorRate >= 5%`)와 미해결 실패 행에만** 쓴다. 정상 상태에 빨강을 뿌리지 마라 — 이 앱의 디자인 의도는 "초과했을 때만 경고색"이다(예산 화면과 동일).

### 3. 파일: `app/src/main/java/com/lsync/app/ui/navigation/NavGraph.kt` (수정)

- `composable("sync") { SyncStatusScreen(onBack = { navController.popBackStack() }) }` 추가 — `composable("notifications")` 바로 아래
- `HomeScreen(...)` 호출부에 `onNavigateToSyncStatus = { navController.navigate("sync") }` 추가

### 4. 파일: `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` (수정)

- 시그니처에 `onNavigateToSyncStatus: () -> Unit` 파라미터 추가
- 헤더 `DropdownMenu`의 **"알림 설정" 항목 바로 아래**에 `"동기화 상태"` `DropdownMenuItem` 추가(기존 항목과 동일한 `Text` 스타일: `Pretendard`, 14sp, `FgPrimary`). 클릭 시 `onNavigateToSyncStatus()` + `menuExpanded = false`

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

**AC 판정 기준:** 출력에 `BUILD SUCCESSFUL`이 **보여야** 통과다. 종료 코드만 보고 판단하지 마라 — Bash로 `./gradlew`를 실행하면 JAVA_HOME을 못 찾고 아무 일도 하지 않은 채 종료 코드 0을 반환한 적이 있다. java를 못 찾으면:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:GRADLE_USER_HOME = "C:\ghome"; .\gradlew.bat assembleDebug lintDebug --no-configuration-cache --console=plain
```

## 검증 절차

1. 위 AC 커맨드를 실행하고 `BUILD SUCCESSFUL`을 확인한다. 기존 단위 테스트 64개가 전부 통과해야 한다.
2. **AC로는 화면 동작을 검증할 수 없다.** 코드를 다시 읽으며 하나씩 확인하라:
   - `collectAsStateWithLifecycle`을 쓰지 않았는가? (`collectAsState()`만 허용)
   - `@HiltViewModel` + `@Inject constructor` 패턴인가? (없으면 런타임 크래시)
   - `attempts == 0`일 때 0나눗셈이 나지 않는가?
   - `AccentRed`가 정상 상태에도 쓰이고 있지는 않은가?
   - `HomeScreen`의 새 파라미터가 `NavGraph` 호출부에 전달됐는가? (빠지면 컴파일 에러)
   - 하단 탭(`bottomNavItems`)에 항목을 추가하지 않았는가? (4탭 불변)
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`ui/sync/`)
   - CLAUDE.md CRITICAL 규칙: `collectAsStateWithLifecycle` 금지, `@HiltViewModel` 패턴, `userId`는 `AuthRepository.currentUserId`
   - Room이 SSOT인가? **화면이 Firestore를 직접 구독하지 않는가?** 이 화면은 `SyncLogDao`의 Flow만 본다
   - **새 DAO·Repository를 추가하지 않았으므로 `di/AppModule.kt` 수정이 필요 없다.** 기존 `provideSyncLogDao`(step 0)를 그대로 주입받는다
4. 결과에 따라 `phases/23-sync-reliability/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **로그 삭제·초기화 버튼을 만들지 마라.** 이유: 이 기록은 PRD 게이트 측정 이력이다. 지울 수 있으면 수치가 언제든 리셋돼 판정이 무의미해진다. 정리는 `SyncLogger`의 30일 가지치기가 알아서 한다.
- **하단 탭을 추가하지 마라.** 4탭(홈·일정·가계부·성경)은 불변이다. 진입은 홈 헤더 메뉴뿐이다.
- **`navTarget` 딥링크 분기를 추가하지 마라.** 이유: 알림·위젯에서 이 화면으로 올 경로가 없다. `NavGraph`의 `LaunchedEffect`는 검증된 코드이고, 하단 탭이 아닌 라우트를 잘못 끼우면 조용히 무시되거나 기존 분기를 깨뜨린다.
- **`SyncLogger`·`SyncRepository.retryFailed`의 로직을 바꾸지 마라.** 이 step은 읽기와 버튼 호출만 한다.
- **에러율을 화면에서 다시 계산해 다른 정의를 만들지 마라.** 분모는 `attempts`, 분자는 `failures`이며 재시도는 분모에 들어가지 않는다(step 2 설계).
- **알림 누락 계측을 여기에 끼워 넣지 마라.** 이번 phase의 범위는 동기화뿐이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
