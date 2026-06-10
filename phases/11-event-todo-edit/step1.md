# Step 1: viewmodel-update

## 배경

이 task(`11-event-todo-edit`)는 "일정·할 일 수정(edit)"이다.

**이전 Step(Step 0)에서** `EventRepository.update(id, title, isAllDay, startDate, rrule, hasAlarm)`와 `TodoRepository.update(id, title, dueDate, financeIsLinked, financeType, financeCategory, financeAmount)`를 추가했다(불변 필드·완료/연동 보존, 알람 재등록 포함).

이 Step은 `ScheduleViewModel`에 그 update를 호출하는 함수를 추가한다(UI가 쓸 API).

## 읽어야 할 파일

먼저 아래 파일을 읽고 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — `collectAsState`, 위젯 즉시 갱신(`WidgetRefreshHelper`)
- `CLAUDE.md` — `@HiltViewModel`+`@Inject`, userId
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — **수정 대상.** 기존 `createEvent`/`createTodo`/`deleteEvent`/`deleteTodo` 패턴과 `widgetRefreshHelper.requestUpdate()` 호출 위치를 그대로 따른다.
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — Step 0의 `update` 시그니처
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — Step 0의 `update` 시그니처

## 작업

`ScheduleViewModel`에 함수 2개를 추가한다(기존 create 함수의 `viewModelScope.launch { runCatching { ... }.onSuccess/onFailure }` 패턴을 그대로 따른다).

```kotlin
fun updateEvent(
    id: String,
    title: String,
    isAllDay: Boolean,
    startDate: String,
    rrule: String? = null,
    hasAlarm: Boolean = false,
)

fun updateTodo(
    id: String,
    title: String,
    dueDate: String?,
    financeIsLinked: Boolean = false,
    financeType: String? = null,
    financeCategory: String? = null,
    financeAmount: Long? = null,
)
```

- 각각 `eventRepository.update(...)` / `todoRepository.update(...)` 호출.
- 성공 시 `widgetRefreshHelper.requestUpdate()`(기존 create/delete와 동일).
- 실패 시 `_uiState.update { it.copy(error = e.message) }`.
- 로딩 표시가 필요하면 `createEvent`와 동일한 `isLoading` 패턴을 따르되, 과하게 만들지 마라.

## 핵심 규칙 (반드시 지킬 것)

- **`@HiltViewModel`+`@Inject` 유지.** 새 의존성 주입 불필요(이미 `eventRepository`, `todoRepository`, `widgetRefreshHelper` 보유).
- **`collectAsStateWithLifecycle` 금지.** (이 Step은 함수 추가라 직접 관련은 적지만 규칙 유지.)
- update 후 별도 재조회를 하지 마라. Room Flow 구독(`observeDayItems` 등)이 자동 재방출하므로 UI가 갱신된다.
- userId는 `currentUserId`만. update는 id로 동작하므로 userId를 새로 만들지 마라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `updateEvent`/`updateTodo`가 Repository update를 호출하고 성공 시 위젯 갱신하는가?
   - 기존 create/delete 패턴(에러 처리·widgetRefresh)을 따르는가?
   - update 후 수동 재조회 없이 Flow에 의존하는가?
3. 결과에 따라 `phases/11-event-todo-edit/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ScheduleViewModel.updateEvent/updateTodo 추가 — Repository update 호출 + 위젯 갱신. UI(step2)가 편집 다이얼로그에서 호출"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- Compose(`ScheduleScreen.kt`)를 수정하지 마라. 이유: UI는 Step 2의 범위다.
- Repository를 수정하지 마라. 이유: Step 0에서 완성됐다.
- 기존 `createEvent`/`createTodo`/`toggleComplete`/`delete*` 동작을 바꾸지 마라. 이 step은 추가만 한다.
