# Step 1: schedule-display

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다. 단일 Event row + `rrule`을 화면 표시 시점에 전개한다.

**이전 Step(0)에서 만든 것:** `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` (순수 Kotlin)
- `expandEvents(events: List<EventEntity>, from: String, to: String): List<EventOccurrence>`
- `EventOccurrence(masterId, date, startDate, title, isAllDay, hasAlarm, isOverridden, isRecurring)`
- 비반복 이벤트는 startDate 날짜가 범위에 들면 1개, 반복은 rrule 전개(−exdates, +overrides).

이 Step은 **일정 화면(ScheduleViewModel)** 이 이 엔진을 소비해 캘린더 점·일 목록에 반복 발생을 표시하게 한다. 현재는 반복 일정이 **시작일에만** 보인다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — `ui/schedule/` 항목, Room SSOT.
- `CLAUDE.md` — `collectAsState()` 사용(`collectAsStateWithLifecycle` 금지).
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — Step 0 엔진.
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — **수정 대상.** `ScheduleUiState`(`monthEvents`, `eventDateSet = monthEvents.map { it.startDate.take(10) }`, `dayItems`, `ScheduleItem.Event/Todo`). `loadMonthData`(월 `observeByDateRange`), `observeDayItems`(특정일 `observeByDateRange(date,date)` + todo combine).
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — `observeByDateRange(from,to)`는 **`startDate BETWEEN from AND to`**. 반복 마스터의 startDate가 과거면 이 쿼리에 안 잡힘(함정, 아래).
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `observeByDateRange`, `observeAll` 위임.
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — `ScheduleItem.Event`를 어떻게 렌더하는지(전개 발생을 같은 카드로 보여줄 수 있는지 확인). `eventDateSet`로 캘린더 점 표시.

## 작업

ScheduleViewModel이 월/일 표시를 **전개 결과 기반**으로 만든다.

### 1) 과거 시작 반복 마스터를 포함하는 조회 (함정 해결)

`observeByDateRange(from, to)`는 `startDate >= from`이라 **과거에 시작한 반복 마스터를 놓친다.** 표시 전개를 위해선 그 마스터도 필요하다. 해결책 중 하나를 택한다(에이전트 재량, 단 누락 없을 것):

- (권장) `EventDao`에 전개용 쿼리 추가: `deletedAt IS NULL AND startDate <= :to AND (rrule IS NOT NULL OR startDate >= :from)` 형태의 `observeForExpansion(from, to)`. → 범위 내 단발 + 시작일이 `to` 이전인 모든 반복 마스터를 포함.
- 또는 개인용 앱 규모를 감안해 `observeAll()`을 구독하고 ViewModel에서 `expandEvents(all, from, to)`로 거른다(단순하지만 전체 로드).

`EventDao`에 쿼리를 추가하면 `EventRepository`에 위임 메서드도 추가한다. **새 DAO 메서드라도 기존 DAO/Repository는 이미 AppModule에 등록돼 있으므로 추가 @Provides는 불필요**(DAO 메서드 추가는 모듈 변경 아님). 새 Repository를 만들지는 마라.

### 2) 월 표시 — 캘린더 점

- `loadMonthData(month)`에서 위 조회로 받은 이벤트를 `expandEvents(events, from, to)`로 전개한다.
- `ScheduleUiState`에 전개 발생을 담을 필드를 추가하거나(`monthOccurrences: List<EventOccurrence>`), `eventDateSet`을 전개 발생의 `date` 집합으로 산출하도록 바꾼다. **`eventDateSet`이 전개 발생 날짜를 모두 포함해야 캘린더 점이 매주/매월 찍힌다.**

### 3) 일 표시 — 일 목록

- `observeDayItems(date)`에서 그 날짜의 이벤트 발생을 `expandEvents(events, date, date)`로 만들어 `ScheduleItem.Event`로 변환한다.

#### ⚠️ `ScheduleItem.Event` 하위호환 (가장 중요 — 빌드 연쇄 붕괴 방지)

`sealed class ScheduleItem`의 `data class Event(val entity: EventEntity)`는 **여러 곳에서 `item.entity`로 소비된다**:
- `ScheduleScreen.kt`: `key = "e_${it.entity.id}"`(L~151,243), `EventCard(item.entity, onEdit, onDelete = { viewModel.deleteEvent(item.entity.id) })`(L~157-160), `EventCard(event: EventEntity, ...)`(L~349).
- `HomeScreen.kt`: `it.entity.id`, `val event = item.entity`(L~243,759-777) — 읽기 표시.
- `HomeViewModel.kt`: `add(ScheduleItem.Event(it))`(L~81).

**`Event`의 생성자에서 `entity: EventEntity`를 제거/교체하면 Home·Schedule이 동시에 컴파일 깨진다.** 각 step은 독립적으로 `assembleDebug`를 통과해야 하므로, 이 Step에서 Home까지 고치게 되면 Step 6 범위를 침범하고 AC가 깨진다.

따라서 **하위호환 방식**을 강제한다:

```kotlin
data class Event(
    val entity: EventEntity,            // 기존 필드 유지 — 단, "합성 발생 엔티티"를 담는다
    val occurrenceDate: String? = null, // 기본값 → 기존 호출부(HomeViewModel 등) 무수정 컴파일
) : ScheduleItem()
```

- **합성 발생 엔티티:** 반복 발생을 표시할 때 `entity`에는 **마스터를 `copy`해 그 발생의 유효 값으로 만든 EventEntity**를 담는다 — `startDate`=발생 startDate(Step 0가 시각 보존), `title`=override 반영 제목, `hasAlarm`=유효값. `entity.id`는 **마스터 id 그대로 유지**(편집/삭제가 마스터를 찾을 수 있게). `occurrenceDate`=발생일.
- 이렇게 하면 `EventCard`가 `entity.startDate`/`entity.title`을 그대로 읽어 **올바른 날짜·제목**을 표시한다(EventCard·렌더 무수정).
- 비반복 이벤트는 `entity`=원본, `occurrenceDate`=null(또는 startDate)로 그대로.
- `HomeViewModel`의 `ScheduleItem.Event(it)`는 `occurrenceDate` 기본값 덕에 **무수정 컴파일**된다(홈의 발생 전개는 Step 6에서).
- LazyColumn key: 하루 목록엔 마스터당 발생이 최대 1개라 `e_${entity.id}`가 유일하다(변경 불필요). 혹시 한 화면에 같은 마스터의 발생이 2개 이상 들어갈 여지가 있으면 key에 `occurrenceDate`를 덧붙여라.

비반복 이벤트는 전개해도 1개로 동일하게 동작해야 한다(회귀 없음).

## 핵심 규칙 (반드시 지킬 것)

- **과거 시작 반복 마스터를 표시 조회에서 누락하지 마라.** 이유: `observeByDateRange`의 `startDate >= from` 때문에 지난달 시작한 매주 일정이 이번 달에 안 보이는 버그가 난다.
- **전개는 Step 0 `EventRecurrence`를 통하라.** ViewModel에서 rrule을 직접 파싱하지 마라(중복 구현 금지).
- **`ScheduleItem.Event`는 하위호환으로 확장하라**(`entity: EventEntity` 유지 + `occurrenceDate: String? = null` 기본값 추가, `entity`엔 합성 발생 엔티티). 생성자에서 `entity`를 제거/교체하지 마라. 이유: Home·Schedule이 `item.entity`를 소비하므로 Step 1만으로 빌드가 깨지고 Step 6 범위를 침범한다.
- **합성 발생 엔티티의 `entity.id`는 마스터 id 유지.** 이유: Step 3·4의 "이 일정만" 편집/삭제가 마스터를 찾고, `occurrenceDate`로 어느 발생인지 안다.
- **비반복 이벤트 회귀 금지.** 전개 후에도 단발 일정은 그대로 1회 표시·동작.
- **`collectAsState()`만**, Room Flow 구독 유지. UI가 Firestore 직접 구독 금지. (CLAUDE.md/ARCHITECTURE)
- 알람·생성 UI·편집/삭제 로직을 이 Step에서 만들지 마라(Step 2~5). 이 Step은 **표시(읽기)** 만.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 캘린더 점·일 목록이 전개 발생을 표시하는가?(과거 시작 매주 일정이 이번 달에 점으로 찍히는가)
   - 전개가 `EventRecurrence`를 통하는가?
   - `ScheduleItem.Event`가 masterId+발생일을 식별하는가?
   - 비반복 일정이 그대로 동작하는가? `collectAsState()`만 썼는가?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ScheduleViewModel이 EventRecurrence.expandEvents로 월/일 전개 — 캘린더 점·일 목록에 반복 발생 표시. EventDao.observeForExpansion(과거 시작 반복 마스터 포함) 추가, ScheduleItem.Event가 masterId+발생일 식별. 비반복 회귀 없음"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- `observeByDateRange`만 써서 전개하지 마라(과거 시작 반복 누락). 이유: 표시 버그.
- ViewModel에서 rrule을 직접 전개하지 마라. 이유: Step 0 엔진 중복.
- 홈/위젯 표시를 여기서 바꾸지 마라. 이유: Step 6(home-widget-display)의 범위.
- 생성/편집/삭제/알람 로직을 추가하지 마라. 이 Step은 일정 화면 표시만.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
