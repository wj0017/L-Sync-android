# Step 2: search-viewmodel

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `app/src/main/java/com/lsync/app/data/repository/SearchRepository.kt` — step 1에서 생성. `search(query): Flow<SearchResults>`, `SearchResults(events, todos, finances)` 모델
- `app/src/main/java/com/lsync/app/ui/finance/FinanceDashboardViewModel.kt` — 기존 `@HiltViewModel` 패턴 참고 (MutableStateFlow + asStateFlow + viewModelScope)

이전 step에서 만들어진 `SearchRepository`/`SearchResults`를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 배경

통합 검색 task의 세 번째 단계다. step 1의 `SearchRepository`를 구독하는 **ViewModel**을 만든다. 사용자가 검색어를 입력하면 디바운스 후 결과를 방출한다. UI(Compose)는 다음 step에서 다룬다.

CRITICAL (CLAUDE.md):
- ViewModel은 반드시 `@HiltViewModel` + `@Inject constructor(...)` 패턴으로 작성한다. 없으면 Hilt 주입 실패로 런타임 크래시.
- `collectAsStateWithLifecycle` 금지. (이 step은 ViewModel만 작성하지만, 노출하는 StateFlow는 화면에서 `collectAsState()`로 구독될 것을 전제로 한다.)

## 작업

`app/src/main/java/com/lsync/app/ui/search/SearchViewModel.kt` 새 파일 생성.

UI 상태:
```kotlin
data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val hasSearched: Boolean = false,   // 사용자가 무언가 입력한 적 있는지 (빈 화면 vs "결과 없음" 구분용)
)
```

ViewModel 시그니처와 핵심 로직:
```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // queryFlow 변경 → 200ms 디바운스 → flatMapLatest로 이전 검색 취소 → repository.search 구독
    // 결과를 _uiState에 반영. query/hasSearched도 함께 유지.

    fun onQueryChange(query: String) { /* queryFlow.value 갱신 + _uiState query 즉시 반영 */ }

    fun clearQuery() { /* 검색어/결과 초기화 */ }
}
```

구현 지침:
- `queryFlow`에 `.debounce(200)` 후 `.flatMapLatest { repository.search(it) }`를 적용하고 `viewModelScope`에서 `collect`하여 `_uiState`의 `results`를 갱신한다. `flatMapLatest`/`debounce`는 `@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)`가 필요할 수 있으니 컴파일 에러에 따라 어노테이션을 붙여라.
- `onQueryChange`는 (1) `queryFlow.value`를 갱신하고 (2) `_uiState`의 `query`를 **즉시** 반영한다(입력 즉시 텍스트필드에 보이도록). `results`는 디바운스 파이프라인이 채운다.
- `hasSearched`: 사용자가 빈 문자열이 아닌 쿼리를 입력한 적이 있으면 true. "결과 없음" UI와 초기 안내 UI를 구분하기 위함.
- 텍스트필드는 화면(다음 step)에서 `uiState.query`를 단일 소스로 사용한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음, Hilt 그래프 검증 통과
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트:
   - `@HiltViewModel` + `@Inject constructor`가 있는가? (CRITICAL, 없으면 런타임 크래시)
   - `uiState`가 `StateFlow`로 노출되는가? (`MutableStateFlow` + `asStateFlow()`)
   - ViewModel이 Firestore를 직접 구독하지 않고 `SearchRepository`만 의존하는가?
   - 디바운스/`flatMapLatest`로 연속 입력 시 이전 검색이 취소되는가?
3. 결과에 따라 `phases/17-global-search/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "SearchViewModel(@HiltViewModel, SearchUiState{query,results,hasSearched}, debounce(200)+flatMapLatest, onQueryChange/clearQuery)"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- Compose/Screen 파일을 작성하지 마라. 이 step의 범위는 ViewModel뿐이다. (화면은 step 3)
- `NavGraph`·`HomeScreen`을 건드리지 마라. (배선은 step 4)
- `@AndroidEntryPoint`나 Activity 코드를 작성하지 마라.
- 빈 쿼리에서 전체 결과를 방출하지 마라. 이유: Repository(step 1)가 이미 blank 가드를 갖지만, ViewModel도 빈 쿼리 시 `results`를 비워 둬야 한다.
- 기존 ViewModel을 리팩토링하지 마라.
