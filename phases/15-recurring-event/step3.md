# Step 3: delete-scope

## 배경

이 task(`15-recurring-event`)는 "반복 일정을 iCal식 읽기-전개로 구현"한다.

**이전 Step에서 만든 것:**
- (Step 0) `data/recurrence/EventRecurrence.kt` — `parseExdates`/`withExdate`, `expandEvents`, `EventOccurrence`.
- (Step 1) ScheduleViewModel이 발생을 표시. `ScheduleItem.Event`가 masterId + 발생일을 식별.
- (Step 2) `CreateEventDialog`로 반복 일정 생성 가능.

이 Step은 **반복 일정 삭제 시 범위 선택**("이 일정만 / 이후 모든 / 전체")을 구현한다. 현재 `EventRepository.delete(id)`는 마스터 전체를 hard delete할 뿐이다.

## 읽어야 할 파일

- `docs/PRD.md` 2.1, `docs/ARCHITECTURE.md` — 일정·할 일 수정(Edit) 섹션, 알람 라이프사이클.
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `parseExdates(json)`, `withExdate(json, date)`. **exdate 추가는 이 헬퍼로.**
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — **수정 대상.**
  - `delete(id)`: `dao.deleteById(id)`(**HARD delete** — 이벤트는 가계부 연동 없어 하드 허용) + `remote.deleteEvent(id)` + `alarmScheduler.cancel(id)`.
  - `save(event)`: upsert + `remote.upsertEvent` + `scheduleForEvent`. **FirestoreDataSource가 `exdatesJson`/`overridesJson`/`rrule`을 이미 동기화한다 → 엔티티만 갱신하면 동기화 자동.**
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — `getById`, `deleteById`(hard), `upsert`.
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleScreen.kt` — `EventCard(event: EventEntity, onEdit, onDelete)`(L~349). 현재 삭제는 카드 내부 확인 후 `onDelete()` → 호출부 `viewModel.deleteEvent(item.entity.id)`(L~160). 카드 **롱프레스=편집**(기존). 공유 다이얼로그(`LSyncDialog`) 스타일 사용. **Step 1에서 `ScheduleItem.Event`에 `occurrenceDate`가 추가됐고 `entity.id`=마스터 id, `entity.rrule`로 반복 여부 판별 가능 — 이 둘을 삭제 호출에 함께 넘긴다.**
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt` — `deleteEvent(id)` 위임. 범위 인자를 받도록 확장.

## 작업

### 1) EventRepository — 삭제 범위 메서드

```kotlin
// 이 발생만: 마스터의 exdatesJson에 해당 날짜 추가 후 save(동기화 자동). 알람은 다음 발생 기준 재계산(Step 5에서 일원화되나, 여기선 save가 scheduleForEvent 호출).
suspend fun deleteOccurrence(masterId: String, occurrenceDate: String)

// 이후 모든: 마스터 rrule에 UNTIL=(occurrenceDate 전날)을 설정해 시리즈를 절단. occurrenceDate 이상 발생 제거. save.
suspend fun deleteFollowing(masterId: String, occurrenceDate: String)

// 전체: 기존 delete(id) 재사용 (hard delete + 원격 삭제 + 알람 취소).
suspend fun deleteSeries(masterId: String)   // = delete(masterId)
```

- **이 발생만:** `withExdate(existing.exdatesJson, occurrenceDate)`로 갱신한 엔티티를 `save()`. 단발(rrule==null) 발생에 "이 일정만"이 들어오면 그냥 전체 삭제와 동일하게 처리.
- **이후 모든:** rrule에 `;UNTIL=`을 붙이거나 교체한다. **UNTIL 경계 주의(함정): UNTIL은 inclusive이므로 `occurrenceDate`를 포함하지 않으려면 `occurrenceDate`의 전날을 UNTIL로** 둔다(또는 lib-recur 규약에 맞춰 정확히). 잘못하면 분할일이 중복되거나 누락된다. UNTIL 값 형식은 lib-recur가 파싱 가능한 형식(예: `UNTIL=20240507` 또는 `UNTIL=20240507T000000Z`)으로. occurrenceDate가 시작일이면 사실상 전체 삭제.
- **전체:** `delete(masterId)` 그대로.

### 2) UI — 삭제 범위 다이얼로그

- 반복 발생(`entity.rrule != null`)을 삭제하려 할 때 `LSyncDialog`(또는 선택지 다이얼로그)로 "이 일정만 / 이후 모든 일정 / 전체 일정" 3택을 띄운다. 발생일은 Step 1에서 추가된 `ScheduleItem.Event.occurrenceDate`로 전달한다.
- **비반복 일정(`entity.rrule == null`)은 범위 선택 없이 바로 전체 삭제**(기존 `deleteEvent(id)` 경로 유지).
- 선택에 따라 ViewModel의 `deleteEvent(masterId, scope, occurrenceDate)`를 호출한다. 기존 `deleteEvent(id)` 호출부와 시그니처 충돌이 나지 않게 오버로드하거나 기본 인자를 둔다(비반복 경로 회귀 방지).

### 3) ViewModel

- `deleteEvent`를 범위(`scope`: THIS / FOLLOWING / ALL)와 `occurrenceDate`를 받도록 확장하고 해당 Repository 메서드로 위임. 완료 후 `widgetRefreshHelper.requestUpdate()`.

## 핵심 규칙 (반드시 지킬 것)

- **"전체 삭제"는 기존 hard delete(`delete(id)`)를 재사용하라.** 이벤트는 soft delete가 아니다(가계부 연동 없음). 이유: 기존 컨벤션 일관.
- **exdate 추가는 `EventRecurrence.withExdate`로.** JSON을 손으로 만들지 마라. 이유: Step 0 파서와 형식 일치.
- **UNTIL 경계 off-by-one 주의.** `occurrenceDate`는 절단 후 보이면 안 된다(이후 삭제). 분할일 중복/누락 금지. 이유: 함정.
- **동기화는 `save()`에 맡겨라.** `exdatesJson`/`rrule`은 FirestoreDataSource가 이미 동기화한다. 별도 원격 호출 추가 금지(중복).
- **비반복 일정엔 범위 다이얼로그를 띄우지 마라.** 바로 전체 삭제(기존 UX).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - "이 일정만"이 exdate를 추가(`withExdate`)하고 save하는가? 해당 발생이 화면에서 사라지는가?(Step 1 전개가 exdate 반영)
   - "이후 모든"이 UNTIL로 절단하며 경계(occurrenceDate 미포함)가 맞는가?
   - "전체"가 기존 hard delete를 재사용하는가?
   - 비반복은 범위 없이 전체 삭제인가?
   - 동기화를 `save()`에 맡겼는가(중복 원격 호출 없음)?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "삭제 범위 구현 — EventRepository.deleteOccurrence(exdate 추가)/deleteFollowing(rrule UNTIL 절단)/deleteSeries(=기존 hard delete). 삭제 범위 다이얼로그(반복만), ViewModel deleteEvent(scope,date) 확장. 동기화는 save 위임"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- "전체 삭제"를 soft delete로 바꾸지 마라. 이유: 이벤트는 hard delete 컨벤션.
- exdate/rrule JSON·문자열을 손으로 조립하지 마라. 이유: Step 0 파서 호환 깨짐.
- 편집(override) 로직을 여기서 만들지 마라. 이유: Step 4의 범위.
- 알람 재계산 로직을 새로 만들지 마라. `save()`의 `scheduleForEvent`에 맡기고, 정교한 다음-발생 알람은 Step 5에서 일원화한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
