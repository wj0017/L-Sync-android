# Step 3: permission-flow

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt`

파일들을 꼼꼼히 읽고 현재 구조를 이해한 뒤 작업하라.

## 작업

Android 13+(API 33) `POST_NOTIFICATIONS` 권한과 Android 12+(API 31) `SCHEDULE_EXACT_ALARM` 권한을 앱 시작 시 요청하는 흐름을 구현하라.

### 1. AndroidManifest.xml 권한 선언

아직 없다면 다음 권한을 추가하라:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### 2. PermissionHelper 생성

`app/src/main/java/com/lsync/app/ui/PermissionHelper.kt`를 신규 생성하라.

시그니처:

```kotlin
object PermissionHelper {
    fun needsNotificationPermission(context: Context): Boolean
    fun needsExactAlarmPermission(context: Context): Boolean
    fun openExactAlarmSettings(context: Context)
}
```

- `needsNotificationPermission`: Android 13+ (Build.VERSION.SDK_INT >= 33)이고 `POST_NOTIFICATIONS` 미승인 시 true
- `needsExactAlarmPermission`: Android 12+ (Build.VERSION.SDK_INT >= 31)이고 AlarmManager의 `canScheduleExactAlarms()` == false 시 true
- `openExactAlarmSettings`: `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` Intent로 시스템 설정 화면으로 이동

### 3. MainActivity 업데이트

`MainActivity.kt`에서 앱 시작 시 권한을 요청하라.

구현 요구사항:
- `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`으로 `POST_NOTIFICATIONS` 권한 요청 런처를 등록한다.
- `LaunchedEffect(Unit)`에서 `PermissionHelper.needsNotificationPermission()`을 확인하고 필요하면 런처를 실행한다.
- `SCHEDULE_EXACT_ALARM`은 일반 권한 요청 API가 없으므로, `PermissionHelper.needsExactAlarmPermission()`이 true이면 Snackbar 또는 간단한 다이얼로그로 사용자에게 안내한 뒤 `openExactAlarmSettings()`로 시스템 설정을 연다.

**디자인 규칙:**
- 다이얼로그나 Snackbar를 표시할 경우 CLAUDE.md 디자인 시스템을 따른다: `BgCard` 배경, `FgPrimary` 텍스트, `AccentBlue` 확인 버튼.
- 권한을 거부해도 앱이 강제 종료되거나 기능이 완전히 차단되어서는 안 된다. 알람 기능만 비활성화하고 나머지 기능은 정상 동작해야 한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `AndroidManifest.xml`에 세 권한이 모두 선언됐는가?
   - `PermissionHelper.kt`가 `ui/` 하위에 생성됐는가?
   - `POST_NOTIFICATIONS`는 런타임 요청, `SCHEDULE_EXACT_ALARM`은 설정 화면 안내로 처리했는가?
   - 권한 거부 시 앱이 정상 동작하는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/1-phase1-completion/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- 권한 거부 시 앱을 종료(`finish()`)하지 마라. 이유: 알람 없이도 캘린더·투두·가계부 기능은 동작해야 한다.
- 라이트 모드 색상을 사용하지 마라. 이유: 이 앱은 다크 미니멀 테마만 지원한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
