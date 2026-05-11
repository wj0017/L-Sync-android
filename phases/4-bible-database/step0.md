# Step 0: bible-entity-dao

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/local/entity/FinanceEntity.kt` (Entity 패턴 참고)
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt` (DAO 패턴 참고)

## 작업

### 1. BibleVerseEntity 생성

`app/src/main/java/com/lsync/app/data/local/entity/BibleVerseEntity.kt`를 신규 생성하라.

```kotlin
@Entity(tableName = "bible_verses")
data class BibleVerseEntity(
    @PrimaryKey val idx: Int,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val testament: String,
    @ColumnInfo(name = "book_name") val bookName: String,
    @ColumnInfo(name = "book_short") val bookShort: String,
)
```

`tableName = "bible_verses"`와 `@ColumnInfo` 이름이 `assets/bible.db`의 실제 컬럼명과 정확히 일치해야 한다.

### 2. BibleDao 생성

`app/src/main/java/com/lsync/app/data/local/dao/BibleDao.kt`를 신규 생성하라.

```kotlin
@Dao
interface BibleDao {
    @Query("SELECT DISTINCT book, book_name, book_short, testament FROM bible_verses ORDER BY book ASC")
    suspend fun getBooks(): List<BibleBook>

    @Query("SELECT MAX(chapter) FROM bible_verses WHERE book = :book")
    suspend fun getChapterCount(book: Int): Int

    @Query("SELECT * FROM bible_verses WHERE book = :book AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getVerses(book: Int, chapter: Int): List<BibleVerseEntity>

    @Query("SELECT * FROM bible_verses WHERE text LIKE '%' || :query || '%' LIMIT 100")
    suspend fun search(query: String): List<BibleVerseEntity>
}
```

`BibleBook`은 같은 파일 안에 data class로 정의하라:

```kotlin
data class BibleBook(
    val book: Int,
    @ColumnInfo(name = "book_name") val bookName: String,
    @ColumnInfo(name = "book_short") val bookShort: String,
    val testament: String,
)
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `BibleVerseEntity`의 `tableName`이 `"bible_verses"`인가?
   - `@ColumnInfo(name = "book_name")`, `@ColumnInfo(name = "book_short")`가 있는가?
   - `BibleDao`가 `@Dao` 인터페이스인가?
   - `BibleBook` data class가 정의됐는가?
3. 결과에 따라 `phases/4-bible-database/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`

## 금지사항

- `AppDatabase.kt`를 수정하지 마라. 이유: BibleDatabase는 별도 데이터베이스로 분리된다 (Step 1에서 처리).
- `AppModule.kt`를 수정하지 마라. 이유: Step 1에서 처리한다.
- `@ColumnInfo` 없이 camelCase 필드명을 쓰지 마라. 이유: Room은 camelCase를 snake_case로 변환하므로 `bookName` → `book_name`이 되어야 assets DB와 일치한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
