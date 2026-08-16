# Step 2: web-recurring-events

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — **기준 전개 엔진. 파일 상단 주석의 "규칙(핵심)" 4줄을 반드시 읽어라.**
- `app/src/test/java/com/lsync/app/data/recurrence/EventRecurrenceTest.kt` — 기대 동작이 18개 케이스로 명세돼 있다. 웹 구현이 만족해야 할 동작의 사양서로 삼아라.
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`
- `web/types/models.ts` — step 0에서 타입이 정정되어 있다. 반드시 확인하라.
- `web/hooks/useEvents.ts` — 수정 대상
- `web/app/schedule/page.tsx` — 수정 대상
- `web/package.json`

**이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경 — 왜 이 작업이 필요한가

앱은 반복 일정을 **iCal식 "읽기-전개"** 로 다룬다. 반복 인스턴스를 DB row로 만들지 않고, 마스터 Event 1개의 `rrule`을 화면에 필요한 날짜 범위로 그때그때 전개한다(`expandEvent`).

웹은 이 개념이 없다. `web/app/schedule/page.tsx`의 `eventDates`가 `events.map(e => e.startDate?.slice(0, 10))`이라 **반복 일정이 첫 발생일에만 점으로 찍히고**, 매주 반복하는 일정이 웹 달력에서는 하루짜리로 보인다.

## 작업

### 1. `rrule` npm 패키지 추가

```bash
cd web && pnpm add rrule
```

**직접 RRULE 파서를 구현하지 마라.** 이유: 앱은 dmfs `lib-recur`를 쓴다. 손으로 포팅하면 두 엔진이 갈라져 같은 rrule에 대해 다른 날짜를 내놓게 되고, 이는 재현이 어려운 데이터 불일치가 된다.

### 2. `web/lib/recurrence.ts` 신규 — 전개 엔진

`EventRecurrence.kt`의 웹 대응물. Firebase·React 의존 없는 순수 모듈로 만든다.

```ts
import { LSyncEvent } from '@/types/models';

export interface EventOccurrence {
  masterId: string;     // 원본 LSyncEvent.id
  date: string;         // YYYY-MM-DD — 발생 날짜(전개 키)
  startDate: string;    // 유효 startDate. 시간지정이면 날짜 부분만 발생일로 치환, 시각·offset 보존
  title: string;        // override 반영된 유효 제목
  isAllDay: boolean;
  isOverridden: boolean;
  isRecurring: boolean; // rrule에서 나온 발생인가(단발 false)
}

export function expandEvent(event: LSyncEvent, from: string, to: string): EventOccurrence[];
export function expandEvents(events: LSyncEvent[], from: string, to: string): EventOccurrence[];
```

**전개 규칙 (`EventRecurrence.kt`와 1:1 대응 — 벗어나지 마라):**

1. **비반복**(`rrule`이 없거나 공백) — `startDate.slice(0,10)`이 `[from, to]` 안이면 발생 1개, 아니면 0개. exdates/overrides는 **무시**한다.
2. **반복** — rrule 전개 → `[from, to]` 범위 필터 → `exdates` 제외 → `overrides` 적용, 순서를 지킨다.
3. `from`/`to`는 **둘 다 inclusive**, `YYYY-MM-DD` 형식.
4. rrule 문자열은 `RRULE:` 접두사가 붙어 있을 수 있다. 앱처럼 `removePrefix("RRULE:")` 상당의 처리를 하라.
5. **파싱 실패는 예외를 던지지 말고 흡수하라.** 깨진 rrule은 빈 배열(또는 부분 결과)을 반환한다. 이유: 앱과 동일한 방어 정책이며, 달력 화면 전체가 죽는 것을 막는다.
6. **무한 반복 방어** — 반복 횟수 상한(앱의 `MAX_ITERATIONS = 10_000` 상당)을 걸어라. `UNTIL`/`COUNT` 없는 rrule이 존재한다.

**타임존 규칙 (CRITICAL — 여기서 틀리면 하루가 밀린다):**

- 종일 일정은 **Floating Date(`YYYY-MM-DD` 문자열)** 다. 타임존 변환을 하지 마라.
- 앱은 `LocalDate.toEpochDay() * 86400000L`로 DateTime을 만들고 `LocalDate.ofEpochDay(millis / 86400000L)`로 되읽는다 — 즉 **UTC 자정 기준**이다.
- 따라서 TS에서도 `dtstart`를 `new Date(Date.UTC(y, m-1, d))`로 만들고, 결과 날짜를 `toISOString().slice(0, 10)`으로 문자열화하라. **`new Date('2026-08-16')` 후 `getFullYear()/getMonth()/getDate()`로 되읽지 마라** — 로컬 타임존(KST)에서 하루가 밀린다.
- `rrule` 패키지는 `RRule` 옵션의 `dtstart`를 UTC로 다루므로 위 방식과 맞는다. 라이브러리 버전에 따라 동작이 다르면 UTC 왕복이 보장되는 쪽으로 맞춰라.

**`exdatesJson` 형식** — 문자열 배열의 JSON. 예: `["2026-08-19","2026-08-26"]`. 값은 `YYYY-MM-DD`. `JSON.parse` 실패 시 빈 집합으로 흡수하라.

**`overridesJson` 형식** — `날짜 → 객체` 맵의 JSON. 예:

```json
{"2026-08-19":{"title":"변경된 제목","startDate":"2026-08-19T14:00","hasAlarm":true}}
```

- 키는 **발생 원본 날짜**다. **override는 발생 날짜를 옮기지 않는다** — 시각만 바꾼다.
- `title`이 있으면 제목을 대체, 없으면 마스터 제목.
- `startDate`가 있으면 **시각 부분만** 가져오고 날짜 부분은 항상 발생일로 유지한다.
- `JSON.parse` 실패 시 빈 맵으로 흡수하라.

**시간지정 발생의 `startDate` 계산** (`EventRecurrence.kt`의 `effectiveTimeStart`와 동일):

```
source   = override?.startDate (길이 >= 10일 때) ?? event.startDate
timePart = source.length > 10 ? source.slice(10) : ''
결과      = 발생일(YYYY-MM-DD) + timePart
```

즉 날짜 부분만 갈아끼우고 시각·offset 문자열은 문자 그대로 보존한다. `Date` 객체로 파싱했다가 다시 포맷하지 마라 — offset 정보가 유실된다.

종일 일정(`isAllDay === true`)의 `startDate`는 발생일 문자열 그 자체다.

### 3. `web/types/models.ts` — 필드 추가

`LSyncEvent`에 `exdatesJson?: string | null`, `overridesJson?: string | null` 추가. `rrule`은 이미 선언돼 있다.

### 4. `web/hooks/useEvents.ts` / `web/app/schedule/page.tsx` — 전개 연결

- `useEvents`는 지금처럼 마스터 이벤트 목록을 반환한다(전개는 화면이 범위를 알고 하는 게 맞다). 단, 정렬 기준이 전개 후에는 의미가 약해지므로 필요한 만큼만 조정하라.
- `schedule/page.tsx`에서 **표시 중인 달(`year`, `month`)의 1일~말일 범위로 `expandEvents`를 호출**하고, 그 결과로:
  - `eventDates` 집합을 만든다(달력 그리드의 점 표시) — 반복 일정이 매 발생일마다 점으로 찍혀야 한다.
  - 선택된 날짜의 일정 목록도 전개 결과에서 뽑는다.
- 전개는 `useMemo`로 감싸 `[events, year, month]`에 의존시켜라.
- **일정 삭제 UI 처리** — 전개 결과의 `masterId`는 마스터 Event의 id다. 기존 `deleteEvent(id)`는 마스터 전체를 tombstone 처리하므로, 반복 일정의 발생 하나를 지우려는 사용자에게는 파괴적이다. 이 step에서는 **`isRecurring === true`인 발생에 대해 삭제 버튼을 비활성화하거나 숨겨라.** 이유: 범위 삭제(단건/이후/전체)는 앱에만 있는 기능이고 웹에 구현하는 것은 이 phase의 범위 밖이다. 마스터 전체를 조용히 지우는 것이 훨씬 나쁘다.

## Acceptance Criteria

```bash
cd web && pnpm build
```

- 타입 에러 없이 빌드 성공.
- `rrule`이 의존성에 추가되어 있다:

```bash
cd web && grep -n "rrule" package.json
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `EventRecurrenceTest.kt`의 케이스 중 최소 아래 4가지를 손으로 대조하라(전개 함수에 직접 값을 넣어 결과를 확인):
   - 비반복 일정이 범위 안/밖일 때 1개/0개
   - `FREQ=WEEKLY` 마스터를 한 달 범위로 전개했을 때 발생 수
   - `exdates`에 든 날짜가 결과에서 빠지는가
   - `overrides`의 `title`이 해당 발생에만 적용되고 **날짜는 그대로**인가
3. **타임존 확인** — 시스템 타임존이 KST(UTC+9)인 환경에서 `2026-08-01` 시작 주간 반복을 전개했을 때 결과 날짜가 하루 밀리지 않는지 확인하라. 이것이 이 step의 가장 흔한 실패 지점이다.
4. 아키텍처 체크리스트:
   - 종일 일정의 Floating Time 의미가 유지되는가? (타임존 변환 없음)
   - Android 코드를 건드리지 않았는가?
5. 결과에 따라 `phases/22-web-catchup/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **RRULE 파서를 직접 구현하지 마라.** 이유: 앱의 dmfs lib-recur와 갈라지면 같은 일정이 두 클라이언트에서 다른 날짜에 뜬다.
- **`Date` 객체를 로컬 타임존으로 왕복시키지 마라.** 이유: KST에서 종일 일정이 하루 밀린다. 이것은 CLAUDE.md의 CRITICAL 규칙(Floating Time, 타임존 변환 금지) 위반이다.
- **웹에 반복 일정 생성/편집 UI를 추가하지 마라.** 이유: 이 step은 **읽기-전개만** 다룬다. 생성·범위 편집은 앱의 기능이다.
- **반복 발생에 대해 `deleteEvent`를 그대로 노출하지 마라.** 이유: 마스터 전체가 삭제된다.
- **Android(`app/`) 코드를 수정하지 마라.**
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
