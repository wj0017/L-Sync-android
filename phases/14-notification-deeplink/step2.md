# Step 2: widget-deeplink

## 배경

이 task(`14-notification-deeplink`)는 "알림·위젯 탭 → 해당 화면 딥링크 + 알림 완료 액션"이다.

**이전 Step에서 만든 것:**
- (Step 0) `MainActivity`(`singleTop`)가 `nav_target` extra(상수 `MainActivity.EXTRA_NAV_TARGET = "nav_target"`)를 읽어 해당 탭(`home`/`schedule`/`finance`/`bible`)으로 이동한다. `onCreate`+`onNewIntent` 처리됨.
- (Step 1) 알림 탭이 `MainActivity`로 `EXTRA_NAV_TARGET="schedule"` + 고유 `data` Uri로 딥링크하는 패턴이 확립됨.

이 Step은 **홈 위젯 카드 탭**을 같은 메커니즘에 연결한다. 현재 `LSyncWidget`의 카드들은 클릭이 없어 탭해도 무반응이다. 각 카드를 탭하면 해당 탭으로 앱이 열리게 한다:

- 할 일 카드(`TodoCard`) → `schedule`
- 일정 카드(`EventCard`) → `schedule`
- 가계부 카드(`FinanceCard`) → `finance`
- 통독 카드(`ReadingCard`) → `bible`

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — `ui/widget/` 항목, 위젯이 Room 전용(Firestore 미사용)인 점.
- `docs/TechSpec.md` 6장 — 위젯 스펙(Glance 1.1.0).
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — **수정 대상.** `WidgetContent`가 `TodoCard`/`EventCard`/`FinanceCard`/`ReadingCard`를 배치한다. **각 카드 Composable은 이미 `modifier: GlanceModifier` 파라미터를 받는다**(클릭 부착 가능). `provideGlance`는 `suspend`.
- `app/src/main/java/com/lsync/app/MainActivity.kt` — `EXTRA_NAV_TARGET` 상수.

## 작업

Glance의 `actionStartActivity`로 각 카드 modifier에 클릭을 부착한다.

- import: `androidx.glance.appwidget.action.actionStartActivity`, `androidx.glance.GlanceModifier.clickable`(`androidx.glance.action.clickable`).
- 각 카드에 전달하는 `modifier`에 `.clickable(actionStartActivity(intent))`를 체이닝한다. `intent`는 다음과 같이 만든다:
  ```kotlin
  fun navIntent(context: Context, target: String) = Intent(context, MainActivity::class.java).apply {
      putExtra(MainActivity.EXTRA_NAV_TARGET, target)
      data = Uri.parse("lsync://nav/$target")            // 고유화 필수 (아래 규칙 참조)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
  }
  ```
  - `WidgetContent`/카드에서 `context`가 필요하면 `LocalContext.current`(`androidx.glance.LocalContext`)로 얻거나, `provideContent` 시점에 만들어 내려보낸다.
  - 카드별 target: TodoCard·EventCard → `"schedule"`, FinanceCard → `"finance"`, ReadingCard → `"bible"`.
- 기존 카드 UI(레이아웃, 색상 토큰, 막대 차트, 통독 목록)는 그대로 둔다. modifier에 클릭만 얹는다.

## 핵심 규칙 (반드시 지킬 것)

- **각 인텐트에 고유 `data` Uri(`lsync://nav/$target`)를 부여하라.** 이유: `Intent.filterEquals()`는 extra를 무시한다. 고유 data 없이 4개 카드가 같은 `Intent(ctx, MainActivity)` + extra만 다르게 만들면, Glance가 감싸는 PendingIntent가 **하나로 합쳐져 모든 카드가 같은 화면**을 연다. target별로 data가 달라야 4개가 구분된다.
- **`nav_target` 값은 Step 0 유효값과 정확히 일치**: `schedule`/`finance`/`bible`. 오타 금지.
- **위젯은 Room 전용.** 클릭 부착 과정에서 Firestore 구독/네트워크 호출을 추가하지 마라. (ARCHITECTURE)
- **카드 내용/레이아웃을 바꾸지 마라.** modifier에 `clickable`만 추가한다. 이유: 이 Step은 딥링크만.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 4개 카드에 `actionStartActivity` 클릭이 붙었는가?
   - 각 인텐트의 `data` Uri가 target별로 고유한가?(`lsync://nav/schedule|finance|bible`)
   - `nav_target` 값이 Step 0 유효 route와 일치하는가?
   - 위젯이 여전히 Room 전용인가?(Firestore 미추가)
3. 결과에 따라 `phases/14-notification-deeplink/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "LSyncWidget 4개 카드에 actionStartActivity 딥링크 추가 — Todo/Event→schedule, Finance→finance, Reading→bible. 각 인텐트 고유 data(lsync://nav/{target})로 filterEquals 충돌 방지. EXTRA_NAV_TARGET 사용"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- 고유 `data` Uri 없이 extra만으로 카드를 구분하려 하지 마라. 이유: Glance/PendingIntent가 `filterEquals`로 합쳐 모든 카드가 같은 화면을 연다.
- 카드별 항목 단위 딥링크(특정 할 일/거래로 이동)를 구현하지 마라. 이유: 탭 레벨만이 이번 범위(Step 0 결정).
- 위젯 데이터 로딩(`loadWidgetState`)이나 갱신 로직을 바꾸지 마라. 이유: 이 Step은 클릭만 추가.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
