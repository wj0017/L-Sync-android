# Step 0: data-layer

도메인 횡단 **월간 리포트**(일정·할일·가계부·통독)를 만들기 위한 첫 단계다. 이 step에서는 월간 집계에 필요한 Room DAO 쿼리와 Repository pass-through 메서드만 추가한다. ViewModel/UI는 다음 step에서 만든다.

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md` — 레이어 구조, Offline-First, Room=SSOT
- `docs/PRD.md` — 2.4 정산 정책(다음 step에서 집계 시 사용), 2.6 예산 정책
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt` — 수정 대상. 기존 `observeByDate`, `searchByTitle` 쿼리 패턴 참고
- `app/src/main/java/com/lsync/app/data/local/dao/ReadingPlanDao.kt` — 수정 대상. 기존 `observeForDate`, `getReadDates` 쿼리 패턴 참고
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `dueDate: String?`("YYYY-MM-DD"), `isCompleted`, `deletedAt` 필드
- `app/src/main/java/com/lsync/app/data/local/entity/ReadingPlanEntity.kt` — `date`, `book`, `chapter`, `isRead` 필드
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` — 수정 대상. 기존 `observeByDate` pass-through 패턴 참고
- `app/src/main/java/com/lsync/app/data/repository/ReadingPlanRepository.kt` — 수정 대상. 기존 `observeForDate` pass-through 패턴 참고

## 작업

### 1. `TodoDao.kt` — 마감일 범위 조회 추가

마감일(`dueDate`)이 특정 월 범위에 속하는 미삭제 Todo를 구독하는 Flow 쿼리를 추가한다.

```kotlin
@Query("""
    SELECT * FROM todos
    WHERE deletedAt IS NULL
      AND dueDate IS NOT NULL
      AND dueDate >= :from
      AND dueDate <= :to
    ORDER BY dueDate ASC
""")
fun observeByDueDateRange(from: String, to: String): Flow<List<TodoEntity>>
```

- `from`/`to`는 `"YYYY-MM-DD"` 문자열 경계(예: `"2026-06-01"` ~ `"2026-06-30"`).
- `dueDate IS NOT NULL` 조건을 반드시 포함하라. 이유: 마감일 없는 Todo는 월간 완료율 분모에서 제외해야 한다.

### 2. `ReadingPlanDao.kt` — 읽은 통독 범위 조회 추가

특정 날짜 범위에서 **읽음 처리된**(`isRead = 1`) 통독 항목을 구독하는 Flow 쿼리를 추가한다.

```kotlin
@Query("""
    SELECT * FROM reading_plan
    WHERE isRead = 1
      AND date >= :from
      AND date <= :to
    ORDER BY date ASC
""")
fun observeReadInRange(from: String, to: String): Flow<List<ReadingPlanEntity>>
```

- `import kotlinx.coroutines.flow.Flow`가 필요하면 추가하라(이미 import되어 있을 수 있음).
- 다음 step에서 이 결과로 "월간 읽은 챕터 수"(행 개수)와 "읽은 날 수"(distinct date 개수)를 계산한다.

### 3. `TodoRepository.kt` — pass-through 추가

```kotlin
fun observeByDueDateRange(from: String, to: String): Flow<List<TodoEntity>> =
    todoDao.observeByDueDateRange(from, to)
```

### 4. `ReadingPlanRepository.kt` — pass-through 추가

```kotlin
fun observeReadInRange(from: String, to: String): Flow<List<ReadingPlanEntity>> =
    dao.observeReadInRange(from, to)
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음 (Room 어노테이션 프로세서가 쿼리 검증)
./gradlew lintDebug       # 린트 경고 없음
```

`assembleDebug`가 통과하면 Room이 SQL 쿼리와 반환 타입 매핑을 검증한 것이므로 쿼리가 유효하다.

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (DAO는 `data/local/dao/`, Repository는 `data/repository/`)
   - DAO 쿼리가 Flow를 반환하는가? (UI가 Room Flow를 구독하는 Offline-First 패턴)
   - 새 DAO/Repository **클래스**를 만들지 않았는가? (기존 클래스에 메서드만 추가했으므로 `AppModule` 변경 불필요)
3. 결과에 따라 `phases/19-monthly-report/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약 (추가한 메서드 시그니처 4개 명시)"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- DB 스키마(컬럼/테이블)를 변경하지 마라. 이유: 이 step은 SELECT 쿼리만 추가하므로 AppDatabase 버전 업·마이그레이션이 불필요하다. 스키마를 건드리면 `MIGRATION_5_6`이 필요해져 범위를 벗어난다.
- 새 DAO 클래스나 Repository 클래스를 만들지 마라. 이유: 기존 `TodoDao`/`ReadingPlanDao`/`TodoRepository`/`ReadingPlanRepository`에 메서드만 추가하면 되고, 새 클래스를 만들면 `AppModule` 등록이 필요해진다.
- EventDao/FinanceDao/EventRepository/FinanceRepository는 건드리지 마라. 이유: 월간 일정·가계부 집계는 기존 `observeForExpansion`·`observeByDateRange`를 그대로 재사용한다.
- ViewModel이나 UI 코드를 작성하지 마라. 이 step의 범위는 데이터 레이어뿐이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
