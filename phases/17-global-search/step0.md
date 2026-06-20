# Step 0: search-dao

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` (없으면 무시)
- `docs/TechSpec.md`
- `CLAUDE.md`
- `app/src/main/java/com/lsync/app/data/local/dao/EventDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/EventEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`

기존 DAO의 쿼리 스타일(`@Query` + `Flow<List<…>>` 반환, `deletedAt IS NULL` / `isExcluded = 0` 필터 관례)을 꼼꼼히 읽고 동일한 패턴으로 작업하라.

## 배경

L-Sync에 **통합 검색(Global Search)** 기능을 추가하는 task의 첫 단계다. 일정(events)·할일(todos)·가계부(finance)를 하나의 검색어로 횡단 검색한다. 이 step은 **DAO 레이어에 검색 쿼리만** 추가한다. Repository·ViewModel·UI는 다음 step에서 다룬다.

검색 대상 필드 (확정):
- events: `title`
- todos: `title`
- finance: `category` + `note`

## 작업

세 DAO에 각각 `LIKE` 기반 검색 쿼리를 추가하라. 모두 `Flow<List<…Entity>>`를 반환해 Room이 SSOT로서 변경을 실시간 방출하도록 한다.

`EventDao.kt`에 추가:
```kotlin
@Query("""
    SELECT * FROM events
    WHERE deletedAt IS NULL
      AND title LIKE '%' || :query || '%'
    ORDER BY startDate DESC
""")
fun searchByTitle(query: String): Flow<List<EventEntity>>
```

`TodoDao.kt`에 추가:
```kotlin
@Query("""
    SELECT * FROM todos
    WHERE deletedAt IS NULL
      AND title LIKE '%' || :query || '%'
    ORDER BY dueDate DESC, createdAt DESC
""")
fun searchByTitle(query: String): Flow<List<TodoEntity>>
```

`FinanceDao.kt`에 추가 (category 또는 note 매칭, 통계 제외 항목은 노출 안 함):
```kotlin
@Query("""
    SELECT * FROM finance
    WHERE isExcluded = 0
      AND (category LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
    ORDER BY date DESC
""")
fun search(query: String): Flow<List<FinanceEntity>>
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (KAPT가 Room 쿼리 검증)
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. (Room은 컴파일 타임에 SQL을 검증하므로 쿼리 오타가 있으면 KAPT 에러로 잡힌다.)
2. 아키텍처 체크리스트:
   - 반환 타입이 `Flow<List<…Entity>>`인가? (UI가 Room Flow를 구독하는 Offline-First 구조)
   - events/todos에 `deletedAt IS NULL`, finance에 `isExcluded = 0` 필터가 있는가?
   - `import kotlinx.coroutines.flow.Flow`가 각 DAO에 존재하는가? (이미 있을 가능성 높음)
3. 결과에 따라 `phases/17-global-search/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "EventDao.searchByTitle / TodoDao.searchByTitle / FinanceDao.search 추가 (LIKE, Flow 반환)"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- 새 테이블·마이그레이션을 추가하지 마라. 이유: 검색은 기존 테이블 읽기 전용이므로 스키마 변경이 전혀 필요 없다. 마이그레이션 누락은 런타임 크래시를 부른다.
- `suspend fun` 1회성 쿼리로 만들지 마라. 이유: 검색 결과가 데이터 변경에 실시간 반응해야 하고 Offline-First 원칙상 UI는 Room Flow를 구독한다.
- Repository·ViewModel·Compose 파일을 건드리지 마라. 이 step의 범위는 DAO 3개뿐이다.
- 기존 쿼리·메서드를 리팩토링하지 마라. 새 메서드만 추가하라.
