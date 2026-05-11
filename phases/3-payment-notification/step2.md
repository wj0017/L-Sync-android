# Step 2: notification-permission

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `app/src/main/java/com/lsync/app/ui/PermissionHelper.kt` (기존 패턴 확인)
- `app/src/main/java/com/lsync/app/MainActivity.kt` (기존 권한 요청 플로우 확인)
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt` (색상 토큰 확인)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. PermissionHelper 확장

`app/src/main/java/com/lsync/app/ui/PermissionHelper.kt`에 두 함수를 추가하라. 기존 함수는 수정하지 마라.

```kotlin
fun needsNotificationListenerPermission(context: Context): Boolean

fun openNotificationListenerSettings(context: Context)
```

**needsNotificationListenerPermission:**
```kotlin
return !NotificationManagerCompat
    .getEnabledListenerPackages(context)
    .contains(context.packageName)
```

**openNotificationListenerSettings:**
```kotlin
context.startActivity(
    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)
```

### 2. MainActivity 확장

`MainActivity.kt`의 기존 `LaunchedEffect(Unit)` 권한 체크 블록에 세 번째 조건을 추가하라.

**추가할 상태 변수** (기존 `showExactAlarmDialog`와 동일한 위치):
```kotlin
var showNotificationListenerDialog by remember { mutableStateOf(false) }
```

**LaunchedEffect(Unit) 안에 추가:**
```kotlin
if (PermissionHelper.needsNotificationListenerPermission(this@MainActivity)) {
    showNotificationListenerDialog = true
}
```

**다이얼로그 추가** (기존 `showExactAlarmDialog` AlertDialog 블록 아래에 추가):
```kotlin
if (showNotificationListenerDialog) {
    AlertDialog(
        onDismissRequest = { showNotificationListenerDialog = false },
        containerColor = BgCard,
        title = {
            Text("결제 알림 자동 등록", color = FgPrimary)
        },
        text = {
            Text(
                "카드·뱅킹 앱 결제 알림을 읽어 가계부에 자동으로 추가합니다.\n설정 > 알림 접근에서 L-Sync를 허용해주세요.",
                color = FgPrimary,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showNotificationListenerDialog = false
                PermissionHelper.openNotificationListenerSettings(this@MainActivity)
            }) {
                Text("설정으로 이동", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = { showNotificationListenerDialog = false }) {
                Text("나중에", color = FgPrimary)
            }
        },
    )
}
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `PermissionHelper`에 두 함수가 추가됐는가?
   - `needsNotificationListenerPermission`이 `NotificationManagerCompat.getEnabledListenerPackages()`를 사용하는가?
   - `MainActivity`의 기존 권한 다이얼로그(`showExactAlarmDialog`)가 변경되지 않았는가?
   - 새 다이얼로그가 `BgCard`, `FgPrimary`, `AccentBlue` 색상을 사용하는가?
   - 다이얼로그 "나중에" 버튼이 앱을 강제 종료하지 않는가?
   - `collectAsState()`를 사용하고 `collectAsStateWithLifecycle`이 없는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/3-payment-notification/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- 다이얼로그 거부 시 `finish()`를 호출하지 마라. 이유: 결제 자동 등록은 선택적 편의 기능이며, 권한 없이도 앱의 모든 기능이 동작해야 한다.
- 기존 `needsNotificationPermission`, `needsExactAlarmPermission` 함수를 수정하지 마라. 이유: 이 step은 추가만 담당한다.
- 라이트 모드 색상을 사용하지 마라. 이유: 이 앱은 다크 미니멀 테마만 지원한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
