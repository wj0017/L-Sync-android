# Step 1: budget-repository

## 배경

이 task(`16-budget`)는 "가계부 예산 — 카테고리별 + 전체 월 한도, 매월 반복, 초과 경고"를 구현한다. 동기화 포함(Firestore push+pull).

**이전 Step(0)에서 만든 것:**
- `BudgetEntity(id="${userId}_${category}", userId, category, limitAmount, createdAt, updatedAt, deletedAt?)`, `companion TOTAL_CATEGORY="__TOTAL__"`.
- `BudgetDao`: `observeBudgets(userId)`(deletedAt IS NULL), `getById`, `upsert`, `softDelete(id, now)`, `migrateUserId`.
- AppDatabase v5 + `MIGRATION_4_5`, AppModule `provideBudgetDao`.

이 Step은 **데이터 레이어**를 만든다: `BudgetRepository`(Offline-First CRUD) + Firestore push/fetch + 복원 pull + AppModule Repository 등록.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md` — Offline-First(로컬 먼저, syncSafe로 원격), 복원 동기화(pull) 섹션.
- `CLAUDE.md` — userId는 `AuthRepository.currentUserId`(하드코딩 금지), syncSafe로 `sync_failed` Crashlytics, 새 Repository는 AppModule 등록 CRITICAL.
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` — **Repository 레퍼런스.** 로컬 upsert 후 `syncSafe { remote... }`, Crashlytics `sync_failed` 패턴.
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `syncSafe` 헬퍼 형태.
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — **수정 대상.** `upsertEvent`/`fetchEvents`/`DocumentSnapshot.toEventEntity()`, 컬렉션 push/fetch·필드 매핑 패턴. 예산도 동일 패턴(컬렉션 `budgets`).
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` — **수정 대상.** `pullAll(userId)`가 `fetchEvents/fetchTodos/fetchFinance`를 받아 **last-write-wins upsert-only**(항목별 `updatedAt > local.updatedAt`일 때만 upsert) 머지. 엔티티별 `syncSafe` 격리.
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — **수정 대상.** `provideFinanceRepository`/`provideEventRepository` 패턴(DAO+remote+필요시 알람 주입). `provideSyncRepository`.

## 작업

### 1) `BudgetRepository` (`data/repository/BudgetRepository.kt`)

```kotlin
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val remote: FirestoreDataSource,
) {
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>> = dao.observeBudgets(userId)

    // 한도 설정/수정 — 결정론적 id로 upsert(멱등). limitAmount<=0이면 삭제로 간주할지 호출자 정책(여기선 그대로 저장, UI에서 처리).
    suspend fun setBudget(userId: String, category: String, limitAmount: Long)

    // soft delete + 동기화
    suspend fun deleteBudget(id: String)
}
```

- `setBudget`: `id = "${userId}_${category}"`로 `BudgetEntity`를 만들어 **로컬 upsert 먼저**, 그 후 `syncSafe { remote.upsertBudget(entity) }`. `createdAt`은 기존 행이 있으면 보존(가능하면 `getById`로 조회 후 보존), 없으면 now.
- `deleteBudget`: `dao.softDelete(id, now)` 후 `syncSafe { remote.upsertBudget(<deletedAt 채운 엔티티>) }` 또는 `remote.deleteBudget(id)`. **soft delete 전파**가 목적이므로 deletedAt이 원격에도 반영돼야 한다(Todo deletedAt 패턴 참고).

### 2) FirestoreDataSource — budgets 컬렉션

- `suspend fun upsertBudget(entity: BudgetEntity)` — `budgets` 컬렉션에 `entity.id` 문서로 set. 필드: id/userId/category/limitAmount/createdAt/updatedAt/deletedAt.
- `suspend fun fetchBudgets(userId: String): List<BudgetEntity>` — `whereEqualTo("userId", userId)` 조회 후 `toBudgetEntity()` 매핑.
- `DocumentSnapshot.toBudgetEntity(): BudgetEntity?` — `runCatching` 방어 매핑(다른 toEntity와 동일).
- 기존 보안 규칙(`request.auth.uid == resource.data.userId`)이 `{collection}` 와일드카드라 budgets에도 자동 적용 — 규칙 변경 불필요.

### 3) SyncRepository.pullAll — budgets 복원

- `pullAll(userId)`에 budgets 머지를 추가한다: `remote.fetchBudgets(userId)`를 받아 **last-write-wins upsert-only**(원격 `updatedAt > local.updatedAt`일 때만 `dao.upsert`). deletedAt이 채워져 오면 upsert로 soft delete 복원. 엔티티별 `syncSafe`로 격리(부분 실패 무방).

### 4) AppModule

- `@Provides @Singleton fun provideBudgetRepository(dao: BudgetDao, remote: FirestoreDataSource): BudgetRepository`를 추가한다.
- `provideSyncRepository`가 `BudgetDao`/`BudgetRepository`를 새로 필요로 하면 시그니처에 주입을 추가한다(SyncRepository가 dao를 직접 upsert하면 BudgetDao 주입).

## 핵심 규칙 (반드시 지킬 것)

- **Offline-First: 로컬 upsert 먼저, 원격은 `syncSafe`.** 네트워크 실패가 로컬 저장/UI를 막으면 안 된다. 실패는 Crashlytics `sync_failed`.
- **userId는 `AuthRepository.currentUserId`.** Repository는 호출자(ViewModel)가 넘긴 userId를 쓰되 `"local_user"` 하드코딩 금지.
- **삭제는 soft delete + deletedAt 원격 전파.** 이유: pull upsert-only라 hard delete는 다른 기기에 반영 안 됨.
- **pull은 last-write-wins upsert-only.** 로컬이 같거나 최신이면 보존. 로컬 전용 데이터 삭제 금지(기존 SyncRepository 규칙 일관).
- **BudgetRepository를 AppModule에 `@Provides` 등록.** 누락 시 런타임 크래시(CLAUDE.md CRITICAL).
- 이 Step은 **데이터 레이어**. ViewModel·UI 변경 금지(Step 2~4).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `BudgetRepository`가 로컬 먼저+`syncSafe` 원격인가? AppModule 등록됐는가?
   - FirestoreDataSource에 upsertBudget/fetchBudgets/toBudgetEntity가 추가됐는가?
   - `pullAll`에 budgets last-write-wins upsert-only 머지가 추가됐는가?
   - 삭제가 soft delete + deletedAt 전파인가?
3. 결과에 따라 `phases/16-budget/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "BudgetRepository(setBudget 멱등 upsert+syncSafe, deleteBudget soft delete 전파)·FirestoreDataSource budgets 컬렉션(upsertBudget/fetchBudgets/toBudgetEntity)·SyncRepository.pullAll budgets 머지(LWW upsert-only)·AppModule provideBudgetRepository 추가"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 원격 동기화를 로컬 저장보다 먼저 하지 마라(Offline-First 위반).
- hard delete로 예산을 지우지 마라. 이유: 다른 기기 전파 불가.
- Firestore 보안 규칙을 바꾸지 마라. 이유: `{collection}` 와일드카드라 budgets에 자동 적용.
- ViewModel/UI를 만들지 마라(Step 2~4).
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
