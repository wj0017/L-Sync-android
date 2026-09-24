# Step 0: sync-log-entity

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — **§1.6 `budgets`**(가장 최근에 추가된 테이블 — 컬럼 주석·문서화 스타일 참고), **§2 AppDatabase 마이그레이션**
- `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` — **현재 version = 6.** `MIGRATION_5_6`까지 정의돼 있다
- `app/src/main/java/com/lsync/app/data/local/entity/BudgetEntity.kt` — 엔티티 작성 스타일 참고
- `app/src/main/java/com/lsync/app/data/local/dao/BudgetDao.kt` — DAO 작성 스타일 참고
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — `provideDatabase`의 `addMigrations(...)` 목록과 `provideXxxDao` 패턴

## 배경

이 앱의 원격 동기화 실패는 **전부 조용히 삼켜진다.** 각 Repository의 `syncSafe`가 `catch (e: Exception)` 안에서 Crashlytics에 기록만 하고 통과시키기 때문에, 사용자는 어떤 항목이 Firestore에 올라가지 않았는지 알 방법이 없다.

PRD Phase 1 게이트는 **"동기화 에러율 5% 미만"**을 요구하는데, 지금은 이 수치를 앱 안에서 측정할 수단 자체가 없다.

이 phase는 동기화 시도를 로컬에 기록하고(step 0~1), 실패한 것을 재전송하고(step 2), 그 현황을 화면으로 보여준다(step 3). **이 step은 그중 Room 레이어만 만든다.**

### 왜 성공까지 기록하는가

에러율은 `실패 / 전체 시도`다. 실패만 기록하면 분모가 없어 게이트 판정이 불가능하다. 대신 무한 증가를 막기 위해 보존 기간 상한을 둔다(step 1에서 가지치기 구현).

## 작업

### 1. 파일: `app/src/main/java/com/lsync/app/data/local/entity/SyncLogEntity.kt` (신규)

```kotlin
package com.lsync.app.data.local.entity

// 원격 동기화 시도 1건의 기록. 로컬 전용 진단 데이터 — Firestore에 동기화하지 않는다.
@Entity(
    tableName = "sync_log",
    indices = [Index(value = ["userId", "createdAt"])],
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val target: String,        // "events" / "todos" / "finance" / "budgets" / "pull"
    val entityId: String?,     // 재전송 키. target이 "pull"이면 null
    val success: Boolean,
    val errorType: String?,    // 예외 클래스 simpleName
    val errorMessage: String?, // 예외 message 앞 200자
    val createdAt: Long,
    val resolvedAt: Long?,     // 재시도로 해소된 시각. null + success=false = 미해결 실패
)
```

### 2. 파일: `app/src/main/java/com/lsync/app/data/local/dao/SyncLogDao.kt` (신규)

아래 시그니처를 만들되 쿼리 본문은 재량에 맡긴다.

```kotlin
@Dao
interface SyncLogDao {
    // 최근 로그 (화면 목록용) — createdAt 내림차순
    fun observeRecent(userId: String, limit: Int): Flow<List<SyncLogEntity>>

    // 미해결 실패 건수 (success = 0 AND resolvedAt IS NULL)
    fun observeUnresolvedCount(userId: String): Flow<Int>

    // 에러율 분모 / 분자 — since 이후 시도 수, 실패 수
    fun observeAttemptsSince(userId: String, since: Long): Flow<Int>
    fun observeFailuresSince(userId: String, since: Long): Flow<Int>

    // 재시도 대상 (오래된 것부터)
    suspend fun getUnresolvedFailures(userId: String): List<SyncLogEntity>

    suspend fun insert(log: SyncLogEntity)
    suspend fun markResolved(id: Long, now: Long)

    // 보존 기간 초과 행 삭제
    suspend fun pruneOlderThan(cutoff: Long)
}
```

### 3. 파일: `app/src/main/java/com/lsync/app/data/local/AppDatabase.kt` (수정)

- `entities` 배열에 `SyncLogEntity::class` 추가
- `version = 6` → **`version = 7`**
- `abstract fun syncLogDao(): SyncLogDao` 추가
- `MIGRATION_6_7` 추가 — `CREATE TABLE IF NOT EXISTS` + **인덱스 생성**

**반드시 지킬 규칙 — 인덱스 이름:**

`MIGRATION_6_7`이 만드는 인덱스 이름은 Room이 `@Index`에서 생성하는 이름과 **문자 단위로 같아야 한다.** Room의 규칙은 `index_{테이블}_{컬럼1}_{컬럼2}`이므로 이 경우 `index_sync_log_userId_createdAt`이다.

```sql
CREATE INDEX IF NOT EXISTS `index_sync_log_userId_createdAt` ON `sync_log` (`userId`, `createdAt`)
```

이름이 다르면 컴파일은 통과하고 **앱 실행 시점에** `IllegalStateException: Migration didn't properly handle sync_log`로 죽는다. 컬럼 타입도 마찬가지다 — `Boolean` → `INTEGER NOT NULL`, `Long?` → `INTEGER`(NULL 허용), `String?` → `TEXT`(NULL 허용), `@PrimaryKey(autoGenerate = true) Long` → `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`.

### 4. 파일: `app/src/main/java/com/lsync/app/di/AppModule.kt` (수정)

- `provideDatabase`의 `.addMigrations(...)` 끝에 **`AppDatabase.MIGRATION_6_7` 추가**
- `@Provides fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()` 추가

마이그레이션 등록을 빠뜨리면 컴파일은 통과하지만 기존 설치 기기에서 앱이 실행 즉시 크래시한다.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

**AC 판정 기준:** 출력에 `BUILD SUCCESSFUL`이 **보여야** 통과다. 종료 코드만 보고 판단하지 마라 — 이 환경에서 Bash로 `./gradlew`를 실행하면 JAVA_HOME을 못 찾고 **아무 일도 하지 않은 채 종료 코드 0**을 반환한 적이 있다. 위처럼 PowerShell에서 `.\gradlew.bat`으로 실행하고 출력 문자열을 직접 확인하라. 그래도 java를 못 찾으면 아래로 실행한다:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:GRADLE_USER_HOME = "C:\ghome"; .\gradlew.bat assembleDebug lintDebug --no-configuration-cache --console=plain
```

## 검증 절차

1. 위 AC 커맨드를 실행하고 출력에서 `BUILD SUCCESSFUL`을 확인한다. **기존 단위 테스트 64개가 전부 통과해야 한다**(EventRecurrence 18 / PaymentNotificationParser 16 / ReminderSchedule 11 / RecurrenceOptions 15 / ReportSummaryText 4).
2. **Room 스키마 검증은 컴파일로 잡히지 않는다.** `MIGRATION_6_7`의 `CREATE TABLE` 문과 `SyncLogEntity`를 나란히 놓고 한 줄씩 대조하라:
   - 컬럼 이름·개수가 정확히 일치하는가?
   - NOT NULL 여부가 Kotlin 타입의 nullable 여부와 일치하는가?
   - 인덱스 이름이 `index_sync_log_userId_createdAt`인가?
   - `AppModule.provideDatabase`의 `addMigrations`에 `MIGRATION_6_7`이 들어갔는가?
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`data/local/entity/`, `data/local/dao/`)
   - CLAUDE.md CRITICAL 규칙: **새 DAO를 추가했으므로 `di/AppModule.kt`에 `@Provides`가 반드시 있어야 한다.**
   - Room이 SSOT인가? (이 step은 로컬 전용 테이블만 만든다)
4. 결과에 따라 `phases/23-sync-reliability/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`fallbackToDestructiveMigration()`을 추가하지 마라.** 이유: 마이그레이션이 틀렸을 때 사용자의 일정·할일·가계부·통독 데이터가 전부 삭제된다. 마이그레이션은 직접 작성한다.
- **`FirestoreDataSource`를 수정하지 마라.** `sync_log`는 로컬 전용 진단 데이터다. 동기화 상태를 기록하는 테이블이 그 자체로 동기화 대상이 되면 순환이고, 오프라인에서 쌓인 기록이 다시 실패를 만든다.
- **`SyncRepository.clearLocalUserData()`에 `syncLogDao.clearAll()`을 추가하지 마라.** 이유: 로그아웃 시 게이트 측정 이력이 사라진다. `reading_plan`·`memos`와 같은 취급(로컬 전용·복원 불가라 보존)이며, 다른 계정에서의 노출은 모든 조회가 `userId`로 필터되는 것으로 막는다.
- **기존 `MIGRATION_1_2` ~ `MIGRATION_5_6`을 수정·재정렬하지 마라.** 이미 배포된 기기에서 실행된 마이그레이션이다.
- **Repository·ViewModel·UI를 만들지 마라.** 이 step은 Room 레이어까지다. 기록 로직은 step 1, 재시도는 step 2, 화면은 step 3이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
