# Step 3: budget-display

## 배경

이 task(`16-budget`)는 "가계부 예산 — 카테고리별 + 전체 월 한도, 매월 반복, 초과 경고"를 구현한다.

**이전 Step에서 만든 것:**
- (Step 2) `FinanceDashboardViewModel`이 `budgets: List<BudgetProgress>`(category/limit/spent/ratio/isOver)를 노출. 카테고리=EXPENSE 원금, TOTAL=순지출, 이번 달 기준.

이 Step은 **예산 진행률 표시 UI**를 대시보드에 추가한다(읽기 전용). 편집(설정)은 Step 4.

## 읽어야 할 파일

- `docs/UI_GUIDE.md`, `CLAUDE.md` 디자인 시스템 — 다크 미니멀, Pretendard, **AccentBlue/Green/Red**, HairlineWhite 카드 테두리, 그림자 금지, 새 색상 금지.
- `docs/ARCHITECTURE.md` — 통계 대시보드: **"지출에 `AccentRed`를 쓰지 않음(체감 부담 완화, 디자인 의도)"**, Canvas 직접 그림(차트 라이브러리 미사용).
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboard.kt` — **수정 대상.** `LazyColumn`에 `DashboardSummaryCard`/`SectionHeader`/`MonthlyTrendCard`/`CategoryBreakdownCard`. `state by viewModel.uiState.collectAsState()`. 카드 스타일(`SectionHeader`, padding 16dp) 패턴.
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — Step 2의 `budgets` state.
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt` — 색 토큰.

## 작업

`FinanceDashboard`에 "예산" 섹션을 추가한다.

- `state.budgets`가 비어 있지 않으면 `SectionHeader("예산")` + 예산 카드를 추가한다(기존 카드 스타일 재사용).
- 각 `BudgetProgress`를 **진행바(progress bar)** 로 표시: 카테고리명 / `spent` · `limit` 금액 / 비율 막대.
  - 진행바는 Canvas 또는 단순 Box 비율로 그린다(기존 대시보드가 Canvas 직접 그림 — 차트 라이브러리 추가 금지).
  - `ratio`는 1.0으로 클램프해 막대를 그리되, `isOver`면 초과 상태를 명확히 표시.
- **전체 한도(`TOTAL_CATEGORY`)** 항목은 맨 위 또는 별도로 "전체"로 라벨링(카테고리 슬라이스와 시각적으로 구분).

### 색상 — 디자인 의도 준수 (중요)

- 정상 진행 막대: **`AccentRed` 금지**(지출 체감 완화 디자인 의도). `AccentBlue` 또는 중립 톤 사용.
- **초과(`isOver`)** 는 명시적 경고이므로 절제된 강조로 표시: 예) 막대 초과분 또는 "초과" 라벨에만 `AccentRed`를 제한적으로 쓰거나, `AccentRed` 대신 경고 텍스트("한도 초과 ₩X") + 막대 색 변화. **전체 막대를 빨갛게 칠해 일상 지출을 위협적으로 만들지 마라.** 디자인 의도(체감 부담 완화)와 경고 명확성의 균형을 맞춘다.

## 핵심 규칙 (반드시 지킬 것)

- **정상 지출 막대에 `AccentRed`를 쓰지 마라.** 이유: "지출에 AccentRed 미사용" 디자인 의도(ARCHITECTURE). 초과 경고에만 절제해 사용.
- **차트/그래프 라이브러리를 추가하지 마라.** 진행바는 Canvas/Box로 직접. (기존 대시보드 일관)
- **`collectAsState()`만.** UI는 ViewModel state만 읽는다. Firestore 직접 구독 금지.
- **예산 미설정 시 섹션을 숨겨라**(빈 `budgets`면 SectionHeader도 미표시). 이유: 깔끔함.
- **디자인 토큰 준수**(Pretendard, 다크, HairlineWhite 테두리, 그림자 금지, 새 색상 금지).
- 이 Step은 **표시만**. 예산 설정/편집 UI는 Step 4.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - 대시보드에 예산 섹션(진행바)이 추가됐는가? 미설정 시 숨김?
   - 정상 막대가 AccentRed를 피하고, 초과만 절제된 경고로 표시하는가?
   - 전체 한도(TOTAL)가 카테고리와 구분 표시되는가?
   - 차트 라이브러리 미추가·`collectAsState()`·디자인 토큰 준수?
3. 결과에 따라 `phases/16-budget/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "FinanceDashboard에 예산 섹션 추가 — BudgetProgress 진행바(Canvas/Box, 차트 라이브러리 미사용), 정상=AccentBlue/중립(AccentRed 미사용), 초과만 절제된 경고. TOTAL 구분 표시, 미설정 시 섹션 숨김"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 정상 지출 진행바를 빨강(`AccentRed`)으로 칠하지 마라. 이유: 디자인 의도 위반.
- 차트 라이브러리(MPAndroidChart 등)를 추가하지 마라. 이유: 기존 Canvas 직접 그림 원칙.
- 예산 설정/편집 다이얼로그를 만들지 마라(Step 4).
- 기존 대시보드 카드(요약/추세/카테고리)를 바꾸지 마라. 예산 섹션만 추가.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
