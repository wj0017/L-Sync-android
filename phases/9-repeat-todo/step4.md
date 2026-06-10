# Step 4: compose-ui

## 배경

이 task(`9-repeat-todo`)는 "반복 Todo 템플릿 UI"다. 백엔드·Repository·ViewModel이 모두 준비됐고, 이 Step이 마지막으로 화면을 붙인다.

이전 Step들에서:
- **Step 0**: `ui/schedule/RecurrenceOptions.kt` — `enum Frequency{DAILY,WEEKLY,MONTHLY,YEARLY}`, `enum Weekday(token)`, `data class RecurrenceOption(frequency, interval, weekdays)`, `buildRrule(option): String`, `describeRrule(rrule): String`.
- **Step 3**: `ScheduleViewModel`에 `uiState.templates: List<TodoTemplateEntity>`, `createTemplate(title, rrule, financeIsLinked, financeType, financeCategory, financeAmount)`, `deleteTemplate(id)` 추가. 템플릿 생성 직후 즉시 materialize됨.

이 Step은 ① 반복 할 일 생성 다이얼로그(빈도·간격·요일·가계부)와 ② 활성 템플릿 목록/삭제 UI를 추가한다.

## 읽어야 할 파일

먼저 아래 파일을 읽고 **기존 디자인 시스템과 다이얼로그 패턴**을 파악하라:

- `docs/UI_GUIDE.md` — 다크 미니멀, Pretendard, ghost chip 패턴, 금지사항
- `docs/ARCHITECTURE.md` — `collectAsState()`만 사용
- `CLAUDE.md` — 디자인 토큰(BgPrimary/BgCard/FgPrimary/AccentBlue 등), `collectAsStateWithLifecycle` 금지
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — **수정 대상.** 특히:
  - `ExpandableFab`(현재 `onAddEvent`, `onAddTodo` 액션) 과 `SmallFabItem`
  - `showEventDialog`/`showTodoDialog` 상태와 다이얼로그 호출부
  - `CreateTodoDialog`(빌딩 블록: 제목/마감일/가계부 연동 섹션, 칩 패턴) — **반복 다이얼로그의 본보기**
  - 공용 프리미티브 `LSyncInputDialog`, `LSyncField`, ghost chip 스타일(`FgPrimary` solid = active)
- `app/src/main/java/com/lsync/app/ui/schedule/RecurrenceOptions.kt` — `buildRrule`/`describeRrule`/모델
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — `uiState.templates`, `createTemplate`, `deleteTemplate`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `TodoTemplateEntity` 필드(목록 표시용)

## 작업

### 1) FAB에 "반복 할 일" 액션 추가

- `ExpandableFab`에 `onAddRepeat: () -> Unit` 파라미터와 `SmallFabItem`("반복 할 일")을 추가한다. 기존 `onAddEvent`/`onAddTodo`와 동일한 스타일.
- 호출부에 `var showRepeatDialog by remember { mutableStateOf(false) }`를 추가하고 `onAddRepeat = { fabExpanded = false; showRepeatDialog = true }`.

### 2) CreateRepeatTodoDialog (새 Composable, 같은 파일 내 private)

```kotlin
@Composable
private fun CreateRepeatTodoDialog(
    onConfirm: (title: String, rrule: String, financeIsLinked: Boolean, financeType: String?, financeCategory: String?, financeAmount: Long?) -> Unit,
    onDismiss: () -> Unit,
)
```

수집 항목:
- **제목** (`LSyncField`, 비어있으면 confirm 비활성).
- **빈도**: 매일/매주/매월/매년 — ghost chip 4개(active=`FgPrimary` solid). `Frequency` enum에 매핑.
- **간격(INTERVAL)**: "N마다" 숫자 입력(`LSyncField`, 숫자만, 기본 1). 표시는 "매 N일/주/개월/년" 식 라벨이면 좋다(선택).
- **요일(BYDAY)**: **빈도가 매주(WEEKLY)일 때만** 노출. 월~일 7개 토글 칩 멀티선택(`Weekday`). 하나도 안 고르면 BYDAY 생략(매주 동일 요일 반복).
- **가계부 연동**: `CreateTodoDialog`의 가계부 섹션(체크박스 + EXPENSE/INCOME 칩 + 카테고리 + 금액)을 그대로 재사용/모방.

확인 시:
- `RecurrenceOption(frequency, interval, weekdays)` 구성 → `buildRrule(option)`로 rrule 문자열 생성.
- `onConfirm(title, rrule, financeLinked, type?, category?, amount?)` 호출. 호출부에서 `viewModel.createTemplate(...)` 연결 후 `showRepeatDialog = false`.

### 3) 활성 템플릿 목록 + 삭제

- `uiState.templates`(이미 ViewModel이 노출)를 보여주는 UI를 추가한다. 위치는 재량이되, 발견 가능해야 한다. 권장: 반복 다이얼로그 상단 또는 별도 "반복 할 일 관리" 영역에 목록을 표시.
- 각 항목: 템플릿 `title` + `describeRrule(template.rrule)` 요약(예: "2주마다 월,수") + 삭제 버튼.
- 삭제 버튼 → `viewModel.deleteTemplate(template.id)`. 삭제는 비활성화(soft)이며, **이미 생성된 인스턴스는 그대로 남는다**는 점을 UI 문구로 오해 없게(예: 버튼 라벨 "반복 중지").

## 핵심 규칙 (반드시 지킬 것)

- **디자인 시스템 준수(UI_GUIDE).** 라이트 모드 금지, Pretendard 폰트, ghost chip(outline only, active=`FgPrimary` solid), 그림자 금지(테두리는 HairlineWhite), 지출 금액에 `AccentRed` 금지. 기존 `CreateTodoDialog`의 토큰·간격을 그대로 따르라.
- **rrule은 `buildRrule`로만 생성.** 문자열을 손으로 조립하지 마라. 이유: Step 0이 Materializer 호환·결정론을 보장한다.
- **`collectAsState()`만.** `collectAsStateWithLifecycle` 금지.
- **삭제는 deactivate(soft).** UI에서 "영구 삭제" 같은 표현/동작을 만들지 마라(ViewModel이 이미 soft delete). 이미 생성된 인스턴스를 지우는 버튼을 추가하지 마라.
- 빈도가 WEEKLY가 아닐 때 요일 선택 UI를 숨겨라. 이유: BYDAY는 WEEKLY에서만 의미 있다(Step 0 `buildRrule`도 WEEKLY에서만 BYDAY를 넣는다).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처/디자인 체크리스트:
   - FAB에 "반복 할 일"이 추가되고 다이얼로그가 열리는가?
   - 다이얼로그가 빈도/간격/요일(WEEKLY 한정)/가계부를 수집하고 `buildRrule`로 rrule을 만들어 `createTemplate`을 호출하는가?
   - 활성 템플릿 목록이 `uiState.templates` + `describeRrule`로 표시되고 삭제가 `deleteTemplate`인가?
   - `collectAsState()` 사용, ghost chip·Pretendard·다크 토큰 준수, 지출 AccentRed 미사용인가?
3. 결과에 따라 `phases/9-repeat-todo/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ScheduleScreen에 반복 할 일 FAB 액션·CreateRepeatTodoDialog(빈도/간격/요일/가계부, buildRrule)·활성 템플릿 목록+중지(deleteTemplate) 추가. 반복 Todo 기능 end-to-end 완성"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- ViewModel / Repository / Worker / `RecurrenceOptions.kt`를 수정하지 마라. 이유: Step 0~3에서 완성됐다. 시그니처가 안 맞으면 해당 파일을 읽고 맞춰 호출하라.
- 새 디자인 토큰/색상을 만들지 마라. 기존 `Color.kt` 토큰만 사용하라.
- 기존 `CreateTodoDialog`(단발성)·`CreateEventDialog`의 동작을 바꾸지 마라. 반복은 별도 다이얼로그로 추가한다.
- 기존 코드를 광범위하게 리팩토링하지 마라. 이 step의 범위(반복 생성·관리 UI)만 작업하라.
