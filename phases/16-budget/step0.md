# Step 0: budget-entity

## 배경

이 task(`16-budget`)는 "가계부 예산(Budget) — 카테고리별 + 전체 월 한도, 매월 반복, 초과 경고"를 구현한다.

설계 결정(확정):
- **단위:** 카테고리별 한도 + 전체 월 한도.
- **주기:** 매월 반복 **단일 한도**(한 번 설정하면 매달 적용. 월별 개별 입력 없음).
- **동기화:** Firestore push+pull 포함(다른 엔티티와 동일).

이 Step은 **Room 스키마 레이어만** 만든다: `BudgetEntity`, `BudgetDao`, AppDatabase v5 마이그레이션, AppModule DAO 등록. Repository·ViewModel·UI·Firestore는 이후 step.

## 읽어야 할 파일

- `docs/ARCHITECTURE.md`, `docs/TechSpec.md` 1·2장(스키마·마이그레이션), `CLAUDE.md`(DI 등록 CRITICAL, userId 규칙).
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt` — 엔티티 컨벤션(id/userId/createdAt/updatedAt).
- `app/src/main/java/com/lsync/app/data/local/entity/TodoEntity.kt` — `deletedAt` soft delete 컨벤션(pull 복원이 upsert-only라 삭제 전파에 필요).
- `app/src/main/java/com/lsync/app/data/local/FinanceCategory.kt` — `FinanceCategory.all`(7개 카테고리). 예산 카테고리 값은 이 상수를 따른다.
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` — DAO 패턴(`observeByDateRange`, `@Upsert`, soft delete 쿼리).
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` — **수정 대상.** 현재 `version = 4`, entities 목록, `MIGRATION_1_2/2_3/3_4`. 마지막 마이그레이션 `ALTER TABLE finance ADD COLUMN settlementGroupId TEXT`(3→4).
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — **수정 대상.** `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`(L~43), `@Provides fun provideFinanceDao(db) = db.financeDao()`(L~49).

## 작업

### 1) `BudgetEntity` (`data/local/entity/BudgetEntity.kt`)

```kotlin
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,   // 결정론적: "${userId}_${category}" — 멱등 upsert(카테고리당 1행)
    val userId: String,
    val category: String,         // FinanceCategory 값. 전체 한도는 sentinel TOTAL_CATEGORY
    val limitAmount: Long,        // 매월 반복 한도(원)
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,  // soft delete — pull(upsert-only) 삭제 전파용
) {
    companion object { const val TOTAL_CATEGORY = "__TOTAL__" }
}
```

### 2) `BudgetDao` (`data/local/dao/BudgetDao.kt`)

```kotlin
@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId AND deletedAt IS NULL")
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Upsert suspend fun upsert(budget: BudgetEntity)

    @Query("UPDATE budgets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // 마이그레이션(Auth) 일관: userId 일괄 변경
    @Query("UPDATE budgets SET userId = :newId WHERE userId = :oldId")
    suspend fun migrateUserId(oldId: String, newId: String)

    // pull 복원용 — deletedAt 포함 전체 조회는 필요 시 추가
}
```

`observeBudgets`는 `deletedAt IS NULL`만. soft delete된 행은 표시에서 제외.

### 3) AppDatabase v5

- `version = 5`로 올리고 entities에 `BudgetEntity::class` 추가, `abstract fun budgetDao(): BudgetDao` 추가.
- `MIGRATION_4_5`를 추가한다. **CREATE TABLE 문이 Room이 기대하는 스키마와 정확히 일치해야 한다**(컬럼명/타입/NOT NULL/nullable). 예:
  ```sql
  CREATE TABLE IF NOT EXISTS `budgets` (
    `id` TEXT NOT NULL,
    `userId` TEXT NOT NULL,
    `category` TEXT NOT NULL,
    `limitAmount` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    `deletedAt` INTEGER,
    PRIMARY KEY(`id`)
  )
  ```
  (Long→INTEGER, String→TEXT, nullable=NOT NULL 생략.)

### 4) AppModule DI

- `addMigrations(...)`에 `AppDatabase.MIGRATION_4_5`를 추가한다.
- `@Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()`를 추가한다.

## 핵심 규칙 (반드시 지킬 것)

- **MIGRATION_4_5의 CREATE TABLE이 BudgetEntity 스키마와 정확히 일치해야 한다.** 이유: Room이 스키마 해시를 검증하므로 불일치 시 앱 시작 즉시 크래시(`IllegalStateException: migration didn't properly handle`).
- **BudgetDao를 AppModule에 `@Provides` 등록하라.** 이유: 누락 시 Hilt 의존성 못 찾아 런타임 크래시(CLAUDE.md CRITICAL).
- **`id = "${userId}_${category}"` 결정론적.** 이유: 카테고리당 1행 멱등 upsert(중복 예산 방지).
- **삭제는 `deletedAt` soft delete.** 이유: pull이 upsert-only라 hard delete는 다른 기기에 전파 안 됨(Todo 패턴).
- **카테고리 값은 `FinanceCategory.all` + sentinel `TOTAL_CATEGORY`.** 임의 문자열 금지.
- 이 Step은 **스키마 레이어만**. Repository·ViewModel·UI·Firestore 변경 금지(이후 step).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `BudgetEntity`/`BudgetDao` 생성, AppDatabase v5 + `MIGRATION_4_5` 추가됐는가?
   - CREATE TABLE이 엔티티 스키마와 정확히 일치하는가?(타입/NOT NULL/PK)
   - AppModule에 `addMigrations`에 4_5, `provideBudgetDao`가 추가됐는가?
   - id 결정론적·deletedAt soft delete·카테고리 상수 사용?
3. 결과에 따라 `phases/16-budget/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "BudgetEntity(id=userId_category 결정론적, deletedAt soft delete, TOTAL_CATEGORY sentinel)·BudgetDao(observeBudgets/upsert/softDelete/migrateUserId)·AppDatabase v5 MIGRATION_4_5·AppModule provideBudgetDao+addMigrations 추가"`
   - 수정 3회 후에도 실패 → `"status": "error"`, `"error_message": "..."`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "..."` 후 중단

## 금지사항

- 마이그레이션 없이 version만 올리지 마라. 이유: 기존 사용자 DB 크래시.
- `fallbackToDestructiveMigration`을 추가하지 마라. 이유: 실제 데이터 삭제 위험.
- DAO/Repository/ViewModel/UI/Firestore 로직을 이 Step에서 만들지 마라. 스키마만.
- 기존 마이그레이션(1_2/2_3/3_4)을 수정하지 마라.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
