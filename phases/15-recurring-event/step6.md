# Step 6: home-widget-display

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다.

**이전 Step에서 만든 것:**
- (Step 0) `data/recurrence/EventRecurrence.kt` — `expandEvents(events, from, to)`(순수 Kotlin, ViewModel·위젯 모두 재사용 가능).
- (Step 1) ScheduleViewModel이 캘린더·일 목록에서 발생을 전개 표시.
- (Step 2~5) 생성·삭제·수정·알람.

문제: **반복 일정의 "오늘" 발생이 홈 화면과 위젯엔 안 뜬다.**
- `HomeViewModel`은 `eventRepository.observeByDateRange(today, today)`로 오늘 이벤트를 받는데, 이는 `startDate`가 오늘인 row만 → 과거 시작 반복 마스터의 오늘 발생 누락.
- `LSyncWidget.loadWidgetState`는 `eventDao.getByDate(todayStr)`(=`startDate LIKE today%`)로 오늘 이벤트를 받음 → 동일 누락.

이 Step은 홈·위젯의 "오늘 일정"이 **반복 발생까지 포함**하도록 Step 0 엔진을 적용한다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — `ui/home/`, `ui/widget/` 항목. 위젯은 Room 전용(Firestore 미사용), `WidgetEntryPoint`(@EntryPoint) 패턴.
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `expandEvents`, `EventOccurrence`.
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt` — **수정 대상.** `eventRepository.observeByDateRange(today, today)`로 오늘 이벤트를 combine해 `ScheduleItem.Event`로 노출(line ~77~81).
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — Step 1에서 추가된 `observeForExpansion`(과거 시작 반복 포함 조회)이 있으면 재사용. 없으면 `observeAll` 사용.
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — `getByDate(datePrefix)`(위젯 사용), Step 1에서 추가됐을 `observeForExpansion`/전개용 쿼리.
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — **수정 대상.** `loadWidgetState`에서 `entryPoint.eventDao().getByDate(todayStr)`로 오늘 이벤트를 받아 `WidgetState.events`로. `EventCard` 렌더.
- `app/src/main/java/com/lsync/app/ui/widget/WidgetState.kt` — `events` 필드 타입 확인.

## 작업

### 1) HomeViewModel — 오늘 발생 전개

- 오늘 이벤트를 가져올 때, **과거 시작 반복 마스터를 포함하는 조회**(Step 1의 `observeForExpansion(today, today)` 또는 `observeAll`)로 받아 `EventRecurrence.expandEvents(events, today, today)`로 전개한 발생을 오늘 일정으로 노출한다.
- **Step 1과 동일한 합성 발생 엔티티 방식으로 `ScheduleItem.Event(entity = 합성발생, occurrenceDate = today)`를 만든다** — `entity`는 마스터를 copy해 발생 startDate/title/hasAlarm을 반영하고 `entity.id`는 마스터 id 유지. HomeScreen은 `item.entity`를 읽어 표시하므로 렌더 무수정으로 올바른 값이 나온다.
- 비반복 일정 회귀 없음.

### 2) 위젯 — 오늘 발생 전개

- `loadWidgetState`에서 오늘 이벤트를 `getByDate(todayStr)` 대신 **반복 마스터까지 포함**해 가져온 뒤(`getAllByDateRange`류가 없으면 `observeForExpansion`에 대응하는 suspend 조회를 `EventDao`에 추가하거나 `observeAll`의 suspend 버전 사용) `EventRecurrence.expandEvents(events, todayStr, todayStr)`로 전개한다.
  - 위젯은 Flow가 아닌 suspend 1회 조회를 쓰므로, 필요하면 `EventDao`에 `suspend fun getForExpansion(from, to): List<EventEntity>`를 추가한다(DAO 메서드 추가는 AppModule 변경 아님 — 추가 @Provides 불필요).
- 전개 발생을 `WidgetState.events`가 받는 형태로 매핑한다. `EventCard` 표시 규칙(시간지정="HH:mm 제목", 최대 3개)은 유지.
- 위젯은 **Room 전용** 유지(Firestore·네트워크 추가 금지).

## 핵심 규칙 (반드시 지킬 것)

- **홈·위젯 모두 `EventRecurrence.expandEvents`로 전개하라.** 각자 rrule을 직접 파싱하지 마라(중복 구현 금지).
- **과거 시작 반복 마스터를 오늘 조회에서 누락하지 마라.** `startDate=today`/`LIKE today%`만 쓰면 안 된다. 이유: 표시 누락(함정 10).
- **위젯은 Room 전용 유지.** Firestore/네트워크 추가 금지(ARCHITECTURE).
- **비반복 일정 회귀 금지.** 전개 후에도 단발은 그대로.
- 알람·생성·편집/삭제 로직을 바꾸지 마라(Step 2~5). 이 Step은 홈·위젯 **표시**만.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 홈 "오늘 일정"에 과거 시작 반복 일정의 오늘 발생이 뜨는가?
   - 위젯 SCHEDULE 섹션에 같은 발생이 뜨는가?
   - 둘 다 `EventRecurrence.expandEvents`를 쓰는가?
   - 위젯이 Room 전용인가(Firestore 미추가)?
   - 비반복 일정 회귀 없음?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 6을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "홈·위젯 오늘 일정에 반복 발생 전개 — HomeViewModel·LSyncWidget이 EventRecurrence.expandEvents 적용(과거 시작 반복 포함 조회). 위젯 Room 전용 유지, 비반복 회귀 없음. Phase 15 완료"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 홈/위젯에서 rrule을 직접 전개하지 마라. 이유: Step 0 엔진 중복.
- `startDate=today`/`LIKE today%`만으로 오늘 이벤트를 조회하지 마라. 이유: 과거 시작 반복 누락.
- 위젯에 Firestore/네트워크를 추가하지 마라. 이유: Room 전용 원칙.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
