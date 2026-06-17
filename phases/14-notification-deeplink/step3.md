# Step 3: notification-action

## 배경

이 task(`14-notification-deeplink`)는 "알림·위젯 탭 → 해당 화면 딥링크 + 알림 완료 액션"이다.

**이전 Step에서 만든 것:**
- (Step 0) `MainActivity`(`singleTop`)가 `nav_target` extra(`MainActivity.EXTRA_NAV_TARGET`)를 읽어 탭 이동. 유효값 `home`/`schedule`/`finance`/`bible`.
- (Step 1) `AlarmReceiver` 알림에 `contentIntent`(탭 → 일정 탭) 추가. requestCode는 `id.hashCode()` 사용, **완료 액션용 `id.hashCode() + 1` 슬롯을 예약**해 둠.
- (Step 2) 위젯 카드 딥링크.

이 Step은 **할 일(Todo) 알림에 "완료" 액션 버튼**을 추가한다. 버튼을 누르면 앱을 열지 않고 Todo를 완료 처리하고 알림을 지운다. 단, **가계부 연동 + 금액 미정** Todo는 헤드리스로 완료하면 안 되므로(아래 핵심 규칙) 앱으로 유도한다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — Todo↔Finance 연동, 알람 라이프사이클. `notification/`, `worker/` 항목.
- `CLAUDE.md` — CRITICAL: Todo 완료 시 Finance 자동 생성(금액 미정 시 UI 팝업 강제), `@HiltViewModel`/DI 규칙, Crashlytics `sync_failed`.
- `docs/PRD.md` 2.2 — Todo↔가계부 생명주기. "금액 미정 시 UI 팝업 강제".
- `app/src/main/java/com/lsync/app/notification/AlarmReceiver.kt` — **수정 대상.** Step 1에서 contentIntent가 추가된 상태. `TYPE_TODO`/`TYPE_EVENT` 분기, `EXTRA_ID`, `manager.notify(id.hashCode(), ...)`.
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — `companion object` 상수(`EXTRA_ID`, `TYPE_TODO`), `R.drawable.ic_notification`.
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — **핵심.** `suspend fun complete(todo: TodoEntity, amount: Long? = null)`. **주의: 가계부 연동 Todo인데 `financeAmount`가 null이면 amount 없이 complete 시 ₩0짜리 Finance가 생성된다**(`completedTodo.financeAmount ?: 0L`). `uncheck`/`delete`도 참고.
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — `suspend fun getById(id: String): TodoEntity?` (deletedAt IS NULL 필터).
- `app/src/main/java/com/lsync/app/ui/widget/WidgetRefreshHelper.kt` — `@Singleton`, `fun requestUpdate()`. 데이터 변경 후 위젯 즉시 갱신.
- `app/src/main/java/com/lsync/app/MainActivity.kt` — `EXTRA_NAV_TARGET`(앱 유도용 딥링크).
- `app/src/main/AndroidManifest.xml` — **수정 대상.** 새 리시버 등록.
- 기존 `@AndroidEntryPoint` 사용 패턴 참고: `app/src/main/java/com/lsync/app/notification/PaymentNotificationService.kt`(또는 다른 Hilt 주입 컴포넌트)에서 `@Inject`/EntryPoint 패턴.

## 작업

### 1) 새 리시버 `TodoActionReceiver` (`notification/TodoActionReceiver.kt`)

```kotlin
@AndroidEntryPoint
class TodoActionReceiver : BroadcastReceiver() {
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var todoDao: TodoDao
    @Inject lateinit var widgetRefreshHelper: WidgetRefreshHelper
    // @ApplicationScope CoroutineScope 주입 권장 (di/Qualifiers.kt 참고)

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
        val pending = goAsync()                       // suspend 작업 동안 프로세스 유지
        scope.launch {
            try {
                val todo = todoDao.getById(todoId)
                // 가드: 없거나 이미 완료면 no-op
                if (todo == null || todo.isCompleted) return@launch
                if (todo.financeIsLinked && todo.financeAmount == null) {
                    // 헤드리스 완료 금지 → 앱으로 유도 (금액 입력 필요)
                    context.startActivity(navIntentToSchedule(context))   // EXTRA_NAV_TARGET=schedule, FLAG_ACTIVITY_NEW_TASK
                } else {
                    todoRepository.complete(todo)      // amount=null이지만 financeAmount 사전지정 or 미연동만 도달
                    NotificationManagerCompat.from(context).cancel(todoId.hashCode())
                    widgetRefreshHelper.requestUpdate()
                }
            } finally {
                pending.finish()
            }
        }
    }
    companion object { const val EXTRA_TODO_ID = "todo_action_id" }
}
```

- `goAsync()` + 코루틴은 **필수**. 이유: `onReceive`는 메인스레드 단명 호출이고 `complete()`는 Room+Firestore suspend 작업이다. `finish()`를 `finally`에서 호출하라.
- 코루틴 스코프는 `@ApplicationScope`(`di/Qualifiers.kt`) 주입을 권장. 없으면 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`를 리시버 내부에 둬도 되나, 주입이 깔끔하다.

### 2) `AlarmReceiver` — TYPE_TODO에만 완료 액션 추가

- `type == TYPE_TODO`일 때만 알림 빌더에 액션을 추가한다:
  - `Intent(context, TodoActionReceiver::class.java).putExtra(TodoActionReceiver.EXTRA_TODO_ID, id)`, 고유 `data = Uri.parse("lsync://complete/$id")`.
  - `PendingIntent.getBroadcast(context, id.hashCode() + 1, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)` — **requestCode는 `id.hashCode() + 1`**(Step 1 contentIntent의 `id.hashCode()`와 분리).
  - `builder.addAction(R.drawable.ic_notification, "완료", pendingIntent)`.
- `TYPE_EVENT` 알림에는 완료 액션을 붙이지 마라.

### 3) Manifest

`TodoActionReceiver`를 등록한다:
```xml
<receiver android:name=".notification.TodoActionReceiver" android:exported="false" />
```

## 핵심 규칙 (반드시 지킬 것)

- **가계부 연동 + `financeAmount == null` Todo는 알림에서 `complete()`를 호출하지 마라.** 이유: `complete()`가 ₩0짜리 Finance를 생성해 PRD 2.2 "금액 미정 시 UI 팝업 강제"를 위반한다. 대신 `MainActivity`로 `EXTRA_NAV_TARGET="schedule"` 딥링크해 사용자가 금액을 입력하도록 유도한다.
- **중복 완료 가드:** `getById` 결과가 `null`이거나 `isCompleted == true`면 즉시 no-op. 이유: 액션 2회 탭/앱에서 이미 완료한 경우 중복 Finance 생성 방지.
- **`goAsync()` + `finally { finish() }` 필수.** 이유: suspend 작업 중 프로세스가 죽으면 완료가 유실된다.
- **완료 액션 requestCode = `id.hashCode() + 1`.** 이유: Step 1 contentIntent(`id.hashCode()`)와 같은 슬롯이면 `FLAG_UPDATE_CURRENT`로 서로 덮어쓴다. 인텐트 `data`도 `lsync://complete/$id`로 고유화하라.
- **완료 후 알림 명시적 취소(`cancel(id.hashCode())`) + 위젯 갱신(`requestUpdate()`).** 이유: `setAutoCancel`은 액션 버튼엔 적용되지 않는다. notify ID는 `AlarmReceiver`가 쓴 `id.hashCode()`와 동일해야 한다.
- **리시버는 `@AndroidEntryPoint`로 의존성 주입.** `@HiltViewModel`이 아닌 BroadcastReceiver 주입 패턴. DI 누락 시 런타임 크래시(CLAUDE.md).
- **완료는 반드시 `TodoRepository.complete()`를 통하라.** `todoDao.upsert`로 직접 완료 처리하지 마라. 이유: Finance 생성·Firestore Batch·알람 취소가 묶여 있어 직접 처리 시 데이터 무결성이 깨진다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `TodoActionReceiver`가 `@AndroidEntryPoint`이고 Manifest에 `exported="false"`로 등록됐는가?
   - 가계부 연동+금액 미정 Todo는 `complete()`를 호출하지 않고 앱으로 유도하는가?
   - 중복 완료 가드(null/이미 완료 시 no-op)가 있는가?
   - `goAsync()`/`finish()`가 `finally`에 있는가?
   - 완료 액션 requestCode가 `id.hashCode()+1`, data가 `lsync://complete/$id`로 분리됐는가?
   - 완료 후 `cancel(id.hashCode())` + `requestUpdate()` 호출하는가?
   - 완료가 `TodoRepository.complete()`를 통하는가?(직접 DAO 조작 금지)
   - 완료 액션이 `TYPE_TODO`에만 붙는가?
3. 결과에 따라 `phases/14-notification-deeplink/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "Todo 알림에 완료 액션 추가 — @AndroidEntryPoint TodoActionReceiver(goAsync+ApplicationScope)가 getById로 로드 후 complete(). 가계부연동+금액미정은 complete 금지하고 schedule 딥링크로 유도, 중복완료 가드(null/완료 no-op). 완료 후 알림 cancel(id.hashCode())+WidgetRefreshHelper. requestCode id.hashCode()+1, data lsync://complete/{id}. Manifest 등록"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- 가계부 연동+금액 미정 Todo를 알림 액션으로 완료하지 마라. 이유: ₩0 Finance 생성(PRD 2.2 위반). 앱 유도가 정답.
- `todoDao`/`financeDao`를 직접 조작해 완료를 흉내내지 마라. 이유: Finance 생성·Firestore Batch·알람 취소가 `complete()`에 묶여 있다. 직접 처리는 데이터 무결성 파괴.
- 완료 액션 requestCode/`data`를 contentIntent와 겹치게 두지 마라. 이유: PendingIntent가 서로 덮어써 탭/완료가 뒤섞인다.
- `goAsync()` 없이 suspend 작업을 실행하지 마라. 이유: 프로세스 종료 시 완료 유실.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
