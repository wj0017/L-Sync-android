# Step 3: finance-screen

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/ui/finance/FinanceViewModel.kt` (Step 2에서 수정됨)
- `app/src/main/java/com/lsync/app/ui/finance/FinanceScreen.kt`
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/ui/theme/Color.kt`
- `app/src/main/java/com/lsync/app/ui/calendar/CalendarScreen.kt` (다이얼로그 패턴 참고용)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. TransactionFormSheet 신규 생성

`app/src/main/java/com/lsync/app/ui/finance/TransactionFormSheet.kt`를 신규 생성하라.

```kotlin
@Composable
fun TransactionFormSheet(
    formState: FormUiState,
    onTypeChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
)
```

**UI 구성 규칙:**

- `ModalBottomSheet` 또는 `AlertDialog` 중 선택하되, 디자인 시스템(`BgCard #161616` 배경)을 따른다.
- **유형 선택**: "지출" / "수입" 두 버튼. 선택된 쪽은 `FgPrimary` solid, 미선택은 outline(Ghost chip 패턴).
- **금액 입력**: `TextField`, `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)`. 단위(원)를 suffix로 표시.
- **카테고리 선택**: `FinanceCategory.all` 목록을 가로 스크롤 chip 행으로 표시. 선택된 chip은 `AccentBlue #4F7EFF` 배경.
- **날짜 입력**: `TextField`로 `"YYYY-MM-DD"` 직접 입력. 타임존 변환 없이 입력값 그대로 저장.
- **메모 입력**: 선택 입력 `TextField`.
- **저장/취소 버튼**: 저장은 `AccentBlue`, 취소는 ghost. `isSaving = true`이면 저장 버튼 비활성화.
- `errorMessage`가 있으면 폼 하단에 `AccentRed #E53935` 텍스트로 표시.
- 폰트: Pretendard (기본). 금액 suffix "원"은 Instrument Serif Italic.

### 2. FinanceScreen 업데이트

`FinanceScreen.kt`를 수정하여 다음 기능을 연결하라:

**폼 연결:**
```kotlin
val formState by viewModel.formState.collectAsState()

if (formState.isVisible) {
    TransactionFormSheet(
        formState = formState,
        onTypeChange = { viewModel.updateFormField(type = it) },
        onAmountChange = { viewModel.updateFormField(amount = it) },
        onCategoryChange = { viewModel.updateFormField(category = it) },
        onDateChange = { viewModel.updateFormField(date = it) },
        onNoteChange = { viewModel.updateFormField(note = it) },
        onSave = viewModel::saveTransaction,
        onDismiss = viewModel::closeForm,
    )
}
```

**"추가" 버튼 연결:**
- 기존 TODO 플레이스홀더를 `viewModel.openCreateForm(LocalDate.now().toString())`으로 교체.

**거래 항목 탭 → 편집:**
- 각 거래 항목(row)을 탭하면 `viewModel.openEditForm(finance)` 호출.
- `sourceTodoId != null`인 항목은 탭 시 편집 폼 대신 "Todo 연동 항목은 수정할 수 없습니다" Snackbar를 표시한다.

**거래 항목 삭제:**
- 각 항목 row에 삭제 아이콘(🗑) 또는 스와이프 제스처로 `viewModel.deleteTransaction(finance.id)` 호출.
- `sourceTodoId != null`인 항목에는 삭제 버튼을 표시하지 않는다.

**CSV 내보내기:**
- 헤더 우측에 내보내기 아이콘 버튼 추가.
- `exportedCsv` SharedFlow를 `LaunchedEffect`로 수집하여, 값이 방출되면 Android Share Intent를 실행한다:
  ```kotlin
  LaunchedEffect(Unit) {
      viewModel.exportedCsv.collect { csv ->
          val intent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, csv)
              putExtra(Intent.EXTRA_SUBJECT, "L-Sync 가계부 ${uiState.yearMonth}")
          }
          context.startActivity(Intent.createChooser(intent, "내보내기"))
      }
  }
  ```
- 내보내기 버튼 클릭 시 `viewModel.triggerExport()` 호출.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `TransactionFormSheet.kt`가 `ui/finance/` 하위에 생성됐는가?
   - "추가" 버튼이 `openCreateForm()`에 연결됐는가?
   - `sourceTodoId != null` 항목에 편집/삭제가 막혀 있는가?
   - CSV 내보내기가 Share Intent로 연결됐는가?
   - `collectAsState()`를 사용하고 `collectAsStateWithLifecycle`은 없는가?
   - 다크 미니멀 테마(BgCard, FgPrimary, AccentBlue 등)를 따르는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/2-finance-automation/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- 날짜를 타임존 변환하지 마라. 이유: CLAUDE.md CRITICAL 규칙 — `"YYYY-MM-DD"` 문자열 그대로 저장.
- `sourceTodoId != null`인 항목에 편집/삭제 기능을 노출하지 마라. 이유: Todo 연동 항목의 생명주기는 TodoRepository가 담당한다.
- 라이트 모드 색상을 사용하지 마라. 이유: 이 앱은 다크 미니멀 테마만 지원한다.
- FinanceViewModel의 기존 `UiState`, `shiftMonth()`, `setFilter()` 코드를 수정하지 마라. 이유: 이 step은 UI 연결만 담당한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
