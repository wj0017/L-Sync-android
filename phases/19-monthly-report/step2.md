# Step 2: report-screen

도메인 횡단 **월간 리포트** 화면(Compose)을 만든다. Step 1에서 만든 `ReportViewModel`을 구독해 일정·할일·가계부·통독 지표를 한 화면에 카드로 보여준다. 진입(NavGraph·홈 버튼) 연결은 다음 step에서 한다.

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 디자인 시스템을 파악하라:

- `docs/UI_GUIDE.md` — 다크 미니멀 디자인 시스템, 컴포넌트 관례
- `docs/ARCHITECTURE.md` — "통계 대시보드" 절(차트 라이브러리 미사용 = Canvas 직접 드로잉)
- `app/src/main/java/com/lsync/app/ui/report/ReportViewModel.kt` — **Step 1 산출물**. `ReportUiState`(todoTotal/todoCompleted/todoCompletionRate, eventCount, expense/income/net/topCategories, chaptersRead/daysRead), `previousMonth()`/`nextMonth()` 확인
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboard.kt` — **차트·카드 레퍼런스**. Canvas로 막대/카테고리 차트를 그리는 방식, 카드 컨테이너 스타일, 정산 색 규칙(지출에 AccentRed 미사용)을 그대로 따르라
- `app/src/main/java/com/lsync/app/ui/search/SearchScreen.kt` — 상단 바 + `onBack` 콜백 패턴(이 화면도 `onBack`을 받는 별도 화면이다)
- `app/src/main/java/com/lsync/app/ui/home/HomeScreen.kt` — 카드/Row/Text 스타일, 폰트(Pretendard, Instrument Serif) 사용 관례, 금액 표기 방식
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt` — 색 토큰: `BgPrimary`/`BgSecondary`/`BgCard`/`BgElevated`, `FgPrimary`/`FgSecondary`/`FgTertiary`/`FgDisabled`, `AccentBlue`/`AccentBlue20`/`AccentGreen`/`AccentRed`/`AccentRed80`, `HairlineWhite`
- `app/src/main/java/com/lsync/app/ui/theme/Type.kt` — 폰트(Pretendard, Instrument Serif Italic)
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` — 카테고리 표시명(상위 카테고리 라벨)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

`app/src/main/java/com/lsync/app/ui/report/ReportScreen.kt`를 만든다.

```kotlin
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // ...
}
```

화면 구성(위→아래, `LazyColumn` 또는 스크롤 `Column`):

1. **상단 바:** 뒤로가기(`onBack`) + 화면 제목("리포트" 등).

2. **월 선택 헤더:** `◀  2026년 6월  ▶`. 좌/우 화살표가 `viewModel.previousMonth()`/`nextMonth()` 호출. 미래 월로 못 가는 것은 ViewModel이 가드하므로 UI는 호출만 한다(다음 달 버튼을 현재 월에서 비활성/흐리게 표시하면 더 좋다).

3. **할일 카드:** 완료율을 시각화(예: 가로 진행바 또는 도넛/링). `"{todoCompleted}/{todoTotal} 완료 · {rate}%"`. 진행바 색은 `AccentBlue` 계열. `todoTotal == 0`이면 "마감일 있는 할 일 없음" 등 빈 상태.

4. **일정 카드:** `eventCount`를 큰 숫자로. "이달 일정 N건".

5. **가계부 카드:** 순지출/수입/잔액(`net`) 요약 + 상위 카테고리(`topCategories`) 막대. `FinanceDashboard.kt`의 카테고리 막대 드로잉을 재사용/참고. 금액 기호는 Instrument Serif Italic 관례를 따르라.

6. **통독 카드:** `"{chaptersRead}장 · {daysRead}일 읽음"`. 간단한 수치 카드.

### 디자인 규칙(반드시 준수)

- **차트 라이브러리 금지.** 막대·링 등은 `Canvas`로 직접 그려라(ARCHITECTURE.md "통계 대시보드"). `FinanceDashboard.kt`의 드로잉을 참고하라.
- **지출 수치/막대에 `AccentRed`를 쓰지 마라.** 이유: 체감 부담 완화 = 디자인 의도(ARCHITECTURE.md). 지출은 `FgPrimary`/`FgSecondary` 또는 중립색으로. `AccentRed`는 경고(예산 초과 등)에만.
- 다크 미니멀: 배경 `BgPrimary`, 카드 `BgCard`, 테두리 `HairlineWhite`, 본문 `FgPrimary`/`FgSecondary`.
- 폰트: 본문/UI는 Pretendard, 금액 기호는 Instrument Serif Italic(`Type.kt`/`HomeScreen.kt` 관례 따름).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (화면은 `ui/report/`)
   - CLAUDE.md CRITICAL 규칙 위반 없는가? — **`collectAsState()`만 사용**했는가(`collectAsStateWithLifecycle` 금지)? UI가 ViewModel의 상태만 구독하고 Firestore/Repository를 직접 만지지 않는가?
   - 차트 라이브러리를 추가하지 않고 Canvas로 그렸는가?
   - 지출에 `AccentRed`를 쓰지 않았는가?
3. 결과에 따라 `phases/19-monthly-report/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "ReportScreen 생성, 카드 구성 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `collectAsStateWithLifecycle`를 사용하지 마라. `collectAsState()`만 사용하라. 이유: CLAUDE.md CRITICAL.
- 차트 라이브러리(MPAndroidChart, Vico 등)를 의존성에 추가하지 마라. 이유: 프로젝트는 Canvas 직접 드로잉을 관례로 한다(ARCHITECTURE.md).
- 지출 수치/막대에 `AccentRed`를 쓰지 마라. 이유: 체감 부담 완화 디자인 의도.
- `NavGraph.kt`나 `HomeScreen.kt`를 수정하지 마라. 이유: 화면 진입 연결은 다음 step의 범위다. 이 step은 `ReportScreen` Composable만 만든다.
- `ReportViewModel`의 로직을 바꾸지 마라. 이유: 집계는 step 1에서 확정됐다. 화면은 상태를 그리기만 한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
