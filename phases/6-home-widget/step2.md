# Step 2: widget-ui

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`
- `app/src/main/java/com/lsync/app/ui/widget/WidgetState.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/ReadingPlanEntity.kt`
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt`

## 작업

`LSyncWidget.kt`의 `provideContent` 블록 안에 Glance 컴포저블 UI를 구현한다.
Glance는 일반 Compose와 API가 다르다. 반드시 `androidx.glance.*` 패키지의 컴포넌트만 사용한다.

### 디자인 시스템 (Glance용 색상)

Glance는 RemoteViews 위에서 동작하므로 앱의 `MaterialTheme` / `BgPrimary` 등을 직접 참조할 수 없다. 아래 색상을 파일 상단에 private 상수로 정의하라:

```kotlin
private val BgPrimary    = ColorProvider(Color(0xFF0A0A0A))
private val BgCard       = ColorProvider(Color(0xFF161616))
private val FgPrimary    = ColorProvider(Color(0xFFF5F5F5))
private val FgSecondary  = ColorProvider(Color(0x99F5F5F5))  // 60% opacity
private val AccentBlue   = ColorProvider(Color(0xFF4F7EFF))
private val AccentGreen  = ColorProvider(Color(0xFF43A047))
private val AccentRed    = ColorProvider(Color(0xFFE53935))
```

### UI 구조

`LSyncWidget.kt`에 `@Composable` 함수 `WidgetContent(state: WidgetState)`를 추가하고,
`provideContent { WidgetContent(state) }` 형태로 호출하라.

```
Box(배경 BgPrimary, padding 12.dp)
└── Column(verticalAlignment Top)
    ├── SectionHeader("할 일")  + TodoSection(state.todos)
    ├── Spacer(4.dp)
    ├── SectionHeader("일정")   + EventSection(state.events)
    ├── Spacer(4.dp)
    ├── SectionHeader("가계부") + FinanceSection(state.monthExpense, state.monthIncome)
    └── Spacer(4.dp)
        SectionHeader("통독")   + ReadingSection(state.todayReadingPlan, state.readCount, state.totalCount)
```

#### SectionHeader

- `Text(label, color=AccentBlue, fontSize=10.sp)`

#### TodoSection

- `todos`가 비어 있으면 `Text("오늘 할 일 없음", color=FgSecondary, fontSize=11.sp)`
- 최대 3개 표시. 3개를 초과하면 마지막 줄에 `Text("+${todos.size - 3}개 더", color=FgSecondary, fontSize=10.sp)`
- 각 항목: `Text("• ${todo.title}", color=FgPrimary, fontSize=11.sp, maxLines=1)`

#### EventSection

- `events`가 비어 있으면 `Text("오늘 일정 없음", color=FgSecondary, fontSize=11.sp)`
- 최대 3개 표시. TodoSection과 동일한 패턴.
- 시간 지정 일정(`isAllDay == false`)은 제목 앞에 시간을 표시하라. `startDate`에서 `T` 뒤의 시분(`HH:mm`)을 파싱해 `"HH:mm 제목"` 형식으로 표시한다. 파싱 실패 시 제목만 표시한다.

#### FinanceSection

- Row 안에 두 컬럼:
  - `Text("지출 ${formatAmount(monthExpense)}원", color=AccentRed, fontSize=11.sp)`
  - `Text("수입 ${formatAmount(monthIncome)}원", color=AccentGreen, fontSize=11.sp)`
- `formatAmount(amount: Long): String` — 천 단위 쉼표 포맷 (예: `1,200,000`). `String.format("%,d", amount)` 활용.

#### ReadingSection

- `todayReadingPlan`이 비어 있으면 `Text("통독 계획 없음", color=FgSecondary, fontSize=11.sp)` 표시
- 진행률 표시: `Text("${readCount}/${totalCount} 완료", color=FgPrimary, fontSize=11.sp)`
- 아직 읽지 않은 챕터 최대 2개 표시: `Text("• ${bookName} ${chapter}장", color=FgSecondary, fontSize=10.sp)`
  - `book` 번호를 한국어 책명으로 변환하는 최소 매핑이 필요하다. 66권 전체 배열을 파일 내에 private 상수로 정의하라 (창세기=1 … 요한계시록=66).
  - 이미 읽은 챕터는 표시하지 않는다 (`isRead == false`인 항목만).

### Glance 사용 시 주의

- `Column`, `Row`, `Box`, `Text`, `Spacer`는 모두 `androidx.glance` 패키지에서 import한다. Compose Material3 컴포넌트(`androidx.compose.material3.*`)는 Glance에서 사용 불가하다.
- `fillMaxWidth()`, `wrapContentHeight()`는 `androidx.glance.layout.*`에 있다.
- `padding()`은 `androidx.glance.layout.padding`이다.
- `TextStyle`은 `androidx.glance.text.TextStyle`이다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `WidgetContent` 컴포저블이 구현됐는가?
   - 4개 섹션(할 일·일정·가계부·통독) 모두 포함됐는가?
   - `androidx.compose.material3.*` import가 없는가?
   - 빈 상태(데이터 없음) 처리가 각 섹션에 있는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/6-home-widget/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `androidx.compose.material3.*`, `androidx.compose.ui.*` 컴포넌트를 사용하지 마라. 이유: Glance는 RemoteViews 기반이므로 일반 Compose 컴포넌트가 런타임 크래시를 일으킨다.
- Pretendard 폰트를 설정하지 마라. 이유: Glance(RemoteViews)는 커스텀 폰트를 지원하지 않는다.
- 클릭 인터랙션(Todo 체크 등)을 구현하지 마라. 이유: 인터랙션은 이 step의 범위가 아니다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
