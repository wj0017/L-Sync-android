# Step 1: report-share-card

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — § 월간 리포트
- `docs/UI_GUIDE.md` — 다크 미니멀 디자인 시스템
- `app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt` — **이 step의 주 수정 대상. `TodoCard`/`EventCard`/`FinanceCard`/`ReadingCard`/`ReportCard`/`SectionHeader`가 전부 `private` top-level composable이다.**
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — `ReportUiState`
- `app/src/main/java/com/lsync/app/ui/report/ReportSummaryText.kt` — **step 0 산출물.** 텍스트 요약 빌더(이 step에서는 수정하지 않는다)
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt`, `Type.kt` — 팔레트·폰트
- `app/src/main/java/com/lsync/app/MainActivity.kt` — `ComponentActivity` 여부 확인용

**step 0에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.**

## 배경 — 반드시 알아야 할 제약

이 step의 목표는 **월간 리포트를 PNG 비트맵으로 렌더링**하는 것이다(공유 진입점은 step 2).

이 프로젝트의 Compose BOM은 `2024.05.00`(Compose 1.6.7)이다. **`rememberGraphicsLayer()` / `GraphicsLayer.toImageBitmap()`은 Compose 1.7.0부터라 사용할 수 없다.** 라이브러리 버전을 올리지 마라(범위 밖이며 전면 회귀 위험).

대신 **오프스크린 `ComposeView`를 직접 measure/layout한 뒤 소프트웨어 `Canvas`에 그리는 방식**을 쓴다. 이 방식을 택한 이유:

- 기존 리포트 카드 Composable을 **그대로 재사용**하므로 화면과 공유 이미지가 갈라지지 않는다. `android.graphics.Paint`로 카드를 다시 그리면 리포트 항목이 추가될 때마다 두 곳을 고쳐야 하고, 안 고치면 조용히 어긋난다.
- AC(`assembleDebug`/`lintDebug`)로는 픽셀 결과를 검증할 수 없다. 이 방식의 실패는 "빈 이미지 / 잘린 이미지"처럼 **눈에 띄게** 나타나므로 step 2의 미리보기 다이얼로그에서 즉시 발견된다.

## 작업

### 1. `ReportScreen.kt` — 공유용 카드 Composable 추가

같은 파일에 **`internal fun ReportShareCard(state: ReportUiState)`** 를 추가한다.

**반드시 같은 파일(`ReportScreen.kt`)에 둬라.** 이유: `TodoCard`·`EventCard`·`FinanceCard`·`ReadingCard`·`SectionHeader`가 `private`이라 다른 파일에서 호출할 수 없다. **이들을 `internal`/`public`으로 승격하지 마라** — 검증된 화면 코드의 가시성을 넓히지 않는다.

레이아웃:

- 최상위는 **반드시 `Column`**(+ `verticalScroll` 없이). **`LazyColumn`을 쓰지 마라.**
  이유: 이 Composable은 높이 `UNSPECIFIED` MeasureSpec으로 측정된다. 스크롤 가능한 컴포넌트를 무한 높이 제약으로 측정하면 Compose가 `IllegalStateException("Vertically scrollable component was measured with an infinity maximum height constraints")`를 던진다. `ReportScreen`의 `LazyColumn`을 그대로 재사용할 수 없는 이유가 이것이다.
- `Modifier.fillMaxWidth().background(BgPrimary)` + 좌우 패딩.
- 구성: **헤더**(`L-Sync 월간 리포트` + `state.yearMonth`의 연·월) → `SectionHeader("할 일")` + `TodoCard` → `SectionHeader("일정")` + `EventCard` → `SectionHeader("가계부")` + `FinanceCard` → `SectionHeader("성경 통독")` + `ReadingCard` → **푸터**(작은 캡션, 예: `L-Sync`).
- 각 카드에 넘기는 인자는 `ReportScreen`의 `LazyColumn` 안에서 넘기는 것과 **완전히 동일해야 한다**(`state.todoTotal`, `state.todoCompletionRate`, `state.expense`, `state.income`, `state.net`, `state.topCategories`, `state.chaptersRead`, `state.daysRead`).
- 헤더의 연·월 포매팅은 **로케일 의존 포매터를 쓰지 마라**(`"%d년 %d월".format(...)` 형태로 직접 조립).

**`ReportScreen` 본체(`LazyColumn`)와 기존 카드 Composable의 시그니처·구현을 바꾸지 마라.** 이 step은 파일에 Composable 하나를 추가할 뿐이다.

### 2. `app/src/main/java/com/lsync/app/ui/report/ReportCardRenderer.kt` (신규)

```kotlin
package com.lsync.app.ui.report

// 오프스크린 ComposeView를 measure/layout 후 소프트웨어 Canvas에 그려 PNG용 Bitmap을 만든다.
// 실패(Activity 없음, 측정 실패 등) 시 null을 반환한다 — 호출자가 대체 경로(텍스트 공유)를 안내한다.
suspend fun renderReportCard(
    context: Context,
    state: ReportUiState,
    widthPx: Int = 1080,
): Bitmap?
```

구현 시 지켜야 할 것:

- **메인 스레드에서 실행해야 한다.** View measure/layout/draw는 메인 스레드 전용이다. 함수 안에서 `withContext(Dispatchers.Main)`로 감싸라. (비트맵 생성 자체는 무겁지 않다 — 1080×~2000 ARGB_8888 ≈ 8MB, 수백 ms.)
- **Activity Context가 필요하다.** `ComposeView`는 `ViewTreeLifecycleOwner`/`ViewTreeSavedStateRegistryOwner`가 있어야 컴포지션이 시작된다. 전달받은 `Context`를 `ContextWrapper.baseContext`를 따라 풀어 `Activity`를 찾아라. 못 찾으면 `null` 반환.
- `ComposeView`를 만들고 `setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)`를 지정한 뒤 `setContent { ... }`.
- **`setContent` 안에서 밀도를 고정하라:**
  ```kotlin
  CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 1f)) {
      LSyncTheme { ReportShareCard(state) }   // com.lsync.app.ui.theme.LSyncTheme
  }
  ```
  이유: 밀도를 고정하지 않으면 `widthPx=1080`이 기기 밀도에 따라 360dp/540dp 등으로 달라져 **기기마다 다른 이미지가 나온다.** `fontScale = 1f`도 함께 고정해 사용자의 글꼴 크기 설정이 공유 이미지를 흔들지 않게 한다.
- **컴포지션을 기다려라.** `activity.findViewById<ViewGroup>(android.R.id.content)`에 `LayoutParams(1, 1)` + `visibility = View.INVISIBLE`로 붙인 뒤, `kotlinx.coroutines.android.awaitFrame()`을 **2회** 호출해 컴포지션·레이아웃이 한 바퀴 돌게 한다(`kotlinx-coroutines-android`는 이미 의존성에 있다). 붙이지 않으면 컴포지션이 시작되지 않아 빈 비트맵이 나온다.
- 그다음 **직접 측정**한다:
  ```
  measure(MeasureSpec.makeMeasureSpec(widthPx, EXACTLY), MeasureSpec.makeMeasureSpec(0, UNSPECIFIED))
  layout(0, 0, measuredWidth, measuredHeight)
  ```
  `measuredHeight <= 0`이면 **곧바로 null을 반환하지 말고 `awaitFrame()`을 한 번 더 기다린 뒤 measure를 1회 재시도하라.** 그래도 0 이하면 `null` 반환. 이유: 컴포지션이 느린 기기에서 첫 두 프레임 안에 끝나지 않을 수 있고, 이 경우의 실패는 "빈 이미지"라 원인 파악이 어렵다.
- `Bitmap.createBitmap(w, h, ARGB_8888)` → `android.graphics.Canvas(bitmap)` → **배경을 먼저 칠하고**(`drawColor(0xFF0A0A0A.toInt())` = `BgPrimary`) → `composeView.draw(canvas)`.
  이유: 배경을 안 칠하면 투명 PNG가 되어 밝은 배경 메신저에서 흰 바탕에 흰 글씨로 보인다.
- **`finally` 블록에서 반드시 부모로부터 뷰를 제거하라**(`(view.parent as? ViewGroup)?.removeView(view)`). 누락하면 Activity에 보이지 않는 뷰가 계속 쌓이고 컴포지션이 살아남는다.
- 예외는 삼켜서 `null`을 반환하되, 무엇이 실패했는지 로그를 남겨라.

소프트웨어 캔버스 주의: `View.draw(softwareCanvas)`는 그림자/elevation을 제대로 렌더하지 못한다. **리포트 카드는 `background` + `border`만 쓰고 elevation을 쓰지 않으므로 문제가 없다.** `ReportShareCard`에 `Modifier.shadow`·`Card(elevation=...)`·`Modifier.blur`를 새로 도입하지 마라.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. **기존 단위 테스트가 깨지면 안 된다.**
2. **AC는 렌더 결과를 검증하지 못한다.** 코드를 다시 읽으며 아래를 직접 확인하라:
   - `ReportShareCard`의 최상위가 `Column`인가? (`LazyColumn`·`verticalScroll`이면 무한 높이 측정에서 크래시한다)
   - `ReportShareCard`가 기존 `private` 카드 composable을 호출만 하고, 그 시그니처를 바꾸지 않았는가?
   - `renderReportCard`가 ① Main 디스패처 ② Activity 탐색 실패 시 null ③ 뷰 attach → `awaitFrame()` ④ 수동 measure/layout ⑤ 배경 채색 ⑥ `finally`에서 removeView — 여섯 단계를 모두 갖췄는가?
   - `LocalDensity`가 고정되어 기기 밀도와 무관하게 같은 크기가 나오는가?
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (신규 파일은 `ui/report/`)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가? (`collectAsStateWithLifecycle` 금지 등)
   - Room이 SSOT인가? (이 step은 데이터 계층을 건드리지 않는다 — 건드렸다면 범위 이탈이다)
4. 결과에 따라 `phases/21-report-export/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`android.graphics.Paint`로 카드를 처음부터 다시 그리지 마라.** 이유: 화면과 공유 이미지가 이중 관리가 되어 갈라진다. 기존 Composable 재사용이 이 step의 핵심 설계 결정이다.
- **Compose·AGP·Kotlin 버전을 올리지 마라.** `rememberGraphicsLayer`를 쓰려고 BOM을 올리는 것은 전면 회귀 위험이 있고 이 task의 범위 밖이다.
- **`ReportScreen`의 기존 `private` composable을 `internal`/`public`으로 승격하지 마라.** 이유: 검증된 화면 코드의 가시성을 넓히면 외부에서 결합이 생겨 이후 변경이 어려워진다. `ReportShareCard`를 같은 파일에 두면 승격이 불필요하다.
- **`ReportScreen` 본체·`ReportViewModel`·`ReportUiState`를 수정하지 마라.** 이 step은 `ReportScreen.kt`에 composable 하나 추가 + 신규 파일 하나다.
- **`PixelCopy`나 창 캡처를 쓰지 마라.** 이유: 화면에 보이는 영역만 잡히고 상태바가 섞이며, 스크롤 위치에 결과가 좌우된다.
- **파일 저장·`FileProvider`·`Intent`를 건드리지 마라.** 이 step은 `Bitmap` 반환까지다. 공유 진입점은 step 2다.
- **`Modifier.shadow` / `elevation` / `Modifier.blur`를 `ReportShareCard`에 도입하지 마라.** 이유: 소프트웨어 캔버스에서 제대로 렌더되지 않는다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
