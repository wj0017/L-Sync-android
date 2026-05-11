# Step 2: finance-viewmodel

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` (Step 1에서 수정됨)
- `app/src/main/java/com/lsync/app/ui/finance/FinanceViewModel.kt`
- `app/src/main/java/com/lsync/app/ui/todo/TodoViewModel.kt` (UiState 패턴 참고용)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

`FinanceViewModel.kt`에 폼 상태와 CRUD 메서드를 추가하라. 기존 `shiftMonth()`, `setFilter()`, `loadMonth()`, `UiState` 관련 코드는 수정하지 마라.

### 1. FormUiState 추가

ViewModel 파일 안에 data class를 추가하라:

```kotlin
data class FormUiState(
    val isVisible: Boolean = false,
    val isEditing: Boolean = false,
    val editId: String? = null,
    val type: String = "EXPENSE",       // "EXPENSE" | "INCOME"
    val amount: String = "",            // 사용자 입력 문자열 (Long 변환은 save 시)
    val category: String = FinanceCategory.ETC,
    val date: String = "",              // "YYYY-MM-DD"
    val note: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
```

### 2. ViewModel에 StateFlow 및 메서드 추가

```kotlin
private val _formState = MutableStateFlow(FormUiState())
val formState: StateFlow<FormUiState> = _formState.asStateFlow()

fun openCreateForm(defaultDate: String)   // 오늘 날짜를 기본값으로 폼 열기
fun openEditForm(finance: FinanceEntity)  // 기존 항목 데이터로 폼 채운 뒤 열기
fun closeForm()                           // 폼 닫기 + 상태 초기화
fun updateFormField(                      // 폼 필드 개별 업데이트
    type: String? = null,
    amount: String? = null,
    category: String? = null,
    date: String? = null,
    note: String? = null,
)
fun saveTransaction()                     // create 또는 update 호출
fun deleteTransaction(id: String)         // delete 호출
fun triggerExport()                       // exportCsv 호출 후 결과를 _exportedCsv에 emit
```

```kotlin
private val _exportedCsv = MutableSharedFlow<String>()
val exportedCsv: SharedFlow<String> = _exportedCsv.asSharedFlow()
```

**구현 규칙:**

- `saveTransaction()`: `amount`를 `toLongOrNull()`으로 변환. null이거나 0 이하이면 `formState.errorMessage = "금액을 올바르게 입력하세요"`를 설정하고 저장 중단. 저장 성공 후 `closeForm()` 호출, 현재 월 데이터 재로드.
- `deleteTransaction(id)`: FinanceRepository.delete()를 호출. sourceTodoId 있는 항목 삭제 시도에서 예외가 오면 무시하고 로그만 남긴다.
- `triggerExport()`: `_uiState.value.yearMonth`를 기준으로 `FinanceRepository.exportCsv()` 호출. 결과 CSV 문자열을 `_exportedCsv.emit()`으로 방출.
- `openEditForm(finance)`: `finance.sourceTodoId != null`이면 편집 불가 — `formState.errorMessage = "Todo 연동 항목은 수정할 수 없습니다"`를 설정하고 폼을 열지 않는다.
- 모든 비동기 작업은 `viewModelScope.launch`에서 실행한다.
- `collectAsState()`를 사용하라. `collectAsStateWithLifecycle`은 사용 금지.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `FormUiState`가 정의됐는가?
   - `formState: StateFlow<FormUiState>`가 노출됐는가?
   - `exportedCsv: SharedFlow<String>`가 노출됐는가?
   - `openEditForm()`이 `sourceTodoId != null` 항목에 대해 편집을 막는가?
   - `@HiltViewModel` + `@Inject constructor`가 유지됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/2-finance-automation/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- Firebase Auth를 호출하지 마라. 이유: CLAUDE.md CRITICAL 규칙. userId는 `"local_user"` 고정.
- 기존 `UiState`, `shiftMonth()`, `setFilter()`, `loadMonth()` 코드를 수정하지 마라. 이유: 이 step은 폼 상태와 CRUD 메서드 추가만 담당한다.
- UI 레이어 코드(Composable 등)를 ViewModel에 추가하지 마라. 이유: 레이어 분리 원칙.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
