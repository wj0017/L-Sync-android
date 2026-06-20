# Step 1: search-repository

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/TechSpec.md`
- `CLAUDE.md`
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt` — step 0에서 `searchByTitle(query): Flow<List<EventEntity>>` 추가됨
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — step 0에서 `searchByTitle(query): Flow<List<TodoEntity>>` 추가됨
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` — step 0에서 `search(query): Flow<List<FinanceEntity>>` 추가됨
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` — 기존 Repository 패턴 참고
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — Repository @Provides 등록 패턴 참고

이전 step에서 만들어진 DAO 검색 메서드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 배경

통합 검색 task의 두 번째 단계다. step 0에서 추가한 세 DAO 검색 Flow를 **하나의 결과 모델로 결합하는 Repository**를 만든다. ViewModel·UI는 다음 step에서 다룬다.

CRITICAL (CLAUDE.md): 새 Repository를 추가하면 반드시 `di/AppModule.kt`에 `@Provides` 함수를 추가한다. 누락 시 Hilt가 의존성을 찾지 못해 런타임 크래시.

## 작업

### 1. 결과 모델 + Repository 생성

`app/src/main/java/com/lsync/app/data/repository/SearchRepository.kt` 새 파일:

```kotlin
package com.lsync.app.data.repository

import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

// 도메인 횡단 검색 결과. 각 섹션은 비어 있을 수 있다.
data class SearchResults(
    val events: List<EventEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val finances: List<FinanceEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = events.isEmpty() && todos.isEmpty() && finances.isEmpty()

    val totalCount: Int
        get() = events.size + todos.size + finances.size
}

class SearchRepository(
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
) {
    // 공백/빈 쿼리는 DAO를 치지 않고 빈 결과를 즉시 방출한다.
    fun search(query: String): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(SearchResults())
        return combine(
            eventDao.searchByTitle(trimmed),
            todoDao.searchByTitle(trimmed),
            financeDao.search(trimmed),
        ) { events, todos, finances ->
            SearchResults(events = events, todos = todos, finances = finances)
        }
    }
}
```

핵심 규칙:
- **빈/공백 쿼리 가드**: `query.trim().isBlank()`이면 DAO를 호출하지 않고 `flowOf(SearchResults())`를 반환한다. 이유: 빈 쿼리로 전체 테이블을 LIKE `%%` 스캔하면 전체 행이 결과로 쏟아진다.
- 검색어는 `trim()` 후 DAO에 전달한다.
- Repository는 Room DAO만 의존한다. **Firestore를 import하거나 구독하지 마라** (Offline-First: 검색은 로컬 전용).

### 2. AppModule 등록

`di/AppModule.kt`에 `@Provides @Singleton` 함수를 추가한다. 기존 `provideFinanceRepository` 등과 동일한 스타일:

```kotlin
@Provides
@Singleton
fun provideSearchRepository(
    eventDao: EventDao,
    todoDao: TodoDao,
    financeDao: FinanceDao,
) = SearchRepository(eventDao, todoDao, financeDao)
```

필요한 import(`com.lsync.app.data.repository.SearchRepository`)도 추가하라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음, Hilt 그래프 검증 통과
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. (Hilt는 컴파일 타임에 의존성 그래프를 검증하므로 AppModule 누락 시 KAPT 에러가 난다.)
2. 아키텍처 체크리스트:
   - `SearchRepository`가 `di/AppModule.kt`에 `@Provides`로 등록되었는가? (CRITICAL)
   - Repository가 Firestore를 import/구독하지 않는가? (Room SSOT)
   - 빈/공백 쿼리 가드가 있는가?
3. 결과에 따라 `phases/17-global-search/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "SearchRepository(SearchResults 모델, combine 3 DAO Flow, blank 가드) + AppModule provideSearchRepository 등록"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `@HiltViewModel`이나 ViewModel 코드를 작성하지 마라. 이 step의 범위는 Repository + AppModule 등록뿐이다.
- Compose/UI 파일을 건드리지 마라.
- Repository에 `debounce`를 넣지 마라. 이유: 디바운스는 사용자 입력 타이밍 관심사이므로 ViewModel(step 2)에 둔다. Repository는 순수 데이터 결합만 담당한다.
- AppModule 등록을 빠뜨리지 마라. 이유: Hilt 런타임 크래시.
- 기존 Repository를 리팩토링하지 마라.
