# Step 1: notification-tap

## 배경

이 task(`14-notification-deeplink`)는 "알림·위젯 탭 → 해당 화면 딥링크 + 알림 완료 액션"이다.

**이전 Step(0)에서 만든 것:**
- `MainActivity`가 `singleTop`이며, 인텐트의 `nav_target` extra(상수 `MainActivity.EXTRA_NAV_TARGET = "nav_target"`)를 읽어 `navTarget` 상태로 보존한다(`onCreate` + `onNewIntent`).
- `NavGraph(authViewModel, navTarget, onTargetConsumed)`가 로그인 후 `LaunchedEffect(isSignedIn, navTarget)`에서 해당 탭(`home`/`schedule`/`finance`/`bible`)으로 이동한다.
- 즉, **`MainActivity`를 `nav_target` extra와 함께 띄우면 그 탭으로 열린다**(adb로 검증됨).

이 Step은 그 메커니즘을 **알림 탭**에 연결한다. 현재 `AlarmReceiver`가 만든 알림은 `contentIntent`가 없어 탭해도 무반응이다. 일정·할 일 알림을 탭하면 **일정(`schedule`) 탭**으로 열리게 한다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — `notification/` 항목, 알람 라이프사이클.
- `app/src/main/java/com/lsync/app/notification/AlarmReceiver.kt` — **수정 대상.** 현재 `NotificationCompat.Builder`로 알림을 만들고 `manager.notify(id.hashCode(), notification)` 한다. `EXTRA_ID/EXTRA_TITLE/EXTRA_TYPE`, `TYPE_TODO`/`TYPE_EVENT` 사용.
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — `companion object`의 상수들(`EXTRA_ID` 등), `id.hashCode()`를 **broadcast PendingIntent의 requestCode로 이미 사용 중**임을 확인하라. (충돌 주의 — 아래 핵심 규칙)
- `app/src/main/java/com/lsync/app/MainActivity.kt` — Step 0에서 추가된 `EXTRA_NAV_TARGET` 상수. 딥링크 진입점.

## 작업

`AlarmReceiver.onReceive`에서 알림을 만들 때 `contentIntent`(탭 시 동작)를 추가한다.

- `Intent(context, MainActivity::class.java)`를 만들고:
  - `putExtra(MainActivity.EXTRA_NAV_TARGET, "schedule")` — 일정/할 일 알림 모두 일정 탭으로.
  - `flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP` (또는 `SINGLE_TOP`) — 기존 인스턴스 재사용(Step 0의 singleTop과 정합).
  - **고유 `data` Uri 부여:** `data = Uri.parse("lsync://nav/schedule/$id")`. 이유: `Intent.filterEquals()`는 extra를 비교에 포함하지 않는다. 항목마다 다른 PendingIntent가 되도록 `data`를 고유화하지 않으면 `FLAG_UPDATE_CURRENT`로 같은 PendingIntent에 묶여 extra가 덮어써질 수 있다.
- `PendingIntent.getActivity(context, <requestCode>, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`로 감싼다.
  - **requestCode는 `id.hashCode()`를 그대로 쓰지 마라.** 이유: `AlarmScheduler`가 알람 broadcast에 `id.hashCode()`를 이미 사용한다. getActivity와 getBroadcast는 타입이 달라 직접 충돌하진 않지만, Step 3에서 같은 알림에 완료 액션(getBroadcast)이 추가되므로 **requestCode 네임스페이스를 분리**해 둔다. 예: contentIntent는 `id.hashCode()`(고정 슬롯), 완료 액션은 Step 3에서 `id.hashCode() + 1`. 본 Step에서는 contentIntent에 `id.hashCode()`를 쓰되, `data` Uri 고유화로 항목 간 구분을 보장한다.
- 빌더에 `.setContentIntent(pendingIntent)`를 추가한다. 기존 `.setAutoCancel(true)`는 유지(탭 시 알림 사라짐).
- 기존 알림 생성 로직(채널, 아이콘, 제목/본문, `manager.notify(id.hashCode(), ...)`)은 그대로 둔다.

## 핵심 규칙 (반드시 지킬 것)

- **`contentIntent`의 인텐트에 고유 `data` Uri를 부여하라**(`lsync://nav/schedule/$id`). 이유: extra는 `filterEquals`에서 무시되므로, 고유 data 없이는 여러 항목의 PendingIntent가 하나로 합쳐져 잘못된 동작을 한다.
- **`nav_target` 값은 반드시 `"schedule"`.** 이유: Step 0이 인식하는 유효 탭 route. 오타(`"Schedule"` 등)면 이동이 안 된다.
- **requestCode 네임스페이스 인지:** Step 3의 완료 액션이 `id.hashCode() + 1`을 쓸 것이므로 contentIntent는 `id.hashCode()`를 유지하고 그 외 오프셋을 침범하지 마라.
- **알림 채널·notify ID(`id.hashCode()`)·제목/본문 로직을 바꾸지 마라.** 이유: Phase 8 알람 라이프사이클과 정합. 이 Step은 탭 동작만 추가.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 알림에 `contentIntent`가 붙고, 인텐트에 `EXTRA_NAV_TARGET="schedule"`와 고유 `data` Uri가 있는가?
   - PendingIntent에 `FLAG_IMMUTABLE`이 있는가?(Android 12+ 필수)
   - 기존 알림 생성/notify 로직이 보존됐는가?
3. 결과에 따라 `phases/14-notification-deeplink/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "AlarmReceiver 알림에 contentIntent 추가 — MainActivity로 EXTRA_NAV_TARGET=schedule + 고유 data Uri(lsync://nav/schedule/{id})로 딥링크. requestCode=id.hashCode()(완료 액션용 +1 슬롯 예약). FLAG_IMMUTABLE 사용"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- `AlarmScheduler`의 알람 등록 로직/requestCode(`id.hashCode()` broadcast)를 바꾸지 마라. 이유: 알람 발화 자체가 깨진다(Phase 8 범위).
- `contentIntent`에 고유 `data` 없이 extra만으로 항목을 구분하려 하지 마라. 이유: `filterEquals`가 extra를 무시해 PendingIntent가 합쳐진다.
- 완료 액션 버튼을 여기서 추가하지 마라. 이유: Step 3의 범위(별도 리시버·금액 분기 필요).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
