# Step 0: report-summary-text

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — § 월간 리포트
- `docs/PRD.md` — **2.4 정산(Settlement) 정책**
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — **`ReportUiState`가 이 step의 입력 타입이다. 수정 금지.**
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — 각 수치가 화면에서 어떤 문구·부호로 표시되는지 확인용
- `app/src/main/java/com/lsync/app/data/report/MonthlyAggregate.kt` — 집계 공식 단일 출처
- `app/src/test/java/com/lsync/app/ui/schedule/RecurrenceOptionsTest.kt` — 이 프로젝트의 단위 테스트 작성 스타일 참고

## 배경

월간 리포트(`ui/report`)를 외부로 내보내는 기능(Phase 21)의 첫 조각이다. 최종적으로 두 가지 공유 형태를 만든다:

1. **PNG 공유 카드** — step 1·2에서 구현
2. **텍스트 요약** — **이 step에서 구현**

텍스트 요약은 카카오톡·메모 앱에 붙여넣기 위한 것이다. `Intent.EXTRA_TEXT`로 전달되므로 순수 문자열이면 된다.

### 이 step의 핵심 설계 결정

**`ReportUiState`를 그대로 입력으로 받는다. 별도의 스냅샷/DTO 타입을 새로 만들지 마라.**

이유: `ReportUiState`에 이미 필요한 필드가 전부 있다. 병렬 타입을 만들면 필드가 추가될 때마다 두 곳을 동기화해야 하고, 안 하면 조용히 갈라진다(Phase 17에서 집계 공식 사본이 갈라진 것과 같은 실패 양상). `ReportUiState`는 Android 의존이 없는 순수 data class라 JVM 단위 테스트가 가능하다 — `RecurrenceOptionsTest.kt`가 이미 `ui` 패키지 클래스를 단위 테스트하고 있다.

## 작업

### 파일: `app/src/main/java/com/lsync/app/ui/report/ReportSummaryText.kt` (신규)

```kotlin
package com.lsync.app.ui.report

// 월간 리포트 텍스트 요약 — Intent.EXTRA_TEXT로 공유할 순수 문자열을 만든다.
// Android 의존 없음(Context·Resources 사용 금지) — 단위 테스트 가능해야 한다.
fun buildReportSummaryText(state: ReportUiState): String
```

출력 형식은 아래 뼈대를 따르되 세부 문구는 재량에 맡긴다. **화면(`ReportScreen.kt`)에 쓰인 라벨·부호와 일치시켜라** — 같은 달을 화면으로 보다가 텍스트로 공유했을 때 다른 말이 나오면 안 된다.

```
L-Sync 월간 리포트 · 2026년 8월

할 일   12/20 완료 (60%)
일정    34건
가계부  순지출 −₩1,234,000 / 수입 +₩2,000,000 / 잔액 +₩766,000
  1. 식비      −₩450,000
  2. 교통      −₩120,000
통독    23장 · 15일 읽음
```

**반드시 지킬 규칙:**

- **집계 공식을 새로 쓰지 마라.** `state.expense`는 이미 순지출(`(Σ EXPENSE − Σ 정산입금).coerceAtLeast(0)`), `state.income`은 정산 입금이 제외된 수입, `state.net`은 `income - expense`다. 여기서 다시 더하거나 빼지 마라. 이유: 집계 공식의 단일 출처는 `data/report/MonthlyAggregate.kt`이며, 사본이 생기면 화면·위젯·리포트의 숫자가 갈라진다.
- **부호 규칙은 화면과 동일하게:** 지출은 `−₩`, 수입은 `+₩`, 잔액은 `net >= 0`이면 `+₩`, 아니면 `−₩` + 절댓값.
- 금액은 `"%,d".format(...)` 천 단위 구분(기존 코드와 동일).
- 카테고리는 `state.topCategories`를 **그대로 순서대로** 쓴다. 재정렬·재계산 금지(이미 내림차순 상위 5개다).
- `category`가 공백이면 `"미분류"`로 표시(화면 `CategoryBars`와 동일).
- **빈 달 처리:** `todoTotal == 0`, `eventCount == 0`, `topCategories`가 비어 있는 등의 경우에도 예외 없이 문자열이 나와야 한다. 완료율은 `state.todoCompletionRate`를 쓰고(0 나눗셈은 이미 가드됨), 카테고리 목록이 비면 그 블록을 통째로 생략한다.
- 헤더의 연·월은 `state.yearMonth`(java.time.YearMonth)에서 만든다. **`Locale`에 의존하는 월 이름 포매팅(`DateTimeFormatter.ofPattern("MMMM")` 등)을 쓰지 마라.** 이유: 기기 로케일에 따라 결과가 달라져 테스트가 깨진다. `"%d년 %d월".format(year, monthValue)` 형태로 직접 조립하라.

### 파일: `app/src/test/java/com/lsync/app/ui/report/ReportSummaryTextTest.kt` (신규)

**테스트는 최소한으로 유지한다. 3~4 케이스면 충분하다.** 문자열 조립 로직이라 회귀 위험이 낮다. 전체 출력을 통째로 문자열 비교하지 말고(문구를 조금만 다듬어도 깨진다), **핵심 값이 들어 있는지**를 검증하라.

권장 케이스:

1. **정상 달** — 완료율·건수·순지출 금액 문자열이 결과에 포함되는지
2. **빈 달**(전 필드 기본값) — 예외 없이 문자열이 반환되고, 카테고리 블록이 없는지
3. **잔액 음수** — `−₩` 부호가 붙고 절댓값이 표시되는지
4. **카테고리 공백** — `"미분류"`로 치환되는지

`ReportUiState`와 `CategorySlice`(= `data.report.CategoryAmount`)를 직접 생성해 넣으면 된다. Repository·Hilt·Robolectric 불필요.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. **`testDebugUnitTest`는 신규 테스트를 포함해 전부 통과해야 한다** — 기존 `EventRecurrenceTest`, `PaymentNotificationParserTest`, `RecurrenceOptionsTest`가 깨지면 안 된다.
2. `buildReportSummaryText`가 `Context`·`Resources`·`R.string`·`android.*`를 하나도 참조하지 않는지 확인하라. 참조하면 단위 테스트가 불가능해진다.
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (신규 파일은 `ui/report/`)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
   - 집계 공식을 재작성하지 않고 `ReportUiState`의 값을 그대로 썼는가?
4. 결과에 따라 `phases/21-report-export/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`ReportSummarySnapshot` 같은 신규 DTO/스냅샷 타입을 만들지 마라.** 이유: `ReportUiState`의 필드를 복제한 병렬 타입이 되어 이중 관리가 발생한다.
- **`ReportUiState`·`ReportViewModel`·`ReportScreen`을 수정하지 마라.** 이 step은 신규 파일 2개만 추가한다.
- **집계·정산 공식을 다시 계산하지 마라.** `expense`/`income`/`net`/`topCategories`는 이미 확정된 값이다.
- **CSV·PNG·파일 저장·Intent를 건드리지 마라.** 이 step은 문자열 생성까지다. 공유 진입점은 step 2다.
- **`String.format`에 `Locale`을 넘기는 것 외의 로케일 의존 포매팅을 쓰지 마라.** 이유: 기기 로케일에 따라 테스트가 비결정적으로 깨진다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
