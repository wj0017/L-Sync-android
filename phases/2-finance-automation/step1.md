# Step 1: finance-repository

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` (Step 0에서 수정됨)
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt`
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt`
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt`
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt` (syncSafe 패턴 참고용)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. FinanceRepository CRUD 추가

`FinanceRepository.kt`에 아래 메서드들을 추가하라. 기존 `observeByMonth()`는 수정하지 마라.

```kotlin
suspend fun create(
    type: String,       // "INCOME" | "EXPENSE"
    amount: Long,
    category: String,
    date: String,       // "YYYY-MM-DD"
    note: String?,
): String               // 생성된 FinanceEntity의 id 반환

suspend fun update(
    id: String,
    type: String,
    amount: Long,
    category: String,
    date: String,
    note: String?,
)

suspend fun delete(id: String)

suspend fun exportCsv(yearMonth: String): String  // "YYYY-MM" 형식 입력, CSV 문자열 반환
```

**구현 규칙:**

- `create()`: UUID로 id 생성, `createdAt = updatedAt = System.currentTimeMillis()`, `userId = "local_user"`, `sourceTodoId = null`, `isExcluded = false`. Room `upsert()` 후 `syncSafe { firestoreDataSource.upsertFinance(entity) }` 호출.
- `update()`: 기존 레코드를 `copy()`로 변경, `updatedAt` 갱신. Room `upsert()` 후 `syncSafe { firestoreDataSource.upsertFinance(entity) }` 호출.
- `delete()`: Room `deleteById(id)` 후 `syncSafe { firestoreDataSource.deleteFinance(id) }` 호출. **sourceTodoId가 있는 항목(Todo 연동 항목)은 삭제하지 않는다.** 이유: Todo 생명주기 정책(CLAUDE.md)에 따라 Todo 연동 항목은 `excludeByTodoId`로만 처리해야 한다. 삭제 전 `getById(id)`로 확인 후, `sourceTodoId != null`이면 예외를 던져라.
- `exportCsv()`: `getAllByDateRange(userId, "${yearMonth}-01", "${yearMonth}-31")`로 조회 후 CSV 문자열 생성. 헤더: `날짜,유형,카테고리,금액,메모`. 유형은 `"INCOME"` → `"수입"`, `"EXPENSE"` → `"지출"`로 변환. 금액은 원 단위 정수. note가 null이면 빈 문자열.

**syncSafe 패턴:** TodoRepository를 참고하여 동일한 `syncSafe { }` 래퍼를 사용하라. Firestore 실패는 Crashlytics에 `sync_failed` 이벤트로 기록하고 로컬 성공을 우선한다.

### 2. FirestoreDataSource Finance 메서드 추가

`FirestoreDataSource.kt`에 아래 두 메서드를 추가하라. 기존 코드는 수정하지 마라.

```kotlin
suspend fun upsertFinance(finance: FinanceEntity)
suspend fun deleteFinance(id: String)
```

- `upsertFinance`: Firestore `finances/{id}` 문서에 `finance.toMap()`을 set한다. (기존 `toMap()` 확장 함수가 있으면 재사용, 없으면 필드를 직접 map으로 변환)
- `deleteFinance`: Firestore `finances/{id}` 문서를 delete한다.
- Firestore 경로: `users/{userId}/finances/{id}` 패턴으로 기존 Todo/Event 경로와 일관되게 맞춰라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `FinanceRepository`에 `create`, `update`, `delete`, `exportCsv` 4개 메서드가 있는가?
   - `delete()`가 `sourceTodoId != null` 항목에 예외를 던지는가?
   - `FirestoreDataSource`에 `upsertFinance`, `deleteFinance`가 추가됐는가?
   - Firestore 경로가 기존 컬렉션 패턴과 일관되는가?
   - syncSafe 패턴이 적용됐는가? (Firestore 실패 시 앱이 크래시되지 않는가?)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/2-finance-automation/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `sourceTodoId`가 있는 Finance 항목을 `delete()`로 삭제하지 마라. 이유: CLAUDE.md CRITICAL 규칙 — Todo 연동 항목은 `isExcluded = true` (Soft Delete)로만 처리한다.
- `userId`에 `"local_user"` 이외의 값을 사용하거나 Firebase Auth를 호출하지 마라. 이유: CLAUDE.md CRITICAL 규칙.
- 날짜를 타임존 변환하지 마라. 이유: CLAUDE.md CRITICAL 규칙 — 날짜는 Floating Time.
- 기존 `observeByMonth()`, `TodoRepository` 코드를 수정하지 마라. 이 step의 범위만 작업하라.
