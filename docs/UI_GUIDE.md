# L-Sync UI 가이드

## 디자인 원칙
- 다크 미니멀. 불필요한 장식 제거.
- 정보 밀도를 높이되 여백을 충분히 확보.
- 색상은 의미를 담는다. 장식용 색 사용 금지.

---

## 색상 시스템

```kotlin
// 배경
BgPrimary   = #0A0A0A   // 앱 배경, 스플래시 배경
BgSecondary = #141414   // 하단 네비게이션 바
BgCard      = #161616   // 카드 배경
BgElevated  = #202020

// 텍스트 (4단계 계층)
FgPrimary   = #F5F5F5   // 제목, 주요 텍스트
FgSecondary = #8A8A8A   // 보조 텍스트
FgTertiary  = #5C5C5C   // 레이블, 메타 (섹션 헤더, 연도 등)
FgDisabled  = #3A3A3A   // 비활성, 플레이스홀더

// 구분선
Divider       = #1F1F1F
HairlineWhite = rgba(255,255,255,0.035)  // 카드 테두리

// 강조
AccentBlue  = #4F7EFF   // 선택됨, 오늘 날짜, CTA 버튼
AccentBlue20= #334F7EFF // 네비게이션 인디케이터
AccentGreen = #43A047   // 수입, 완료 체크
AccentRed   = #E53935   // 삭제 버튼 (위험)
AccentRed80 = #CCE53935 // 일요일, 경고 텍스트
```

---

## 타이포그래피

```
Pretendard      — 모든 UI 텍스트 기본 폰트
InstrumentSerif — Italic만 사용: 금액 기호(+/₩/−), 성경 절 번호
```

### 공통 텍스트 스타일
| 용도 | Size | Weight | Tracking | Color |
|------|------|--------|----------|-------|
| 화면 제목 | 30sp | SemiBold | -0.035em | FgPrimary |
| 섹션 헤더 | 11sp | Medium | 0.12em | FgTertiary |
| 앱 이름 (홈) | 20sp | SemiBold | 0.06em | FgPrimary |
| 날짜 (홈) | 14sp | Medium | -0.01em | FgSecondary |
| 카드 본문 | 14sp | Medium | -0.005em | FgPrimary |
| 메타 레이블 | 10sp | Medium | 0.12em | FgTertiary (항상 uppercase) |

---

## 레이아웃 규칙

- **수평 패딩:** 전 화면 `20dp` 통일 (카드, 헤더, 섹션 레이블 모두 동일)
- **화면 상단:** `top = 24~32dp` (화면별 상이)
- **카드 간격:** `8dp`
- **아이템 간격 (리스트):** `6dp`

---

## 컴포넌트 패턴

### 카드
```
배경: BgCard
테두리: 1dp HairlineWhite, RoundedCornerShape(14dp)
패딩: horizontal 16dp, vertical 14dp
```

### 섹션 헤더
```
Text: 11sp / Medium / 0.12em / FgTertiary
padding: horizontal 20dp, bottom 10dp
```

### 섹션 구분선 (홈 화면)
```
HorizontalDivider: 0.5dp, Divider 색
padding: horizontal 20dp, top 24dp, bottom 20dp
```

### Ghost Chip (필터, 타입 선택)
```
비활성: Transparent 배경 + 1dp HairlineWhite 테두리 + FgSecondary 텍스트
활성:   AccentBlue 배경 + Transparent 테두리 + White 텍스트
```

### 원형 아이콘 버튼 (헤더)
```
크기: 38dp
배경: BgCard
테두리: 1dp HairlineWhite, CircleShape
아이콘: 20dp, FgPrimary
```

### 원형 체크박스 (할 일)
```
크기: 22dp, CircleShape
미완료: Transparent 배경 + 1.5dp FgDisabled 테두리
완료:   AccentBlue 배경 + "✓" White 11sp
```

---

## 화면별 헤더 구조

### 홈 화면
```
Row (좌우 정렬, vertical = Bottom)
├── "L·SYNC"          20sp SemiBold FgPrimary
└── Column (우측)
    ├── 연도            10sp Medium FgTertiary
    └── "MMM d, EEEE"  14sp Medium FgSecondary  (예: May 14, Wednesday)
```

### 그 외 화면 (가계부, 성경)
```
Row (SpaceBetween, vertical = Bottom)
├── Column
│   ├── 서브레이블   11sp Medium FgTertiary (연도, 버전 등)
│   └── 화면 제목   30sp SemiBold FgPrimary
└── 우측 아이콘 버튼 (38dp circle)
```

### 일정 화면 (통스크롤 예외)
단일 LazyColumn 통스크롤 레이아웃이라 헤더를 컴팩트하게 유지한다.
```
Row (vertical = Bottom)
└── "YYYY년 M월"   26sp SemiBold -0.035em FgPrimary  (연·월 한 줄 병합)
```

---

## 홈 화면 섹션 구조

```
[L·SYNC 헤더]
───────────────────────────────
성경 통독                         ← 섹션 헤더
[ReadingPlanCard]
─── divider ───
오늘 · 일정 & 할 일 · N           ← 섹션 헤더
[ScheduleItem × 최대 5]
[더 보기 →]
─── divider ───
M월 가계부                        ← 섹션 헤더
[FinanceSummaryCard]
```

### FinanceSummaryCard (홈 축소 버전)
```
Row { "잔액"(FgTertiary)   +₩X,XXX(FgPrimary 22sp) }
Row { 수입 ₩X(AccentGreen)   지출 ₩X(FgSecondary) }
```
잔액 부호(+/−)는 InstrumentSerif Italic 17sp. 지출 금액은 FgSecondary (빨간색 금지).

---

## 성경 뷰어 상태

| 상태 | 헤더 레이블 | 토글 버튼 | 교차 버튼 |
|------|------------|----------|----------|
| esvOnTop=true | "ESV" | "개역개정" | SwapVert (BgCard) |
| esvOnTop=false | "개역개정" | "ESV" | SwapVert (AccentBlue) |

교차 버튼 및 토글 버튼 상시 노출. 책명·섹션 헤더·검색 결과·목차 모두 esvOnTop에 연동.

---

## 정산 UI (가계부)
- **지출 카드 정산 푸터:** 진행 표시 "정산 받은 금액 ₩X / ₩Y"(AccentGreen) + "+ 받기" 버튼. 완료 시 "정산 완료"(FgTertiary).
- **수입 카드 연결 푸터:** "정산에 연결 →"(AccentBlue). 단 `잔액 ≥ 수입액 AND 지출일 ≤ 수입일`인 정산이 있을 때만 노출.
- **요약 카드 "정산 받음":** 돌려받은 돈이 있을 때만 "+₩X"(AccentBlue) 줄 추가. 수입과 별개로 표기.

---

## 홈 위젯 UI

Glance는 RemoteViews 위에서 동작하므로 앱 컴포넌트(`MaterialTheme`, Pretendard 폰트 등)를 사용할 수 없다.

### 위젯 전용 색상 상수 (`LSyncWidget.kt` 내 private 상수)

```kotlin
BgPrimary   = ColorProvider(Color(0xFF0A0A0A))
BgCard      = ColorProvider(Color(0xFF161616))
FgPrimary   = ColorProvider(Color(0xFFF5F5F5))
FgSecondary = ColorProvider(Color(0x99F5F5F5))   // 60% opacity
AccentBlue  = ColorProvider(Color(0xFF4F7EFF))
AccentGreen = ColorProvider(Color(0xFF43A047))
AccentRed   = ColorProvider(Color(0xFFE53935))
```

### 위젯 레이아웃 구조 (5×2, 2열 카드)

```
Box(BgPrimary, padding 12.dp)
└── Row (2열, 카드별 BgCard + HairlineWhite 테두리, 높이 고정)
    ├── 좌열 Column
    │   ├── 할 일 카드   — AccentBlue 헤더, 최대 3개 + "+N개 더"
    │   └── 일정 카드     — 시간 지정 일정은 "HH:mm 제목" 형식
    └── 우열 Column
        ├── 가계부 카드   — 이달 순지출/수입 막대 차트, 지출 AccentRed / 수입 AccentGreen
        └── 통독 카드     — "X/Y 완료" + 오늘 챕터 목록 (원형 진행 바 대신 목록 표시)
```

- 가계부 지출은 **순지출**(`expense − reimbursed`)을 표시 — 정산 받은 금액을 차감해 앱 내 표기와 일치.
- 카드 높이는 고정해 좌·우열 정렬을 맞춘다(데이터 양과 무관).

### 위젯 금지사항
- `androidx.compose.material3.*` 컴포넌트 사용 금지 — 런타임 크래시 발생
- Pretendard 폰트 설정 금지 — RemoteViews 미지원
- 지출에 AccentRed 금지 규칙은 앱 내 Finance와 달리 **위젯에서는 AccentRed 사용** (공간 제약상 색으로 구분)

---

## 금지사항
- 라이트 모드 구현 금지 (다크 전용)
- 지출 금액에 AccentRed 사용 금지 — 지출은 FgSecondary, 수입만 AccentGreen
- 그림자(elevation) 사용 금지 — 카드 구분은 HairlineWhite 테두리로만
- `collectAsStateWithLifecycle` 사용 금지 — `collectAsState()` 만 사용
- 수평 패딩 16dp/22dp 혼용 금지 — 20dp 통일
