# Step 0: todo-template-dao

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt`
- `app/src/main/java/com/lsync/app/data/local/entity/TodoTemplateEntity.kt`
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

파일들을 꼼꼼히 읽고 현재 구조를 이해한 뒤 작업하라.

## 작업

### 1. RRULE 라이브러리 의존성 추가

`gradle/libs.versions.toml`에 다음을 추가하라:

```toml
[versions]
# 기존 버전 항목들 유지 ...
lib-recur = "0.15.0"

[libraries]
# 기존 라이브러리 항목들 유지 ...
dmfs-lib-recur = { group = "org.dmfs", name = "lib-recur", version.ref = "lib-recur" }
dmfs-rfc5545-datetime = { group = "org.dmfs", name = "rfc5545-datetime", version.ref = "lib-recur" }
```

`app/build.gradle.kts`의 `dependencies` 블록에 추가하라:

```kotlin
implementation(libs.dmfs.lib.recur)
implementation(libs.dmfs.rfc5545.datetime)
```

### 2. TodoTemplateDao 생성

`app/src/main/java/com/lsync/app/data/local/dao/TodoTemplateDao.kt`를 신규 생성하라.

인터페이스 시그니처:

```kotlin
@Dao
interface TodoTemplateDao {
    @Query("SELECT * FROM todo_templates WHERE userId = :userId AND isActive = 1")
    fun observeActiveTemplates(userId: String): Flow<List<TodoTemplateEntity>>

    @Query("SELECT * FROM todo_templates WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveTemplates(userId: String): List<TodoTemplateEntity>

    @Upsert
    suspend fun upsert(template: TodoTemplateEntity)

    @Query("UPDATE todo_templates SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivate(id: String, updatedAt: Long)
}
```

### 3. AppDatabase 업데이트

`AppDatabase.kt`에 TodoTemplateDao를 추가하라:

```kotlin
abstract fun todoTemplateDao(): TodoTemplateDao
```

### 4. AppModule 업데이트

`di/AppModule.kt`에 @Provides 함수를 추가하라:

```kotlin
@Provides
@Singleton
fun provideTodoTemplateDao(db: AppDatabase): TodoTemplateDao = db.todoTemplateDao()
```

### 5. TodoDao에서 템플릿 메서드 제거

`TodoDao.kt`에 있는 템플릿 관련 메서드들(observeActiveTemplates, upsertTemplate, deactivateTemplate 등)을 제거하라. 이 메서드들은 이제 TodoTemplateDao에서 담당한다.

### 6. TodoRepository 업데이트

`TodoRepository.kt`에서 TodoTemplateDao를 주입받도록 constructor를 수정하고, 템플릿 조회 메서드들이 TodoTemplateDao를 사용하도록 변경하라.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 아키텍처 체크리스트를 확인한다:
   - `data/local/dao/TodoTemplateDao.kt` 파일이 생성됐는가?
   - `AppDatabase`에 `todoTemplateDao()` 추상 함수가 있는가?
   - `AppModule.kt`에 `provideTodoTemplateDao` @Provides가 있는가?
   - `TodoDao.kt`에 중복 템플릿 메서드가 없는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/1-phase1-completion/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- TodoTemplateEntity 자체를 수정하지 마라. 이유: 이 step은 DAO 레이어만 다룬다.
- FinanceDao, EventDao를 수정하지 마라. 이유: 이 step의 범위가 아니다.
- TodoRepository의 비즈니스 로직(complete, uncheck, delete 등)을 수정하지 마라. 이유: constructor 주입 변경과 템플릿 관련 메서드 교체만 허용된다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
