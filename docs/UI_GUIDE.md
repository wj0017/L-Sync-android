# L-Sync UI 가이드

## 디자인 원칙
- 다크 미니멀. 불필요한 장식 제거.
- 정보 밀도를 높이되 여백을 충분히 확보.
- 색상은 의미를 담는다. 장식용 색 사용 금지.

## 색상 시스템

```kotlin
// 배경
BgPrimary   = Color(0xFF0A0A0A)   // 앱 배경
BgSecondary = Color(0xFF141414)
BgCard      = Color(0xFF161616)   // 카드 배경
BgElevated  = Color(0xFF202020)

// 텍스트
FgPrimary   = Color(0xFFF5F5F5)   // 주요 텍스트
FgSecondary = Color(0xFF8A8A8A)   // 보조 텍스트
FgTertiary  = Color(0xFF5C5C5C)   // 레이블, 메타
FgDisabled  = Color(0xFF3A3A3A)   // 비활성

// 구분선
Divider       = Color(0xFF1F1F1F)
HairlineWhite = Color(0x09FFFFFF) // 카드 테두리 (rgba 255,255,255,0.035)

// 강조
AccentBlue  = Color(0xFF4F7EFF)   // 선택됨, 링크, 오늘 날짜
AccentBlue20= Color(0x334F7EFF)   // 네비게이션 인디케이터
AccentGreen = Color(0xFF43A047)   // 수입, 완료
AccentRed   = Color(0xFFE53935)   // 위험 (삭제 버튼)
AccentRed80 = Color(0xCCE53935)   // 일요일, 경고
```

## 타이포그래피

```kotlin
Pretendard         // 모든 UI 텍스트
InstrumentSerif    // Italic만 사용: 금액 기호(+/₩/−), 성경 절 번호
```

- 헤더 년도 레이블: 11sp / Medium / 0.12em / FgTertiary
- 화면 제목: 30sp / SemiBold / -0.035em / FgPrimary
- 본문: 14sp / Medium / -0.005em
- 메타 레이블: 10sp / Medium / 0.12em / FgTertiary (항상 uppercase)

## 컴포넌트 패턴

### 카드
```
배경: BgCard
테두리: 1dp HairlineWhite, RoundedCornerShape(14.dp)
패딩: 16dp horizontal, 14dp vertical
```

### Ghost Chip (필터, 타입 선택)
```
비활성: Transparent 배경 + 1dp Divider 테두리
활성: FgPrimary 배경 + FgPrimary 테두리 + BgPrimary 텍스트
```

### 헤더 구조 (모든 화면 동일)
```
padding: start=22dp, end=14dp, top=24dp, bottom=20dp
Column {
    Text(서브레이블)   // 11sp, FgTertiary (예: 연도, "개역개정")
    Spacer(6dp)
    Text(화면 제목)    // 30sp, SemiBold
}
+ 우측 IconButton (38dp circle, BgCard, HairlineWhite border)
```

### Date Chip (TodoScreen)
```
선택됨: FgPrimary 배경, BgPrimary 텍스트
오늘: AccentBlue DOW 텍스트
비선택: BgCard + HairlineWhite border
```

## 금지사항
- 라이트 모드 구현 금지 (다크 전용)
- 빨간색(AccentRed)을 지출 금액 색상으로 사용 금지 — 지출은 FgPrimary, 수입만 AccentGreen
- 그림자(elevation) 사용 금지 — 카드 구분은 HairlineWhite 테두리로만
- collectAsStateWithLifecycle 사용 금지
