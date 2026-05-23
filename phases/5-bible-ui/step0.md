# Step 0: memo-entity-dao

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/entity/ReadingPlanEntity.kt` (autoGenerate PK 패턴 참고)
- `app/src/main/java/com/lsync/app/data/local/dao/ReadingPlanDao.kt` (Flow 반환 DAO 패턴 참고)
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` (버전·마이그레이션·엔티티 등록 패턴 참고)
- `app/src/main/java/com/lsync/app/di/AppModule.kt` (@Provides 패턴 참고)

이전 step에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

### 1. MemoEntity 생성

`app/src/main/java/com/lsync/app/data/local/entity/MemoEntity.kt`를 신규 생성하라.

```kotlin
@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val date: String,   // YYYY-MM-DD
)
```

`date`는 `YYYY-MM-DD` 문자열로 저장한다. 타임존 변환 금지.

### 2. MemoDao 생성

`app/src/main/java/com/lsync/app/data/local/dao/MemoDao.kt`를 신규 생성하라.

```kotlin
@Dao
interface MemoDao {
    @Insert
    suspend fun insert(memo: MemoEntity): Long

    @Query("SELECT * FROM memos WHERE book = :book AND chapter = :chapter ORDER BY verse ASC, id ASC")
    fun observeForChapter(book: Int, chapter: Int): Flow<List<MemoEntity>>

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

`observeForChapter`는 `Flow`를 반환해야 한다. `suspend`가 아니다.

### 3. AppDatabase v3 마이그레이션

`app/src/main/java/com/lsync/app/data/local/AppDatabase.kt`를 수정하라:

1. `version`을 `2`에서 `3`으로 올린다.
2. `entities` 배열에 `MemoEntity::class`를 추가한다.
3. `abstract fun memoDao(): MemoDao`를 추가한다.
4. `MIGRATION_2_3` 객체를 추가한다:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `memos` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `book` INTEGER NOT NULL,
                `chapter` INTEGER NOT NULL,
                `verse` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `date` TEXT NOT NULL
            )"""
        )
    }
}
```

5. `addMigrations(...)` 호출에 `MIGRATION_2_3`을 추가한다.

### 4. AppModule에 MemoDao @Provides 추가

`app/src/main/java/com/lsync/app/di/AppModule.kt`에 아래 @Provides를 추가하라. 기존 코드는 수정하지 마라.

```kotlin
@Provides
@Singleton
fun provideMemoDao(db: AppDatabase): MemoDao = db.memoDao()
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `ARCHITECTURE.md` 디렉토리 구조를 따르는가? (`entity/`, `dao/` 위치)
   - `CLAUDE.md` CRITICAL 규칙을 위반하지 않았는가?
   - `AppDatabase`의 `version`이 `3`인가?
   - `MIGRATION_2_3`이 `addMigrations(...)`에 등록됐는가?
   - `MemoEntity`가 `entities` 배열에 추가됐는가?
   - `AppModule`에 `provideMemoDao`가 있는가?
3. 결과에 따라 `phases/5-bible-ui/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `BibleDatabase.kt`나 `EsvDatabase.kt`에 `MemoEntity`를 추가하지 마라. 이유: 묵상 메모는 사용자 데이터이므로 `AppDatabase`(lsync.db)에만 저장한다.
- `fallbackToDestructiveMigration()`을 사용하지 마라. 이유: `AppDatabase`는 사용자 데이터를 담고 있어 파괴적 마이그레이션은 데이터 손실을 초래한다.
- `MIGRATION_2_3` SQL에서 컬럼 타입이나 이름을 `MemoEntity` 필드와 다르게 쓰지 마라. 이유: Room은 마이그레이션 이후 스키마가 Entity 정의와 일치하는지 검증하며, 불일치 시 런타임 크래시가 발생한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
