# Step 1: bible-database

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/local/entity/BibleVerseEntity.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/dao/BibleDao.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` (기존 DB 참고용)
- `app/src/main/java/com/lsync/app/di/AppModule.kt` (@Provides 패턴 참고)

## 작업

### 1. BibleDatabase 생성

`app/src/main/java/com/lsync/app/data/local/BibleDatabase.kt`를 신규 생성하라.

```kotlin
@Database(
    entities = [BibleVerseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
}
```

기존 `AppDatabase`와 완전히 분리된 별도 데이터베이스다. `AppDatabase`를 수정하지 마라.

### 2. AppModule에 BibleDatabase 및 BibleDao 추가

`di/AppModule.kt`에 아래 두 @Provides 함수를 추가하라. 기존 코드는 수정하지 마라.

```kotlin
@Provides
@Singleton
fun provideBibleDatabase(@ApplicationContext context: Context): BibleDatabase =
    Room.databaseBuilder(context, BibleDatabase::class.java, "bible.db")
        .createFromAsset("bible.db")
        .fallbackToDestructiveMigration()
        .build()

@Provides
@Singleton
fun provideBibleDao(db: BibleDatabase): BibleDao = db.bibleDao()
```

**핵심 규칙:**
- `createFromAsset("bible.db")` — `app/src/main/assets/bible.db` 파일을 첫 실행 시 자동 복사한다. 이 파일은 이미 존재한다.
- `fallbackToDestructiveMigration()` — 읽기 전용 데이터이므로 스키마 변경 시 assets에서 재생성해도 무방하다.
- 데이터베이스 파일명 `"bible.db"`는 assets 파일명과 정확히 일치해야 한다.
- `@ApplicationContext`를 통해 Context를 주입받는다 (기존 AppModule 패턴과 동일).

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `BibleDatabase.kt`가 `data/local/` 하위에 생성됐는가?
   - `AppModule.kt`에 `provideBibleDatabase`, `provideBibleDao` 두 @Provides가 추가됐는가?
   - `AppDatabase.kt`가 변경되지 않았는가?
   - `createFromAsset("bible.db")`가 사용됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/4-bible-database/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`

## 금지사항

- `AppDatabase.kt`에 `BibleVerseEntity`를 추가하지 마라. 이유: 사용자 데이터(todo, finance 등)와 성경 데이터는 완전히 분리된 별도 DB여야 한다.
- `createFromAsset` 없이 빈 DB로 생성하지 마라. 이유: bible.db 없이 앱을 실행하면 성경 데이터가 비어있다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
