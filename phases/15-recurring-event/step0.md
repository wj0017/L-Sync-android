# Step 0: recurrence-expansion

## 배경

이 task(`15-recurring-event`)는 "반복 일정(Event)을 **iCal식 읽기-전개**로 구현"한다. 단일 Event row에 `rrule`을 저장하고, 화면 표시·알람 시점에 그 rrule을 가시 범위로 **전개(expand)**해 가상 발생(occurrence)을 만든다. 별도 인스턴스 row를 만들지 않는다(Todo의 Materialization과 다른 전략).

현황:
- `EventEntity`에는 이미 `rrule: String?`, `exdatesJson: String?`(제외일 JSON 배열), `overridesJson: String?`(날짜→수정필드 JSON 맵), `deletedAt`이 있다. **로직은 전혀 없다.**
- `org.dmfs:lib-recur:0.15.0` + `rfc5545-datetime:0.3` 의존성이 있다. `TodoMaterializerWorker`가 `RecurrenceRule(rrule).iterator(DateTime)`로 Todo를 전개하는 검증된 패턴이 있다.
- `ui/schedule/RecurrenceOptions.kt`의 `buildRrule`/`describeRrule`은 순수 Kotlin이며, rrule 문자열은 `FREQ=...;INTERVAL=...;BYDAY=...` 형태(접두사 `RRULE:` 없을 수도, 있을 수도).

이 Step은 **순수 Kotlin 전개 엔진만** 만든다. DB·UI·알람은 건드리지 않는다. 이후 모든 step(표시·삭제·수정·알람)이 이 엔진을 재사용한다.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md`, `docs/TechSpec.md` 4장(백그라운드 엔진 — RRULE), `CLAUDE.md`(Floating Time·타임존 변환 금지 CRITICAL).
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt` — 필드 정의. `startDate`: 종일=`"2024-05-01"`, 시간지정=`"2024-05-01T09:00:00+09:00"`. `exdatesJson`/`overridesJson` 주석.
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt` — **lib-recur 사용 레퍼런스.** `RecurrenceRule(rrule.removePrefix("RRULE:"))`, `DateTime(epochDay * MS_PER_DAY)`, `iter.nextMillis() / MS_PER_DAY → LocalDate`, `MAX_ITERATIONS = 10_000` 안전장치.
- `app/src/main/java/com/lsync/app/ui/schedule/RecurrenceOptions.kt` — rrule 문자열 형식, 깨진 입력 방어 패턴(`describeRrule`의 try/catch).

## 작업

`app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt`(새 패키지/파일)에 순수 Kotlin 전개 엔진을 만든다. Android 의존 최소화(가능하면 `java.time`만).

### 모델

```kotlin
data class EventOccurrence(
    val masterId: String,    // 원본 EventEntity.id
    val date: String,        // YYYY-MM-DD — 발생 날짜(전개 키)
    val startDate: String,   // 유효 startDate. 시간지정이면 date 부분만 발생일로 치환, 시각·offset 보존
    val title: String,       // 유효 제목(override 반영)
    val isAllDay: Boolean,
    val hasAlarm: Boolean,   // 유효 알람 여부(override 반영)
    val isOverridden: Boolean, // 이 발생에 override가 적용됐는가
    val isRecurring: Boolean,  // rrule에서 나온 발생인가(단발 false)
)

data class EventOverride(
    val title: String? = null,
    val startDate: String? = null,  // 시각만 바꿈. 날짜 부분은 발생일과 동일해야 함(아래 규칙)
    val hasAlarm: Boolean? = null,
)
```

### 함수 시그니처

```kotlin
// 단일 이벤트를 [from, to] (둘 다 inclusive, YYYY-MM-DD)로 전개.
// 비반복(rrule==null): startDate의 날짜가 범위 안이면 1개, 아니면 0개.
// 반복: rrule 전개 → 범위 필터 → exdates 제외 → overrides 적용.
fun expandEvent(event: EventEntity, from: String, to: String): List<EventOccurrence>

// 여러 이벤트 전개 후 평탄화(ViewModel 편의).
fun expandEvents(events: List<EventEntity>, from: String, to: String): List<EventOccurrence>

// afterDate(YYYY-MM-DD) "다음" 발생을 1개 반환(알람용). exdates 제외·overrides 반영. 없으면 null.
fun nextOccurrence(event: EventEntity, afterDate: String): EventOccurrence?

// JSON 헬퍼 — exdates(배열) / overrides(맵)
fun parseExdates(json: String?): Set<String>
fun withExdate(json: String?, date: String): String        // date 추가한 JSON 배열 반환
fun parseOverrides(json: String?): Map<String, EventOverride>
fun withOverride(json: String?, date: String, override: EventOverride): String  // 추가/병합한 JSON 맵 반환
```

### 전개 규칙(핵심)

- **DTSTART** = `event.startDate.take(10)`를 LocalDate로. lib-recur `iterator`로 전개하되 범위 상한(`to`) 초과 시 break, MAX_ITERATIONS(예: 10_000) 안전장치(무한 rrule 방어).
- **시간지정 발생의 시각 보존:** 시간지정 이벤트는 발생 `startDate`를 만들 때 **날짜 부분만 발생일로 치환하고 `T09:00:00+09:00` 같은 시각·offset 문자열은 그대로 유지**한다. 날짜만 갈아끼운다. (예: 원본 `2024-05-01T09:00:00+09:00`, 발생일 `2024-05-08` → `2024-05-08T09:00:00+09:00`.)
- **종일 발생:** `startDate = 발생일`(YYYY-MM-DD). Floating Date. **타임존 변환 절대 금지**(CLAUDE.md CRITICAL).
- **exdates 제외:** `parseExdates`에 든 날짜의 발생은 결과에서 뺀다.
- **overrides 적용:** `overridesJson`은 **발생일(원본 날짜)을 키**로 한다. override가 있으면 title/시각/hasAlarm을 덮어쓰고 `isOverridden=true`. **override는 발생 날짜를 바꾸지 않는다**(startDate override는 시각만; 날짜 부분은 그 발생일과 같아야 함). 이유: 날짜를 옮기면 같은 발생이 두 날에 보이거나 사라져 발생 수가 흔들린다.
- **비반복 이벤트:** rrule이 null이면 `expandEvent`는 startDate 날짜가 [from,to]에 들면 그 1개를, 아니면 빈 리스트를 반환(override/exdate 무시). 단발은 그대로.
- **깨진 입력 방어:** rrule/JSON 파싱 실패는 예외를 던지지 말고 빈 결과/원본 보존으로 흡수한다(`describeRrule` 방식). UI/알람 크래시 방지.

## 핵심 규칙 (반드시 지킬 것)

- **종일 일정은 Floating Date. 타임존 변환 금지.** (CLAUDE.md CRITICAL)
- **시간지정 발생은 날짜만 치환, 시각·offset 문자열 보존.** 이유: 매 발생마다 시각이 바뀌면 안 된다.
- **override 키 = 발생 원본 날짜. 날짜 이동 금지.** 이유: 발생 중복/유실 방지(함정).
- **무한 rrule 방어**(`to` 상한 + MAX_ITERATIONS). 이유: `FREQ=DAILY` 무한 전개로 OOM/ANR 방지.
- **파싱 실패는 예외 없이 흡수.** 이유: 깨진 rrule/JSON이 화면·알람을 죽이면 안 된다.
- 이 Step은 **순수 함수만**. DB 조회·UI·알람·`EventRepository` 변경 금지. 이유: 레이어 분리(이후 step이 소비).

## Acceptance Criteria

```bash
./gradlew assembleDebug          # 컴파일 에러 없음
./gradlew lintDebug              # 린트 경고 없음
./gradlew testDebugUnitTest      # 단위 테스트(작성 시) 통과 — GRADLE_USER_HOME=C:\ghome 우회 가능
```

(권장) `expandEvent`에 대한 간단한 단위 테스트: 매주 반복 4주 전개 개수, exdate 1개 제외, override title 반영, 종일 vs 시간지정 startDate 형식.

## 검증 절차

1. 위 AC 커맨드를 실행한다(`testDebugUnitTest`가 환경 문제로 막히면 `GRADLE_USER_HOME=C:\ghome`로 우회, 그래도 안 되면 assemble/lint만으로 진행하고 summary에 명시).
2. 체크리스트:
   - `EventRecurrence.kt`가 순수 Kotlin(Android/DB/UI 의존 없음)인가?
   - 시간지정 발생이 시각·offset를 보존하는가? 종일이 Floating Date인가?
   - 무한 rrule 방어·파싱 예외 흡수가 있는가?
   - override가 발생 날짜를 옮기지 않는가?
3. 결과에 따라 `phases/15-recurring-event/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "data/recurrence/EventRecurrence.kt 순수 Kotlin 전개 엔진 — expandEvent/expandEvents/nextOccurrence + exdates/overrides JSON 헬퍼(parse/with). lib-recur 사용, 시간지정 시각보존·종일 Floating·무한방어·예외흡수. EventOccurrence/EventOverride 모델"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- `EventRepository`/DAO/ViewModel/AlarmScheduler를 수정하지 마라. 이유: Step 1~6의 범위다. 이 Step은 엔진만.
- 인스턴스 row를 생성하지 마라(Todo Materialization 방식 금지). 이유: 이 task는 읽기-전개 전략으로 확정됐다.
- 타임존 변환을 넣지 마라. 이유: 종일은 Floating, 시간지정은 문자열 보존(CLAUDE.md).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
