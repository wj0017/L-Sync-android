# Step 2: dashboard-ui

## 배경

이 task(`12-finance-dashboard`)는 "가계부 통계 대시보드"다.

**이전 Step들에서:**
- **Step 0**: `FinanceRepository.observeByDateRange(from, to)` Flow.
- **Step 1**: 대시보드 ViewModel — `monthly`(월별 추세), `categoryBreakdown`(카테고리별 순지출), `totalExpense`(순지출)/`totalIncome`/`totalReimbursed` 상태.

이 Step은 그 상태를 **다크 미니멀 디자인으로 시각화**하고, 진입점을 붙인다.

**중요:** 이 프로젝트에는 차트 라이브러리가 없다. 홈 위젯의 막대 차트처럼 **Compose `Canvas`/기본 컴포저블로 직접** 그린다(새 의존성 추가 금지).

## 읽어야 할 파일

먼저 아래 파일을 읽고 디자인 시스템·기존 시각화를 파악하라:

- `docs/UI_GUIDE.md` — 다크 미니멀, Pretendard, Instrument Serif Italic(금액 기호), ghost chip, **지출 금액 AccentRed 금지(지출=FgSecondary, 수입=AccentGreen)**, 그림자 금지, 위젯 막대 차트 참고
- `docs/ARCHITECTURE.md` — `collectAsState()`
- `CLAUDE.md` — 디자인 토큰(BgPrimary/BgCard/FgPrimary/AccentBlue/AccentGreen 등)
- `app/src/main/java/com/lsync/app/ui/finance/FinanceScreen.kt` — **수정 대상(진입점).** 기존 화면 구조·SummaryCard·카드 스타일을 본보기로.
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt` — 막대 차트를 Compose 외 환경에서 그린 방식(비율 계산 참고)
- Step 1의 대시보드 ViewModel/`DashboardUiState`

## 작업

### 1) 대시보드 Composable

`app/src/main/java/com/lsync/app/ui/finance/FinanceDashboard.kt`(또는 `FinanceScreen.kt` 내 private 섹션)에 대시보드 UI를 만든다. `viewModel()` 또는 hiltViewModel로 Step 1 ViewModel을 받고 `collectAsState()`로 상태 구독.

구성(재량이되 아래를 포함):
- **요약 카드:** 선택 기간 순지출/수입/정산 받음 합계. 금액은 Instrument Serif Italic 기호 + Pretendard 숫자(기존 SummaryCard 스타일).
- **월별 추세:** `monthly`를 막대 차트로(달별 순지출, 선택적으로 수입). Compose `Canvas` 또는 Row+Box 비율 막대. 최댓값 기준 정규화.
- **카테고리별 순지출:** `categoryBreakdown`을 상위 N개 가로 막대 또는 비율 리스트로(카테고리명 + 금액 + 비율 바).

### 2) 진입점

- `FinanceScreen`에서 대시보드로 가는 진입을 추가한다(예: 상단 토글/탭 "거래 | 통계", 또는 헤더 아이콘 버튼). 기존 화면을 크게 흔들지 말고, 거래 목록과 대시보드를 전환할 수 있는 가벼운 방식으로.

## 핵심 규칙 (반드시 지킬 것)

- **디자인 시스템 준수.** 라이트 모드 금지, Pretendard, 그림자 금지(테두리 HairlineWhite), **지출 금액에 AccentRed 사용 금지**(지출=FgSecondary 계열, 수입=AccentGreen). 새 색상 토큰 만들지 마라.
- **차트는 직접 그린다.** MPAndroidChart 등 외부 라이브러리를 `build.gradle`에 추가하지 마라. Compose 기본/`Canvas`로만.
- **`collectAsState()`만.**
- **빈/0 데이터 안전.** 거래가 없거나 최댓값이 0일 때 0으로 나누지 말고 빈 상태 UI를 보여라.
- 집계 로직을 UI에서 다시 만들지 마라. Step 1 상태를 그대로 렌더만.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처/디자인 체크리스트:
   - 대시보드가 Step 1 상태를 `collectAsState()`로 구독해 추세·카테고리·합계를 그리는가?
   - 차트가 외부 라이브러리 없이 Compose로 그려졌는가?
   - 지출에 AccentRed 미사용, Pretendard·다크 토큰 준수?
   - 0/빈 데이터에서 크래시·0 나눗셈이 없는가?
   - `FinanceScreen`에서 대시보드 진입이 가능한가?
3. 결과에 따라 `phases/12-finance-dashboard/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FinanceScreen에 통계 대시보드(월별 추세·카테고리별 순지출·합계) Compose 시각화 + 진입점 추가. 차트는 Canvas 직접 구현. dashboard end-to-end 완성"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 중단

## 금지사항

- ViewModel/Repository/DAO를 수정하지 마라. 이유: Step 0·1에서 완성됐다.
- 차트 라이브러리 의존성을 추가하지 마라. 이유: 프로젝트 정책상 직접 구현하며 번들 크기·일관성을 지킨다.
- 새 색상/디자인 토큰을 만들지 마라. 기존 `Color.kt`만.
- 기존 거래 목록/입력 시트 동작을 바꾸지 마라. 대시보드 추가·진입만.
