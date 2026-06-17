# Step 5: recurrence-alarm

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다. 단일 row + rrule이라 **반복 발생마다의 알람 row가 없다.**

**이전 Step에서 만든 것:**
- (Step 0) `EventRecurrence.nextOccurrence(event, afterDate): EventOccurrence?` — exdates/overrides 반영한 다음 발생.
- (Step 1~4) 표시·생성·삭제·수정. `EventRepository.save()`가 `alarmScheduler.scheduleForEvent(event)`를 호출.

문제: `AlarmScheduler.scheduleForEvent(event)`는 **`event.startDate`(시작일) 하나로만** 트리거를 계산한다. 반복 일정은:
1. 시작일이 과거면 트리거가 과거 → 등록 스킵 → **알람이 전혀 안 울린다.**
2. AlarmManager는 one-shot이라, 한 발생이 울려도 **다음 발생을 누가 거는지** 경로가 없다.
3. 재부팅 복원 `EventDao.getFutureAlarmedEvents(fromDate)`는 `startDate >= fromDate`라 **과거 시작 반복 마스터를 누락**한다.

이 Step은 알람을 **반복 인식**하게 만든다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md`/`docs/TechSpec.md` 5장 — 알람 라이프사이클(트리거 단일 계산, REMINDER_HOUR=9, inexact 폴백, 재부팅 복원).
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `nextOccurrence(event, afterDate)`.
- `app/src/main/java/com/lsync/app/notification/AlarmScheduler.kt` — **수정 대상.** `scheduleForEvent(event)`: 현재 `event.isAllDay ? dateToReminderMillis(startDate) : OffsetDateTime.parse(startDate)`, `trigger <= now`면 스킵. `dateToReminderMillis`, `setAlarm`(inexact 폴백), `cancel`. companion 상수.
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — **수정 대상.** `getFutureAlarmedEvents(fromDate)` = `hasAlarm=1 AND deletedAt IS NULL AND startDate >= :fromDate`.
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` — 재부팅 복원이 `getFutureAlarmedEvents`→`scheduleForEvent`.
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` — **일 1회 주기 워커 패턴 레퍼런스**(`enqueuePeriodicWork`, `ExistingPeriodicWorkPolicy.KEEP`, `LSyncApplication.onCreate` 등록).
- `app/src/main/java/com/lsync/app/LSyncApplication.kt` — 주기 워커 등록 위치.

## 작업

### 1) `AlarmScheduler.scheduleForEvent` — 반복 인식

- `event.rrule == null`이면 기존 로직 그대로(단발).
- `event.rrule != null`이면 `EventRecurrence.nextOccurrence(event, afterDate = 오늘)`로 **다음 발생**을 구해 그 발생의 `startDate`로 트리거를 계산해 등록한다. 다음 발생이 없으면(UNTIL 지남 등) `cancel(event.id)`.
- 트리거 계산(시간지정=시각, 종일=09:00)·inexact 폴백·`trigger<=now` 스킵은 **기존 메서드를 재사용**한다(트리거 단일 계산 원칙 유지). 발생의 `startDate` 문자열은 단발과 같은 형식이므로 기존 계산식에 그대로 넣을 수 있어야 한다.
- 알람 PendingIntent requestCode/notify는 기존대로 `event.id` 기반(발생별 분리 불필요 — 항상 "다음 1개"만 건다).

### 2) `EventDao.getFutureAlarmedEvents` — 과거 시작 반복 포함

- 쿼리를 보정한다: `hasAlarm=1 AND deletedAt IS NULL AND (rrule IS NOT NULL OR startDate >= :fromDate)`. 이유: 과거 시작 반복 마스터도 복원 대상.

### 3) 다음 발생 재등록 틱 (one-shot 한계 해결)

- AlarmManager one-shot이라 한 발생이 울린 뒤 다음 발생이 자동 등록되지 않는다. **일 1회 주기 WorkManager**(`TodoMaterializerWorker.enqueuePeriodicWork` 패턴)로 `getFutureAlarmedEvents`를 다시 `scheduleForEvent`에 흘려 **매일 다음 발생으로 알람을 갱신**한다.
  - 새 워커(예: `EventAlarmRefreshWorker`) 또는 기존 일 1회 워커에 이벤트 알람 갱신을 얹어도 된다(재량). `ExistingPeriodicWorkPolicy.KEEP`.
  - `LSyncApplication.onCreate`에서 등록.
- (선택) 앱 오픈 시에도 한 번 갱신하면 즉시성이 좋아진다(과용 금지).

## 핵심 규칙 (반드시 지킬 것)

- **트리거 시각 계산은 `AlarmScheduler` 안에 단일하게 유지하라.** 발생의 startDate를 만들어 기존 계산식에 넣되, 시각 계산식을 복제하지 마라. 이유: 라이브/복원 드리프트 방지(Phase 8 설계).
- **반복은 항상 "다음 1개" 발생만 등록.** 발생마다 알람을 잔뜩 걸지 마라. 이유: AlarmManager 슬롯·중복 방지.
- **`getFutureAlarmedEvents`에 과거 시작 반복 마스터를 포함시켜라.** 이유: 누락 시 반복 알람이 재부팅 후 사라진다.
- **다음 발생 재등록 경로(일 1회 틱)를 반드시 둬라.** 이유: one-shot이라 갱신 없으면 첫 발생 이후 알람이 끊긴다.
- **inexact 폴백·`trigger<=now` 스킵을 유지하라.** (Phase 8 규칙)
- `EventRecurrence`를 통해 다음 발생을 구하라(rrule 직접 파싱 금지).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `scheduleForEvent`가 반복 시 `nextOccurrence`로 다음 발생을 등록하는가? 발생 없으면 cancel하는가?
   - 트리거 시각 계산을 복제하지 않고 기존 식을 재사용하는가?
   - `getFutureAlarmedEvents`가 과거 시작 반복 마스터를 포함하는가?
   - 일 1회 틱으로 다음 발생 재등록 경로가 있고 `LSyncApplication`에 등록됐는가?
   - inexact 폴백·과거 스킵이 유지되는가?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 5를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "반복 알람 — scheduleForEvent가 rrule!=null이면 EventRecurrence.nextOccurrence로 다음 1개 발생 등록(없으면 cancel), 기존 트리거식 재사용. getFutureAlarmedEvents에 rrule IS NOT NULL 포함, 일1회 EventAlarmRefreshWorker로 다음 발생 갱신(LSyncApplication 등록). inexact 폴백 유지"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 발생마다 알람을 여러 개 등록하지 마라. 이유: AlarmManager 과다·중복.
- 트리거 시각 계산식을 `AlarmScheduler` 밖에 복제하지 마라. 이유: 드리프트(Phase 8).
- `nextOccurrence` 없이 rrule을 직접 파싱하지 마라. 이유: Step 0 엔진 중복.
- 표시·생성·편집/삭제 로직을 바꾸지 마라(Step 1~4). 이 Step은 알람만.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
