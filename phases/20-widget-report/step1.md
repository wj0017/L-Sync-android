# Step 1: report-widget

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — §6 홈 위젯, §9 월간 리포트
- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — **디자인·구조의 기준. 색 상수, 카드 테두리 관례, `formatAmount`, `loadWidgetState` 패턴을 그대로 따른다. 수정 금지.**
- `app/src/main/java/com/lsync/app/ui/widget/WidgetState.kt` — 상태 클래스 관례(`empty()` 팩토리)
- `app/src/main/java/com/lsync/app/ui/widget/WidgetEntryPoint.kt` — **이미 `todoDao/eventDao/financeDao/readingPlanDao/authRepository`를 노출한다. 수정 불필요.**
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidgetReceiver.kt`
- `app/src/main/java/com/lsync/app/ui/widget/WidgetRefreshHelper.kt` — 수정 대상
- `app/src/main/res/xml/lsync_widget_info.xml`
- `app/src/main/AndroidManifest.xml` — 위젯 receiver 등록 위치
- `app/src/main/java/com/lsync/app/data/report/MonthlyAggregate.kt` — **step 0 산출물. 반드시 재사용한다.**
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` / `ReadingPlanDao.kt` — step 0에서 suspend 조회 메서드가 추가되어 있다.
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — 지표 정의(무엇을 세는지)
- `app/src/main/java/com/lsync/app/data/recurrence/EventRecurrence.kt` — `expandEvents`

**step 0에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경

앱에 월간 리포트 화면이 있지만 **앱을 열어야만 보인다.** 홈 화면에서 바로 보이면 실제로 쓰이게 된다.

기존 5×2 위젯(`LSyncWidget`)은 Row2가 106dp 고정에 4카드로 이미 포화 상태다. **건드리지 않고 별도의 4×2 위젯**을 추가한다.

## 작업

### 1. `ui/widget/ReportWidgetState.kt` 신규

`WidgetState.kt`의 관례(데이터 클래스 + `companion object { fun empty() }`)를 따른다.

```kotlin
data class ReportWidgetState(
    val yearMonth: String,     // 표시 라벨 (예: "8월 리포트" 또는 "2026.08")
    val todoTotal: Int,
    val todoCompleted: Int,
    val eventCount: Int,
    val expense: Long,         // 순지출
    val chaptersRead: Int,
    val daysRead: Int,
) {
    companion object { fun empty(): ReportWidgetState }
}
```

### 2. `ui/widget/ReportWidget.kt` 신규

`LSyncWidget`과 동일한 구조: `class ReportWidget : GlanceAppWidget()`, `provideGlance`에서 `loadState(context)` 후 `provideContent { ... }`.

**데이터 로드 (`LSyncWidget.loadWidgetState`의 패턴을 따른다):**

- `EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)`로 DAO를 얻는다. **`WidgetEntryPoint`는 수정하지 마라 — 필요한 5개가 이미 노출돼 있다.**
- `authRepository().currentUserId ?: return ReportWidgetState.empty()` — **미로그인 시 빈 상태.** userId를 하드코딩하지 마라.
- 이번 달 범위: Floating Date 문자열 `"YYYY-MM-01"` ~ `"YYYY-MM-{말일}"`. `LSyncWidget`이 쓰는 문자열 조립 방식을 따르되, 말일은 `YearMonth.lengthOfMonth()`로 정확히 구하라(`LSyncWidget`은 `-31`로 넉넉히 잡는 방식인데, 리포트는 건수를 세므로 정확한 말일을 쓴다).

**지표 정의 (`ReportViewModel`과 동일해야 한다 — 어긋나면 위젯과 리포트 화면 숫자가 다르다):**

| 지표 | 소스 | 규칙 |
|------|------|------|
| `todoTotal` / `todoCompleted` | `todoDao().getByDueDateRange(from, to)` (step 0 추가분) | 마감일 있는 Todo만. 완료 수는 `count { it.isCompleted }` |
| `eventCount` | `eventDao().getForExpansion(from, to)` → `expandEvents(masters, from, to).size` | **반복 마스터를 전개한 발생 수.** 행 수를 세지 마라 |
| `expense` | `financeDao().getAllByDateRange(userId, from, to)` → `financeTotals(items).expense` | **step 0의 `financeTotals`를 호출하라.** 정산 공식을 다시 쓰지 마라 |
| `chaptersRead` / `daysRead` | `readingPlanDao().getReadInRange(from, to)` (step 0 추가분) | 장 수 = 행 수, 일 수 = `map { it.date }.distinct().size` |

**UI:**

- `LSyncWidget.kt`의 카드 테두리 관례를 그대로 따른다 — **중첩 Column**(outer `background(BgCardBorder #383838)` + `cornerRadius(12.dp)` + `padding(1.dp)`, inner `background(BgCard #161616)` + `cornerRadius(11.dp)`).
- 색·타이포도 동일 값을 쓴다: `FgPrimary`, `FgSecondary`(80%), `FgTertiary`(60%), `AccentBlue`. 금액은 `formatAmount`(`String.format("%,d", ...)`) 형식.
- **지출 수치에 `AccentRed`를 쓰지 마라.** 이유: 체감 부담 완화 = 프로젝트 전반의 확립된 디자인 의도. 앱 리포트 화면·대시보드 모두 지출에 red를 쓰지 않는다.
- `LSyncWidget.kt`의 색 상수는 `private`이므로 그 파일에서 import할 수 없다. **`ReportWidget.kt` 안에 동일한 값으로 다시 선언하라**(LSyncWidget.kt를 수정해 public으로 바꾸지 마라 — 검증된 파일을 건드리지 않는다).
- 4×2 크기에 맞춰 지표 4개(할일 완료율 / 일정 / 지출 / 통독)를 2×2 그리드로 배치한다. 좁으므로 텍스트는 `maxLines = 1`.

### 3. `ui/widget/ReportWidgetReceiver.kt` 신규

`LSyncWidgetReceiver.kt`와 동일한 형태:

```kotlin
class ReportWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ReportWidget()
}
```

### 4. `res/xml/report_widget_info.xml` 신규

`lsync_widget_info.xml`을 본떠 만들되 **4×2**로:

- `android:targetCellWidth="4"`, `android:targetCellHeight="2"`
- `minWidth`/`minHeight`는 4×2에 맞게 조정
- `android:initialLayout="@layout/widget_loading"` — **기존 레이아웃을 재사용한다.** 새로 만들지 마라.
- `android:updatePeriodMillis`, `android:widgetCategory="home_screen"`는 기존과 동일

### 5. `AndroidManifest.xml` — receiver 등록

기존 `LSyncWidgetReceiver` 블록 바로 아래에 같은 형식으로 `ReportWidgetReceiver`를 등록한다(`android:exported="true"`, `APPWIDGET_UPDATE` intent-filter, `@xml/report_widget_info` meta-data).

### 6. `WidgetRefreshHelper` 확장

현재 `requestUpdate()`가 `LSyncWidget`만 갱신한다. `ReportWidget`도 함께 갱신하도록 확장한다. 기존 호출부(Schedule/Finance/HomeViewModel)는 **수정하지 않는다** — 헬퍼 내부만 바꾸면 전부 따라온다.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

집계 사본이 늘지 않았는지 확인:

```powershell
Select-String -Path app\src\main\java\com\lsync\app\ui\widget\ReportWidget.kt -Pattern "settlementGroupId"
```

위 명령이 **아무것도 출력하지 않아야** 한다 — 정산 판정은 `MonthlyAggregate.kt` 안에만 있어야 한다.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. `ReportWidget`의 지표 계산을 `ReportViewModel`의 대응 지표와 대조한다. 특히 `eventCount`가 `expandEvents(...).size`인지(행 수가 아닌지) 확인하라.
3. 미로그인 경로를 확인한다 — `currentUserId == null`일 때 크래시 없이 `empty()`가 반환되는가?
4. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`ui/widget/`)
   - CLAUDE.md CRITICAL 규칙 — userId는 `AuthRepository.currentUserId`인가? 날짜는 Floating Time 문자열인가?
   - Room이 SSOT인가? (위젯이 Firestore를 직접 읽지 않는가 — DAO만 써야 한다)
5. 결과에 따라 `phases/20-widget-report/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`LSyncWidget.kt` / `WidgetState.kt` / `lsync_widget_info.xml`을 수정하지 마라.** 이유: 기존 5×2 위젯은 Row2 106dp 고정으로 레이아웃이 빡빡하게 맞춰져 있고 동작 검증이 끝났다. 이 phase는 **별도 위젯 추가**이지 기존 위젯 변경이 아니다.
- **`WidgetEntryPoint`를 수정하지 마라.** 필요한 DAO 5개가 이미 노출돼 있다.
- **정산 집계식을 `ReportWidget.kt` 안에 다시 쓰지 마라.** step 0의 `financeTotals`를 호출하라. 이유: 이 step의 존재 이유가 5번째 사본을 막는 것이다.
- **지출 수치에 `AccentRed`를 쓰지 마라.**
- **차트 라이브러리를 추가하지 마라.** Glance는 제한된 컴포넌트만 지원하며, 프로젝트는 Canvas/기본 컴포넌트 직접 조합으로 통일돼 있다.
- **위젯 탭 동작(딥링크)을 구현하지 마라.** 이유: step 2의 작업이다. 이 step에서는 표시까지만 한다.
- **Firestore를 직접 읽지 마라.** Room DAO만 쓴다(Offline-First, Room이 SSOT).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
