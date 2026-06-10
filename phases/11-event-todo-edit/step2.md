# Step 2: compose-edit-ui

## 배경

이 task(`11-event-todo-edit`)는 "일정·할 일 수정(edit)"이다.

**이전 Step들에서:**
- **Step 0**: `EventRepository.update`, `TodoRepository.update`(보존 규칙·알람 재등록 포함).
- **Step 1**: `ScheduleViewModel.updateEvent(id, title, isAllDay, startDate, rrule, hasAlarm)`, `updateTodo(id, title, dueDate, financeIsLinked, financeType, financeCategory, financeAmount)`.

이 Step은 화면에서 **편집 진입과 편집 다이얼로그**를 붙인다.

**핵심 제약(점검 결과):** `TodoItemCard`는 이미 **탭 = 완료 토글**(`.clickable { onToggle() }`)로 점유돼 있다. 따라서 편집 진입은 **롱프레스**(`combinedClickable`의 `onLongClick`)로 잡아야 탭 토글과 충돌하지 않는다. `EventCard`는 탭이 비어 있으나, 일관성을 위해 **롱프레스로 통일**한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 기존 패턴을 파악하라:

- `docs/UI_GUIDE.md` — 다크 미니멀, Pretendard, ghost chip, 금지사항
- `docs/ARCHITECTURE.md` — `collectAsState()`만
- `CLAUDE.md` — 디자인 토큰, `collectAsStateWithLifecycle` 금지
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — **수정 대상.** 특히:
  - `EventCard(event, onDelete)` (라인 ~301), `TodoItemCard(todo, onToggle, onDelete)` (라인 ~341)
  - 이들이 호출되는 곳(`onDelete = { viewModel.deleteEvent(...) }` 등, 라인 ~151)
  - `CreateEventDialog`(라인 ~463), `CreateTodoDialog`(라인 ~493) — **편집 다이얼로그의 본보기/재사용 대상**
  - 공용 프리미티브 `LSyncInputDialog`, `LSyncField`
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — `updateEvent`/`updateTodo` 시그니처

## 작업

### 1) 편집 진입(롱프레스)

- `EventCard`/`TodoItemCard`에 `onEdit: () -> Unit` 파라미터를 추가하고, 카드 루트 modifier를 `combinedClickable(onClick = ..., onLongClick = onEdit)`로 바꾼다.
  - `TodoItemCard`: `onClick`은 기존 `onToggle` 유지, `onLongClick = onEdit`.
  - `EventCard`: `onClick`은 빈 동작(또는 생략 불가하면 no-op), `onLongClick = onEdit`.
- 호출부(라인 ~151 부근)에서 `onEdit = { editingEvent = item.entity }` / `onEdit = { editingTodo = item.entity }` 형태로 편집 대상 상태를 채운다.
- 상태 추가: `var editingEvent by remember { mutableStateOf<EventEntity?>(null) }`, `var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }`.

### 2) 편집 다이얼로그(생성 다이얼로그 재사용)

- `CreateEventDialog`/`CreateTodoDialog`에 **초기값 파라미터**를 추가해 편집에 재사용한다. 예: `CreateTodoDialog(initial: TodoEntity? = null, ...)`로 만들고, `remember { mutableStateOf(initial?.title ?: "") }`처럼 초기값을 채운다. 제목 라벨도 초기값 유무로 "새 할 일"/"할 일 수정"으로 바꾼다.
  - 또는 별도 `EditEventDialog`/`EditTodoDialog`를 만들어도 되나, **생성과 편집의 입력 폼이 동일하므로 재사용을 권장**(중복 최소화).
- 편집 확인 시 `viewModel.updateEvent(id = editingEvent!!.id, ...)` / `viewModel.updateTodo(id = editingTodo!!.id, ...)` 호출 후 상태를 `null`로(다이얼로그 닫기).
- `editingEvent != null` / `editingTodo != null` 일 때 편집 다이얼로그를 렌더한다.

## 핵심 규칙 (반드시 지킬 것)

- **탭 토글 보존.** `TodoItemCard`의 탭은 반드시 완료 토글로 유지하라. 편집은 롱프레스다. 이유: 탭을 편집으로 바꾸면 기존 완료 토글 UX가 깨진다.
- **디자인 시스템 준수.** Pretendard, ghost chip(active=`FgPrimary` solid), 그림자 금지, 지출 금액 `AccentRed` 금지, 기존 다이얼로그 토큰/간격 유지.
- **`collectAsState()`만.**
- **Todo 편집은 완료/연동을 건드리지 않는다.** 편집 폼에 완료 체크나 가계부 연결 해제 같은 동작을 추가하지 마라. `updateTodo`는 편집 필드만 넘긴다(Step 0이 나머지를 보존).
- 편집 다이얼로그가 `dueDate`/`startDate` 등 기존 값을 그대로 보여주고, 비운 채 저장해 데이터를 날리지 않게 하라(생성 다이얼로그의 검증 로직 재사용).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처/디자인 체크리스트:
   - 롱프레스로 편집 진입, 탭은 여전히 Todo 완료 토글인가?
   - 편집 다이얼로그가 기존 값으로 prefill되고 `updateEvent`/`updateTodo`를 호출하는가?
   - 생성 다이얼로그를 재사용(또는 동일 폼)했는가? 디자인 토큰 준수?
   - `collectAsState()` 사용?
3. 결과에 따라 `phases/11-event-todo-edit/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ScheduleScreen: 카드 롱프레스로 편집 진입(탭 토글 유지), 생성 다이얼로그 prefill 재사용으로 일정·할일 편집. updateEvent/updateTodo 연결. edit 기능 end-to-end 완성"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- ViewModel/Repository를 수정하지 마라. 이유: Step 0·1에서 완성됐다.
- `TodoItemCard`의 탭(onToggle)을 편집으로 바꾸지 마라. 이유: 완료 토글 UX 유지.
- 새 색상/토큰을 만들지 마라. 기존 `Color.kt`만 사용.
- 기존 코드를 광범위하게 리팩토링하지 마라. 편집 진입·다이얼로그 추가 범위만 작업하라.
