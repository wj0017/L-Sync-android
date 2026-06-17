# Step 4: edit-scope

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다.

**이전 Step에서 만든 것:**
- (Step 0) `EventRecurrence.kt` — `parseOverrides`/`withOverride`, `EventOverride(title, startDate, hasAlarm)`, `withExdate`.
- (Step 1) ScheduleViewModel 표시(발생 masterId+date 식별, override 반영).
- (Step 2) `CreateEventDialog` 반복 피커(prefill 포함).
- (Step 3) 삭제 범위(`deleteOccurrence`=exdate, `deleteFollowing`=UNTIL, `deleteSeries`=hard delete) + 범위 다이얼로그 패턴.

이 Step은 **반복 일정 수정 시 범위 선택**("이 일정만 / 이후 모든 / 전체")을 구현한다. 현재 `EventRepository.update(...)`는 마스터 전체를 수정할 뿐이다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — "일정·할 일 수정(Edit)" 섹션(`EventRepository.update`가 불변 필드 보존 후 `save()` 재사용).
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `parseOverrides`, `withOverride(json, date, override)`, `EventOverride`. **override는 발생 날짜를 옮기지 않는다(시각·title·alarm만).**
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — **수정 대상.** `update(id, title, isAllDay, startDate, rrule, hasAlarm)`(불변 필드 보존 후 `save()`), `save()`(upsert+동기화+알람). `exdatesJson`/`overridesJson`/`rrule`은 FirestoreDataSource가 이미 동기화.
- Step 3에서 추가된 `deleteFollowing`(rrule UNTIL 절단 로직) — "이후 모든 수정"이 이 절단을 재사용한다.
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — 카드 **롱프레스=편집**(`CreateEventDialog` prefill 재사용), Step 3의 범위 다이얼로그.
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — `updateEvent(...)` 위임.

## 작업

### 1) EventRepository — 수정 범위 메서드

```kotlin
// 이 발생만: 마스터 overridesJson에 occurrenceDate→EventOverride 병합 후 save. 마스터 rrule/startDate는 불변.
suspend fun editOccurrence(masterId: String, occurrenceDate: String, title: String, startTimeIso: String?, hasAlarm: Boolean)

// 이후 모든: 원본 마스터를 occurrenceDate 전날까지 UNTIL 절단(Step 3 deleteFollowing 로직 재사용) +
//            occurrenceDate를 시작으로 하는 새 마스터 생성(새 id, 새 값, 같은 rrule).
suspend fun editFollowing(masterId: String, occurrenceDate: String, title: String, isAllDay: Boolean, startDate: String, rrule: String?, hasAlarm: Boolean)

// 전체: 기존 update(...) 재사용 (마스터 직접 수정).
suspend fun editSeries(...)  // = update(...)
```

- **이 발생만:** `withOverride(existing.overridesJson, occurrenceDate, EventOverride(title=..., startDate=<발생일 + 새 시각>, hasAlarm=...))`로 갱신 후 `save()`. **override의 startDate는 날짜 부분이 반드시 occurrenceDate와 같아야 한다**(시각만 변경). 날짜를 옮기지 마라(Step 0 규칙 — 발생 중복/유실 방지).
- **이후 모든:** ① 원본 마스터를 `deleteFollowing(masterId, occurrenceDate)`와 같은 방식으로 UNTIL 절단. ② `occurrenceDate`(+새 시각)를 `startDate`로, 같은(또는 수정된) `rrule`을 가진 **새 EventEntity**를 `create`/`save`. 새 마스터는 자신의 exdates/overrides는 비운다. **경계(off-by-one): 원본은 occurrenceDate 미포함, 새 마스터는 occurrenceDate 포함 — 중복/누락 없게.**
- **전체:** 기존 `update(...)` 재사용. 단 마스터 수정 시 기존 overrides/exdates를 어떻게 할지 결정: 제목/알람만 바꾸면 보존, rrule을 바꾸면 발생 키가 달라져 기존 overrides가 무의미해질 수 있음 → rrule 변경 시 overrides/exdates를 비우는 것을 권장(주석으로 의도 명시).

### 2) UI — 수정 범위 다이얼로그

- 반복 발생(`entity.rrule != null`) 편집 확정 시 "이 일정만 / 이후 모든 일정 / 전체 일정" 3택(Step 3 삭제와 동일 패턴, `LSyncDialog`). 발생일은 Step 1의 `ScheduleItem.Event.occurrenceDate`로 전달.
- **비반복 일정(`entity.rrule == null`)은 범위 없이 기존 `update` 경로**(기존 UX 유지).
- 편집 다이얼로그(`CreateEventDialog` prefill)는 그대로 쓰되, 확정 후 범위를 물어 ViewModel로 위임. 기존 `updateEvent(...)` 호출부와 충돌하지 않게 오버로드/기본 인자 사용.
- **주의:** 편집 prefill의 `startDate`는 합성 발생 엔티티의 값(발생일 기준)이다. "전체 수정"을 고르면 마스터의 원래 시작일이 아니라 이 발생일이 startDate로 들어갈 수 있으니, "전체"는 마스터를 다시 로드해 수정하거나 시작일 처리를 명확히 하라(발생일로 마스터 시작일을 덮어쓰지 말 것).

### 3) ViewModel

- `updateEvent`를 범위(`scope`)와 `occurrenceDate`를 받도록 확장, 해당 Repository 메서드로 위임. 완료 후 `widgetRefreshHelper.requestUpdate()`.

## 핵심 규칙 (반드시 지킬 것)

- **override는 발생 날짜를 옮기지 않는다(시각·title·alarm만).** override.startDate의 날짜 부분 = occurrenceDate. 이유: 발생 중복/유실 방지(Step 0).
- **override/exdate 추가는 `EventRecurrence.withOverride/withExdate`로.** JSON 손조립 금지.
- **"이후 모든 수정"의 UNTIL 절단은 Step 3 로직을 재사용**하고 경계(occurrenceDate 중복/누락)를 지켜라.
- **동기화는 `save()`/`create()`에 맡겨라.** overrides/rrule은 FirestoreDataSource가 동기화. 별도 원격 호출 금지.
- **비반복 일정엔 범위 다이얼로그를 띄우지 마라.** 기존 `update` 경로.
- **rrule 변경 시 기존 overrides/exdates 정합 처리**(권장: 비움)하고 의도를 주석으로 남겨라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - "이 일정만"이 override를 병합(`withOverride`)하고 그 발생만 바뀌는가?(날짜 이동 없음)
   - "이후 모든"이 원본 UNTIL 절단 + 새 마스터 생성이며 경계가 맞는가?
   - "전체"가 기존 `update` 재사용인가? rrule 변경 시 overrides 정합?
   - 비반복은 범위 없이 기존 경로인가?
   - 동기화를 save/create에 맡겼는가?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "수정 범위 구현 — EventRepository.editOccurrence(override 병합, 날짜 불변)/editFollowing(원본 UNTIL 절단+새 마스터)/editSeries(=기존 update). 수정 범위 다이얼로그(반복만), ViewModel updateEvent(scope,date) 확장. 동기화 save 위임"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- override로 발생 날짜를 옮기지 마라. 이유: 발생 중복/유실(Step 0 규칙).
- override/exdate/rrule을 손으로 JSON 조립하지 마라. 이유: Step 0 파서 호환.
- "이후 모든"에서 원본을 두고 새 마스터만 만들지 마라(원본 절단 필수). 이유: 발생 중복.
- 알람 다음-발생 재계산을 여기서 새로 만들지 마라. Step 5에서 일원화. 여기선 `save()`의 `scheduleForEvent`에 맡긴다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
