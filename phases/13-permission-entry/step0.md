# Step 0: permission-entry

## 배경

이 task(`13-permission-entry`)는 "설정 — 권한 진입 추가(경량)"이다.

점검 결과, 별도 Settings 화면을 새로 만들 필요가 없다:
- **통독 계획 설정**은 이미 존재한다(`ReadingPlanRepository.getSettings/saveSettings` + HomeScreen 헤더 메뉴의 설정 다이얼로그).
- **로그아웃**도 HomeScreen 헤더의 `DropdownMenu`에 이미 있다.

비어 있는 것은 **알림/정확 알람 권한을 앱 안에서 다시 열어줄 진입점**뿐이다. 권한이 거부되면 알람·알림이 조용히 동작하지 않는데(Phase 8의 inexact 폴백이 있어도 알림 자체가 막히면 무용), 사용자가 이를 인지하고 권한 화면으로 갈 길이 없다. 이 Step은 HomeScreen 메뉴에 그 진입을 추가한다.

`PermissionHelper`(이미 존재)가 상태 판별·설정 화면 열기를 제공한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 기존 패턴을 파악하라:

- `docs/UI_GUIDE.md` — 다크 미니멀, Pretendard, 금지사항
- `docs/ARCHITECTURE.md`, `CLAUDE.md` — 디자인 토큰
- `app/src/main/java/com/lsync/app/ui/PermissionHelper.kt` — `needsNotificationPermission`, `needsExactAlarmPermission`, `openExactAlarmSettings`, `needsNotificationListenerPermission`, `openNotificationListenerSettings` (있는 함수 그대로 사용)
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — **수정 대상.** 기존 헤더 `DropdownMenu`(`menuExpanded`)와 로그아웃 항목, 통독 설정 다이얼로그 호출 패턴. `LocalContext.current` 사용 위치 확인.
- `app/src/main/java/com/lsync/app/MainActivity.kt` — 앱 최초 진입 시 권한 요청을 어떻게 하는지(중복/일관성 참고)

## 작업

HomeScreen 헤더의 기존 `DropdownMenu`(또는 설정 다이얼로그)에 **권한 진입 항목**을 추가한다.

- `val context = LocalContext.current` 사용.
- 항목 1 — **알림 권한:** `PermissionHelper.needsNotificationPermission(context)`가 true일 때(또는 항상) 표시. 탭하면 시스템 앱 알림 설정으로 이동.
  - `PermissionHelper`에 알림 설정 열기 함수가 없으면, 같은 파일에 `openAppNotificationSettings(context)`를 추가하라(`Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)`; 구버전 폴백은 앱 상세 설정 `ACTION_APPLICATION_DETAILS_SETTINGS`). 이 추가는 권한 모듈 범위 내이므로 허용된다.
- 항목 2 — **정확 알람 권한:** `PermissionHelper.needsExactAlarmPermission(context)`가 true일 때 표시(Android 12+). 탭하면 `PermissionHelper.openExactAlarmSettings(context)`.
- (선택) 항목 3 — **결제 알림 접근:** `needsNotificationListenerPermission`/`openNotificationListenerSettings`가 이미 있으니, 자연스러우면 같은 메뉴에 노출. 과하면 생략 가능.
- 권한이 이미 허용된 항목은 숨기거나 "허용됨" 상태로 흐리게 표시(재량). 메뉴가 길어지면 통독 설정 다이얼로그 안의 한 섹션으로 묶어도 된다.

UI는 기존 메뉴 항목/다이얼로그 스타일(Pretendard, 다크 토큰)을 그대로 따른다.

## 핵심 규칙 (반드시 지킬 것)

- **새 화면/네비 라우트를 만들지 마라.** 이유: 이 task는 "경량 권한 진입"으로 확정됐다. 기존 HomeScreen 메뉴/다이얼로그에 항목만 추가한다.
- **기존 통독 설정·로그아웃을 옮기거나 바꾸지 마라.** 이미 동작하므로 그대로 둔다(중복 구현 금지).
- **권한 로직은 `PermissionHelper`에만.** 권한 판별/설정 열기 코드를 HomeScreen에 흩뿌리지 말고 `PermissionHelper` 함수를 호출하라(없으면 거기 추가).
- 디자인 토큰 준수(Pretendard, 다크, 그림자 금지). 새 색상 만들지 마라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처/디자인 체크리스트:
   - HomeScreen 메뉴에 알림·정확 알람 권한 진입이 추가됐는가?
   - 권한 판별/열기가 `PermissionHelper`를 통하는가?
   - 새 네비 라우트/화면 없이 기존 메뉴에 얹었는가?
   - 기존 통독 설정·로그아웃이 그대로인가? 디자인 토큰 준수?
3. 결과에 따라 `phases/13-permission-entry/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "HomeScreen 메뉴에 알림·정확알람 권한 재진입 추가(PermissionHelper 사용, 필요 시 openAppNotificationSettings 추가). 신규 화면 없이 경량 처리"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- 새 Settings 화면이나 네비게이션 목적지를 만들지 마라. 이유: task 범위가 경량 권한 진입으로 확정됐다.
- 통독 계획 설정/로그아웃을 재구현하거나 이동하지 마라. 이미 존재한다.
- `MainActivity`의 최초 권한 요청 흐름을 바꾸지 마라(이 Step은 사후 재진입만 추가).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
