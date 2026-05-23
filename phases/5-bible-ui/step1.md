# Step 1: bible-memo-persist

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/UI_GUIDE.md`
- `app/src/main/java/com/lsync/app/data/local/entity/MemoEntity.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/dao/MemoDao.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/ui/bible/BibleViewModel.kt` (전체 읽기)
- `app/src/main/java/com/lsync/app/ui/bible/BibleScreen.kt` (전체 읽기)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. BibleViewModel 수정

`app/src/main/java/com/lsync/app/ui/bible/BibleViewModel.kt`를 수정하라.

#### 1-1. BibleUiState에 memos 추가

```kotlin
data class BibleUiState(
    // ... 기존 필드 유지 ...
    val memos: List<MemoEntity> = emptyList(),
)
```

#### 1-2. BibleViewModel 생성자에 MemoDao 추가

```kotlin
@HiltViewModel
class BibleViewModel @Inject constructor(
    private val bibleDao: BibleDao,
    private val esvDao: EsvDao,
    private val memoDao: MemoDao,
    @ApplicationContext context: Context,
) : ViewModel()
```

#### 1-3. observeMemos 함수 추가

장이 바뀔 때마다 해당 장의 메모 Flow를 새로 구독한다. 이전 구독은 취소한다.

```kotlin
private var memoJob: Job? = null

private fun observeMemos(book: Int, chapter: Int) {
    memoJob?.cancel()
    memoJob = viewModelScope.launch {
        memoDao.observeForChapter(book, chapter).collect { memos ->
            _state.value = _state.value.copy(memos = memos)
        }
    }
}
```

`fetchAndApply(book, chapter)` 함수 끝에 `observeMemos(book, chapter)` 호출을 추가하라.

#### 1-4. saveMemo / deleteMemo 추가

```kotlin
fun saveMemo(verse: Int, text: String, date: String) {
    val s = _state.value
    viewModelScope.launch {
        memoDao.insert(MemoEntity(book = s.currentBook, chapter = s.currentChapter, verse = verse, text = text, date = date))
    }
}

fun deleteMemo(id: Long) {
    viewModelScope.launch { memoDao.deleteById(id) }
}
```

### 2. BibleScreen 수정

`app/src/main/java/com/lsync/app/ui/bible/BibleScreen.kt`를 수정하라.

#### 2-1. 로컬 SavedMemo 제거

아래를 삭제한다:
- `private data class SavedMemo(val ref: String, val text: String, val date: String)` 선언 전체
- `var savedMemo by remember { mutableStateOf<SavedMemo?>(null) }` 선언
- `LaunchedEffect` 안의 `savedMemo = null` 줄 (나머지 `anchored = null`, `memoText = ""` 리셋은 유지)

#### 2-2. 저장 버튼 동작 교체

기존:
```kotlin
savedMemo = SavedMemo(anchoredRef, memoText.trim(), todayLabel)
memoText = ""
anchored = null
```

변경 후:
```kotlin
viewModel.saveMemo(anchored!!, memoText.trim(), LocalDate.now().toString())
memoText = ""
anchored = null
```

`LocalDate.now().toString()`은 `YYYY-MM-DD` 형식을 반환한다.

#### 2-3. 메모 카드 렌더링 교체

기존의 `savedMemo?.let { memo -> ... }` 블록을 아래로 교체한다:

```kotlin
// Persisted memo cards (현재 장만)
if (isCurrentPage) {
    items(state.memos, key = { it.id }) { memo ->
        val memoRef = if (state.esvOnTop) {
            "${BOOK_NAMES_EN[memo.book] ?: ""} ${memo.chapter}:${memo.verse}"
        } else {
            val bookKoName = state.books.find { it.book == memo.book }?.bookName ?: ""
            "$bookKoName ${memo.chapter}장 ${memo.verse}절"
        }
        val memoDateLabel = runCatching {
            LocalDate.parse(memo.date)
                .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))
        }.getOrDefault(memo.date)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "묵상 · $memoRef · $memoDateLabel".uppercase(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.12.em,
                    color = FgTertiary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.deleteMemo(memo.id) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "삭제",
                        tint = FgTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = memo.text,
                fontFamily = Pretendard,
                fontSize = 14.sp,
                lineHeight = (14 * 1.55).sp,
                color = FgPrimary,
            )
        }
    }
}
```

`state.memos`는 ViewModel이 Room Flow로 자동 업데이트하므로, 삭제 즉시 카드가 사라진다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `CLAUDE.md` CRITICAL 규칙을 위반하지 않았는가?
   - Room이 SSOT인가? 메모는 Room에 저장하고 UI는 Flow를 통해 구독하는가?
   - `collectAsStateWithLifecycle`를 사용하지 않았는가? (`collectAsState()`만 사용)
   - `@HiltViewModel` + `@Inject constructor` 패턴을 유지했는가?
   - 로컬 `savedMemo` state가 완전히 제거됐는가?
3. 결과에 따라 `phases/5-bible-ui/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `memoDao.observeForChapter()`를 `suspend fun`으로 호출하지 마라. 이유: `Flow` 반환 함수는 suspend가 아니며, `collect`로 구독해야 한다.
- `viewModelScope.launch` 없이 DAO suspend 함수를 직접 호출하지 마라. 이유: Main 스레드에서 DB 작업 시 ANR 발생.
- 메모 저장 후 `fetchAndApply`를 다시 호출하지 마라. 이유: `observeMemos`의 Flow가 자동으로 UI를 갱신한다. 재호출하면 성경 본문이 불필요하게 리로드된다.
- `savedMemo` 관련 코드를 남기지 마라. 이유: 로컬 state와 Room state가 공존하면 렌더링 충돌이 발생한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
