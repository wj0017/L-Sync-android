// 반복 일정(Event)을 iCal식 "읽기-전개"로 다루는 웹 엔진.
// 앱의 data/recurrence/EventRecurrence.kt와 1:1 대응해야 한다 —
// 같은 rrule을 두 클라이언트가 다른 날짜로 전개하면 재현이 어려운 데이터 불일치가 된다.
//
// 규칙(핵심, EventRecurrence.kt와 동일):
//  - 종일 일정은 Floating Date(YYYY-MM-DD). 타임존 변환 금지.
//  - 시간지정 발생은 날짜 부분만 발생일로 치환하고 시각·offset 문자열은 그대로 보존한다.
//  - override 키는 "발생 원본 날짜". override는 발생 날짜를 옮기지 않는다(시각만 변경).
//  - 무한 rrule 방어(범위 상한 + MAX_ITERATIONS). 파싱 실패는 예외 없이 흡수한다.
//
// RRULE 파싱은 rrule 패키지에 위임한다(직접 파서 구현 금지 — 앱의 dmfs lib-recur와 갈라짐 방지).
// Firebase·React에 의존하지 않는 순수 계산 모듈.

import { RRule } from 'rrule';
import { LSyncEvent } from '@/types/models';

const MAX_ITERATIONS = 10_000;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export interface EventOccurrence {
  masterId: string;     // 원본 LSyncEvent.id
  date: string;         // YYYY-MM-DD — 발생 날짜(전개 키)
  startDate: string;    // 유효 startDate. 시간지정이면 날짜 부분만 발생일로 치환, 시각·offset 보존
  title: string;        // 유효 제목(override 반영)
  isAllDay: boolean;
  isOverridden: boolean; // 이 발생에 override가 적용됐는가
  isRecurring: boolean;  // rrule에서 나온 발생인가(단발 false)
}

export interface EventOverride {
  title?: string;
  startDate?: string; // 시각만 바꿈. 날짜 부분은 발생일과 동일해야 함
  hasAlarm?: boolean;
}

// ── 전개 ──────────────────────────────────────────────────────────────────────

// 단일 이벤트를 [from, to] (둘 다 inclusive, YYYY-MM-DD)로 전개.
// 비반복(rrule 없음): startDate 날짜가 범위 안이면 1개(override/exdate 무시), 아니면 0개.
// 반복: rrule 전개 → 범위 필터 → exdates 제외 → overrides 적용.
export function expandEvent(event: LSyncEvent, from: string, to: string): EventOccurrence[] {
  const rrule = event.rrule?.trim();
  const startDate = event.startDate ?? '';

  if (!rrule) {
    const date = startDate.slice(0, 10);
    return date >= from && date <= to
      ? [singleOccurrence(event, date, false)]
      : [];
  }

  const exdates = parseExdates(event.exdatesJson);
  const overrides = parseOverrides(event.overridesJson);

  // Floating Date를 UTC 자정으로 다룬다 — 앱의 LocalDate.toEpochDay() * 86400000L과 동일.
  // 로컬 타임존(KST)으로 왕복시키면 종일 일정이 하루 밀린다.
  const fromDate = utcDateOrNull(from);
  const toDate = utcDateOrNull(to);
  const dtstart = utcDateOrNull(startDate.slice(0, 10));
  if (!fromDate || !toDate || !dtstart) return [];
  if (toDate.getTime() < fromDate.getTime()) return [];

  const result: EventOccurrence[] = [];
  try {
    const options = RRule.parseString(stripRrulePrefix(rrule));
    const rule = new RRule({ ...options, dtstart });
    // between은 [from, to]로 이미 유계지만, 초/분 단위 rrule의 폭주를 MAX_ITERATIONS로 한 번 더 막는다.
    const dates = rule.between(fromDate, toDate, true, (_d, len) => len < MAX_ITERATIONS);
    for (const d of dates) {
      const dateStr = d.toISOString().slice(0, 10);
      if (exdates.has(dateStr)) continue;
      result.push(buildOccurrence(event, dateStr, overrides[dateStr]));
    }
  } catch {
    // 깨진 rrule은 빈/부분 결과로 흡수 (달력 화면 전체가 죽는 것을 방지)
  }
  return result;
}

// 여러 이벤트 전개 후 평탄화.
export function expandEvents(events: LSyncEvent[], from: string, to: string): EventOccurrence[] {
  return events.flatMap((e) => expandEvent(e, from, to));
}

// ── 발생 빌더 ─────────────────────────────────────────────────────────────────

function singleOccurrence(event: LSyncEvent, date: string, isRecurring: boolean): EventOccurrence {
  return {
    masterId: event.id,
    date,
    startDate: event.isAllDay ? date : effectiveTimeStart(event, date, undefined),
    title: event.title,
    isAllDay: event.isAllDay,
    isOverridden: false,
    isRecurring,
  };
}

function buildOccurrence(
  event: LSyncEvent,
  date: string,
  override: EventOverride | undefined,
): EventOccurrence {
  return {
    masterId: event.id,
    date,
    startDate: event.isAllDay ? date : effectiveTimeStart(event, date, override),
    title: override?.title ?? event.title,
    isAllDay: event.isAllDay,
    isOverridden: override !== undefined,
    isRecurring: true,
  };
}

// 시간지정 발생의 startDate: 날짜는 발생일로 치환, 시각·offset 문자열은 보존.
// override.startDate가 있으면 시각 부분만 그것으로 교체(날짜는 항상 발생일).
// Date로 파싱했다 다시 포맷하면 offset 정보가 유실되므로 문자열 조작만 한다.
function effectiveTimeStart(
  event: LSyncEvent,
  date: string,
  override: EventOverride | undefined,
): string {
  const ov = override?.startDate;
  const source = ov !== undefined && ov.length >= 10 ? ov : (event.startDate ?? '');
  const timePart = source.length > 10 ? source.slice(10) : '';
  return date + timePart;
}

function stripRrulePrefix(rrule: string): string {
  return rrule.startsWith('RRULE:') ? rrule.slice('RRULE:'.length) : rrule;
}

// YYYY-MM-DD → UTC 자정 Date. 형식이 어긋나거나 롤오버(2024-02-31)면 null.
function utcDateOrNull(s: string): Date | null {
  if (!DATE_RE.test(s)) return null;
  const y = Number(s.slice(0, 4));
  const m = Number(s.slice(5, 7));
  const d = Number(s.slice(8, 10));
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (Number.isNaN(dt.getTime())) return null;
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return null;
  return dt;
}

// ── exdates / overrides JSON 헬퍼 ─────────────────────────────────────────────

// 문자열 배열의 JSON. 예: ["2026-08-19","2026-08-26"]
export function parseExdates(json: string | null | undefined): Set<string> {
  if (!json) return new Set();
  try {
    const parsed: unknown = JSON.parse(json);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((v): v is string => typeof v === 'string'));
  } catch {
    return new Set();
  }
}

// 맵의 JSON. 예: {"2026-08-19":{"title":"변경","startDate":"2026-08-19T14:00","hasAlarm":true}}
export function parseOverrides(json: string | null | undefined): Record<string, EventOverride> {
  if (!json) return {};
  try {
    const parsed: unknown = JSON.parse(json);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const result: Record<string, EventOverride> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (value === null || typeof value !== 'object' || Array.isArray(value)) continue;
      const obj = value as Record<string, unknown>;
      result[key] = {
        title: typeof obj.title === 'string' ? obj.title : undefined,
        startDate: typeof obj.startDate === 'string' ? obj.startDate : undefined,
        hasAlarm: typeof obj.hasAlarm === 'boolean' ? obj.hasAlarm : undefined,
      };
    }
    return result;
  } catch {
    return {};
  }
}
