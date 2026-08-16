# Step 3: web-report-page

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/UI_GUIDE.md` — 디자인 시스템
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — 앱 리포트의 집계 정의(웹이 맞춰야 할 수치)
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — 앱 리포트 화면의 구성·톤
- `web/lib/finance.ts` — **step 1 산출물.** `financeTotals` / `topExpenseCategories`를 재사용한다.
- `web/lib/recurrence.ts` — **step 2 산출물.** `expandEvents`를 재사용한다.
- `web/hooks/useEvents.ts`, `web/hooks/useTodos.ts`, `web/hooks/useFinance.ts`
- `web/app/finance/page.tsx` — 월 이동(prevMonth/nextMonth) 패턴 참고
- `web/app/bible/page.tsx` — "앱에서 확인" 안내 문구의 톤 참고
- `web/components/BottomNav.tsx` — 수정 대상
- `web/app/globals.css` — 색 토큰(`--color-ls-*`)

**이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라. 특히 step 1·2의 함수를 그대로 재사용하고 집계 로직을 다시 구현하지 마라.**

## 배경

앱에는 월간 리포트 화면이 있다(일정·할일·가계부·통독 횡단 집계). 웹에는 없다. 이 step은 웹에 대응 화면을 만든다.

**통독(`reading_plan`·`memos`)은 Firestore에 동기화되지 않는다** — 로컬 전용이며 이는 확정된 설계다. 따라서 웹 리포트는 **일정·할일·가계부 3개 도메인만** 집계한다.

## 작업

### 1. `web/hooks/useReport.ts` 신규

`useEvents` / `useTodos` / `useFinance`를 조합해 지정한 달의 집계를 반환하는 훅.

```ts
export interface ReportData {
  yearMonth: string;        // "2026년 8월" 등 표시용 라벨
  todoTotal: number;
  todoCompleted: number;
  eventCount: number;       // 해당 월의 일정 "발생" 수 (반복 전개 결과)
  expense: number;          // 순지출
  income: number;
  topCategories: CategorySlice[];
}

export function useReport(uid: string, year: number, month: number): ReportData;
```

**집계 규칙 (앱 `ReportViewModel`과 1:1 대응):**

- `todoTotal` / `todoCompleted` — **`dueDate`가 해당 월에 속하는** 할일만 센다. `dueDate`가 없는 할일은 제외한다(앱 `TodoDao.observeByDueDateRange`가 `dueDate NOT NULL` 조건을 건다). tombstone(`deletedAt`) 제외는 `useTodos`가 이미 처리한다.
- `eventCount` — **step 2의 `expandEvents(events, from, to)` 결과의 개수.** 마스터 row 수가 아니다. 반복 일정은 발생마다 1건으로 센다.
- `expense` / `income` / `topCategories` — **step 1의 `financeTotals` / `topExpenseCategories`를 그대로 호출한다.** 집계식을 여기서 다시 쓰지 마라.
- `useFinance(uid, year, month)`는 이미 해당 월로 필터링해 반환하므로 그대로 넘기면 된다.

`from`/`to`는 해당 월의 1일과 말일(`YYYY-MM-DD`). 말일 계산은 `new Date(year, month, 0).getDate()` 패턴을 쓰되 문자열 조립 시 로컬 타임존 왕복을 하지 마라.

### 2. `web/app/report/page.tsx` 신규

- **월 이동** — `web/app/finance/page.tsx`의 `prevMonth`/`nextMonth` 패턴을 그대로 따른다. 단 **현재 월을 초과해 앞으로 이동할 수 없게 가드**한다(앱 리포트와 동일). 초과 시 다음달 버튼을 비활성 표시한다.
- **카드 구성** — 할일 완료율 / 일정 발생 수 / 가계부(순지출·수입) / 상위 지출 카테고리.
- **통독 안내** — 카드 하나 또는 한 줄로 "통독 현황은 앱에서 확인" 취지를 명시한다. `web/app/bible/page.tsx:20-22`의 문구 톤을 따라라. **가짜 통독 수치를 만들어 넣지 마라.**
- **디자인** — 다크 미니멀. `globals.css`의 토큰만 쓴다: 배경 `bg-ls-bg`, 카드 `bg-ls-card`, 테두리 `border-ls-line` / `border-ls-hair`, 본문 `text-ls-fg`, 보조 `text-ls-fg2` / `text-ls-fg3`, 강조 `text-ls-blue`.
- **`text-ls-red`(AccentRed)를 지출 금액에 쓰지 마라.** 이유: 프로젝트 전반의 확립된 디자인 의도다. 앱은 예산 **초과** 같은 경고 상태에만 red를 절제해서 쓴다. 평상시 지출은 강조색 없이 표시한다.
- 차트가 필요하면 CSS(`div` 폭 비율)나 인라인 SVG로 그린다. **차트 라이브러리를 추가하지 마라** — 앱도 Canvas 직접 드로잉으로 통일돼 있고, 웹 번들에 라이브러리를 들이는 것은 이 phase의 범위 밖이다.
- 다른 페이지와 동일하게 `'use client'`, `useAuth()`의 `uid`/`loading` 처리, `BottomNav` 하단 여백 관례를 따른다. 기존 페이지 하나를 열어 레이아웃 골격을 맞춰라.

### 3. `web/components/BottomNav.tsx` — 진입 추가

- `NAV` 배열에 `{ href: '/report', label: '리포트', icon: ChartIcon }` 추가.
- 아이콘은 파일 하단의 인라인 SVG 컴포넌트 관례(`HomeIcon`/`CalendarIcon`/`WalletIcon`/`BookIcon`)를 따라 `ChartIcon`을 같은 스타일(`viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `strokeWidth={1.6}`)로 새로 만든다.
- 항목이 5개가 되므로 `flex-1` 균등 분배에서 라벨이 깨지지 않는지 확인하라(최대 폭 480px 기준).

## Acceptance Criteria

```bash
cd web && pnpm build
```

- 타입 에러 없이 빌드 성공.
- 빌드 결과 라우트 목록에 `/report`가 포함된다(`pnpm build` 출력의 Route 표에서 확인).
- 집계 로직 중복 구현이 없다:

```bash
cd web && grep -rn "settlementGroupId" app/report hooks/useReport.ts
```

위 grep이 **아무것도 출력하지 않아야** 한다 — 정산 판정은 `lib/finance.ts` 안에만 있어야 한다.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `pnpm dev`로 띄워 `/report`에 들어가 월 이동이 동작하고, 미래 월로 넘어가지 않는지 확인한다.
3. 수치 대조 — 같은 달을 앱 리포트 화면과 웹 리포트에서 열어 **할일 완료율·일정 발생 수·순지출·수입이 일치**하는지 확인한다. 통독 항목만 웹에 없는 것이 정상이다.
4. 아키텍처 체크리스트:
   - step 1·2의 함수를 재사용했는가? 집계식을 복제하지 않았는가?
   - 지출 금액에 red를 쓰지 않았는가?
   - Android 코드를 건드리지 않았는가?
5. 결과에 따라 `phases/22-web-catchup/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **통독 지표를 웹 리포트에 넣지 마라.** 이유: `reading_plan`은 Firestore에 없다. 웹에서는 데이터 자체를 얻을 수 없으므로 어떤 수치든 거짓이 된다.
- **집계식을 다시 구현하지 마라.** `financeTotals` / `topExpenseCategories` / `expandEvents`를 호출하라. 이유: 사본이 늘어나면 정책 변경 시 갈라진다 — 이 phase가 고치고 있는 문제가 바로 그것이다.
- **차트 라이브러리를 추가하지 마라.**
- **지출 금액에 `text-ls-red`를 쓰지 마라.** 이유: 확립된 디자인 의도이며 red는 경고 상태 전용이다.
- **`web/lib/db.ts`에 쓰기 함수를 추가하지 마라.** 리포트는 읽기 전용 화면이다.
- **Android(`app/`) 코드를 수정하지 마라.**
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
