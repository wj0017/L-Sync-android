# Step 2: create-recurrence-ui

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다.

**이전 Step에서 만든 것:**
- (Step 0) `data/recurrence/EventRecurrence.kt` — 전개 엔진.
- (Step 1) ScheduleViewModel이 전개 발생을 캘린더·일 목록에 표시. **즉, rrule이 있는 이벤트는 이제 매주/매월 화면에 보인다.**

문제: 사용자가 **일정에 반복 규칙을 넣을 UI가 없다.** `CreateEventDialog`는 `onConfirm(title, isAllDay, startDate, rrule, hasAlarm)`을 호출하지만 `rrule`로 항상 `initial?.rrule`(수정 시 기존 값)만 넘긴다 — 새 반복을 만드는 입력이 없다.

이 Step은 **반복 할 일(Todo) 템플릿이 쓰는 것과 동일한 반복 피커**를 일정 생성 다이얼로그에 추가한다.

## 읽어야 할 파일

- `docs/UI_GUIDE.md`, `CLAUDE.md` 디자인 시스템 — 다크 미니멀, Pretendard, Ghost chip, 새 색상 금지.
- `app/src/main/java/com/lsync/app/ui/schedule/RecurrenceOptions.kt` — `RecurrenceOption(frequency, interval, weekdays)`, `Frequency`, `Weekday`, `buildRrule(option): String`, `describeRrule(rrule): String`. **그대로 재사용.**
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — **수정 대상.**
  - `CreateEventDialog`(현재 시그니처: `onConfirm(title, isAllDay, startDate, rrule, hasAlarm)`, 내부에서 `onConfirm(title, isAllDay, startDate, initial?.rrule, hasAlarm)` 호출 — line ~538).
  - **`CreateRepeatTodoDialog`(line ~620~716)** — 반복 피커 UI의 레퍼런스. `RecurrenceOption` state, 빈도/간격/요일 선택, `describeRrule(buildRrule(option))` 미리보기를 어떻게 그렸는지 그대로 참고/재사용.
  - `CreateEventDialog` 호출부(line ~188 create, ~224 update prefill).
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — `createEvent(...)`/`updateEvent(...)`는 이미 `rrule` 파라미터를 받는다. **ViewModel/Repository 변경 불필요.**

## 작업

`CreateEventDialog`에 반복 설정 입력을 추가한다.

- 다이얼로그 안에 "반복" 토글/섹션을 둔다. 켜면 `CreateRepeatTodoDialog`와 동일한 빈도(매일/매주/매월/매년)·간격(N마다)·요일(매주) 선택 UI를 노출하고, `describeRrule(buildRrule(option))`로 한국어 미리보기를 보여준다.
- 확정 시 `rrule = if (반복 켜짐) buildRrule(option) else null`로 만들어 기존 `onConfirm(title, isAllDay, startDate, rrule, hasAlarm)`에 넘긴다.
- **수정 진입(prefill):** `initial?.rrule`이 있으면 반복 토글을 켜고 그 rrule을 피커 상태로 역파싱해 보여준다. 역파싱 헬퍼가 없으면 `RecurrenceOptions.kt`에 `parseRrule(rrule): RecurrenceOption?`를 추가해도 된다(반복 모듈 범위 내). 역파싱이 부담되면 최소한 "반복 있음"만 인지시키고 `describeRrule`로 텍스트 표시 + 재선택 가능하게 한다.
- 종일/시간지정·알람 등 기존 입력은 그대로 둔다. 반복 입력만 추가한다.

디자인은 `CreateRepeatTodoDialog`의 칩/토글 스타일(Ghost chip, Pretendard, 다크 토큰)을 그대로 따른다. 새 색상·컴포넌트를 만들지 마라.

## 핵심 규칙 (반드시 지킬 것)

- **`buildRrule`/`describeRrule`/`RecurrenceOption`을 재사용하라.** rrule 문자열을 손으로 조립하지 마라. 이유: Todo 반복과 형식 일관(Step 0 엔진·Materializer 파서 호환).
- **ViewModel/Repository를 바꾸지 마라.** `createEvent`/`updateEvent`는 이미 `rrule`을 받는다. 이 Step은 UI 입력만.
- **디자인 토큰 준수**(Pretendard, 다크, Ghost chip, 그림자 금지, 새 색상 금지).
- 반복 미설정 시 `rrule = null`을 넘겨 기존 단발 동작을 유지하라(회귀 금지).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `CreateEventDialog`에 반복 피커가 추가됐고 `buildRrule`로 rrule을 만드는가?
   - 반복 미설정 시 `rrule=null`인가(단발 회귀 없음)?
   - 수정 진입 시 기존 rrule을 인지/표시하는가?
   - `CreateRepeatTodoDialog`와 동일한 디자인 토큰을 쓰는가?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "CreateEventDialog에 반복 피커 추가 — RecurrenceOption/buildRrule/describeRrule 재사용(CreateRepeatTodoDialog 패턴). 반복 켜면 buildRrule(option), 끄면 null. 수정 prefill 처리. ViewModel/Repo 무변경"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- rrule 문자열을 직접 문자열 조립하지 마라. 이유: `buildRrule`과 형식이 어긋나면 Step 0 전개가 깨진다.
- ViewModel/Repository/DAO를 수정하지 마라. 이유: 이미 rrule을 받는다. UI만.
- 편집범위("이 일정만/이후/전체") 분기를 여기서 만들지 마라. 이유: Step 3·4의 범위.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
