# Step 1: sync-logger

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — **§7 트랜잭션 및 모니터링**(현재의 Crashlytics `sync_failed` 기록 방식)
- `app/src/main/java/com/lsync/app/data/local/entity/SyncLogEntity.kt` — **step 0에서 생성됨**
- `app/src/main/java/com/lsync/app/data/local/dao/SyncLogDao.kt` — **step 0에서 생성됨**
- `app/src/main/java/com/lsync/app/data/repository/EventRepository.kt` — `syncSafe` 정의(파일 끝)와 호출부
- `app/src/main/java/com/lsync/app/data/repository/TodoRepository.kt`
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt`
- `app/src/main/java/com/lsync/app/data/repository/BudgetRepository.kt`
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` — 여기의 `syncSafe`만 `suspend`다
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — 위 5개 Repository는 전부 `@Provides`로 생성자 인자를 **명시적으로** 넘긴다

step 0에서 만들어진 엔티티·DAO를 꼼꼼히 읽고, 컬럼의 의미를 이해한 뒤 작업하라.

## 배경

`syncSafe`는 이 프로젝트에 **5벌의 사본**으로 존재한다. 4개 Repository(Event/Todo/Finance/Budget)의 것은 `appScope.launch`로 감싼 fire-and-forget이고, `SyncRepository`의 것만 `suspend` 함수다. 이 차이는 의도된 것이다 — 원격 push는 오프라인에서 서버 ack를 기다리며 hang되면 안 되고, pull은 호출자가 완료를 기다려야 한다.

이 step은 그 5벌 전부가 **시도 결과를 `sync_log`에 남기도록** 만든다.

### 핵심 설계 결정 1 — `SyncLogger`는 `AuthRepository`를 주입받지 않는다

`AuthRepository`는 생성자에서 `SyncRepository`를 주입받고 `init`에서 `pullAll`을 호출한다(`AuthRepository.kt:22`). 따라서 `SyncRepository → SyncLogger → AuthRepository → SyncRepository` 순환이 생겨 **Hilt 컴파일이 실패한다.**

→ `userId`는 **호출부가 인자로 넘긴다.** 모든 `syncSafe` 호출부의 스코프에는 해당 엔티티가 있고, 엔티티에는 `userId` 필드가 있다.

### 핵심 설계 결정 2 — 원격 연산 1회 = 로그 1행

`TodoRepository`의 일부 연산은 Firestore Batch Write로 Todo와 Finance를 **한 번에** 쓴다(`completeTodoWithFinance` 등). 이때 로그를 2행(todos + finance) 남기지 마라. 원격 호출이 1회이므로 성공·실패도 1건이다. 2행을 남기면 에러율의 분모가 사용자의 실제 액션 수와 어긋나 게이트 해석이 불가능해진다.

## 작업

### 1. 파일: `app/src/main/java/com/lsync/app/data/sync/SyncLogger.kt` (신규)

```kotlin
package com.lsync.app.data.sync

// 동기화 대상 구분 — sync_log.target에 저장되는 문자열의 단일 출처.
enum class SyncTarget(val key: String) {
    EVENTS("events"),
    TODOS("todos"),
    FINANCE("finance"),
    BUDGETS("budgets"),
    PULL("pull"),
}

// 원격 동기화 시도를 Room에 기록한다. 로컬 전용 진단 — Firestore에 올리지 않는다.
// AuthRepository를 주입받지 않는다(DI 순환). userId는 호출부가 엔티티에서 넘긴다.
@Singleton
class SyncLogger @Inject constructor(
    private val dao: SyncLogDao,
) {
    suspend fun recordSuccess(target: SyncTarget, userId: String, entityId: String?)

    suspend fun recordFailure(target: SyncTarget, userId: String, entityId: String?, error: Throwable)
}
```

구현 규칙:

- `errorType` = `error::class.java.simpleName`, `errorMessage` = `error.message?.take(200)`. **message 전문을 그대로 넣지 마라** — Firestore 예외 메시지는 길고, 화면 목록에서 한 줄로 보여줄 것이다.
- `createdAt` = `System.currentTimeMillis()`, `resolvedAt` = `null`.
- **가지치기:** 삽입 카운터를 메모리에 두고 **50회마다 1번** `dao.pruneOlderThan(now - 30일)`을 호출한다. 매 삽입마다 DELETE를 돌리면 사용자 액션마다 쓰기가 2배가 된다. 카운터는 `@Singleton`이므로 프로세스 수명 동안만 유지되면 충분하다.
- 기록 자체가 실패해도 호출부를 막지 마라(`runCatching`으로 감싼다). 진단 기록 때문에 사용자 데이터 저장이 깨지면 본말전도다.

### 2. 5개 `syncSafe`에 기록 추가 (Repository 수정)

각 Repository의 `private fun syncSafe`(또는 `private suspend fun`) 시그니처를 아래로 바꾼다:

```kotlin
private fun syncSafe(
    target: SyncTarget,
    userId: String,
    entityId: String?,
    block: suspend () -> Unit,
)
```

본문 규칙:

- `try { block(); syncLogger.recordSuccess(target, userId, entityId) }`
- `catch (e: Exception) { /* 기존 Crashlytics 블록 그대로 */ ; syncLogger.recordFailure(target, userId, entityId, e) }`
- **기존 Crashlytics 기록을 지우지 마라.** 원격 모니터링 경로를 잃는다. 두 곳 모두에 남긴다.
- `SyncRepository`의 `syncSafe`는 `suspend`이고 `appScope.launch`가 없다. 그 형태를 유지한 채 인자만 추가한다.

호출부에 넘길 값:

| Repository | target | userId | entityId |
|---|---|---|---|
| `EventRepository` | `SyncTarget.EVENTS` | 해당 이벤트 엔티티의 `userId` | 해당 엔티티의 `id` |
| `TodoRepository` | `SyncTarget.TODOS` | 해당 Todo 엔티티의 `userId` | **Todo의 `id`**(Finance와 묶인 배치 연산도 마찬가지) |
| `FinanceRepository` | `SyncTarget.FINANCE` | 해당 Finance 엔티티의 `userId` | 해당 엔티티의 `id` (정산 그룹 루프에서는 그 `member`의 값) |
| `BudgetRepository` | `SyncTarget.BUDGETS` | 해당 예산 엔티티의 `userId` | 해당 엔티티의 `id` |
| `SyncRepository.pullAll` | `SyncTarget.PULL` | `pullAll(userId)`의 파라미터 | `null` |

각 Repository 생성자에 `private val syncLogger: SyncLogger`를 추가하고, **`di/AppModule.kt`의 해당 `@Provides` 함수에도 파라미터를 추가**한다 — 이 프로젝트의 Repository는 `@Inject constructor`가 아니라 `@Provides`가 인자를 하나씩 넘기는 방식이라 빠뜨리면 컴파일 에러가 난다(`provideEventRepository`, `provideTodoRepository`, `provideFinanceRepository`, `provideBudgetRepository`, `provideSyncRepository`).

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

**AC 판정 기준:** 출력에 `BUILD SUCCESSFUL`이 **보여야** 통과다. 종료 코드만 보고 판단하지 마라 — Bash로 `./gradlew`를 실행하면 JAVA_HOME을 못 찾고 아무 일도 하지 않은 채 종료 코드 0을 반환한 적이 있다. PowerShell에서 `.\gradlew.bat`으로 실행하고 출력 문자열을 직접 확인하라. java를 못 찾으면:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:GRADLE_USER_HOME = "C:\ghome"; .\gradlew.bat assembleDebug lintDebug --no-configuration-cache --console=plain
```

## 검증 절차

1. 위 AC 커맨드를 실행하고 `BUILD SUCCESSFUL`을 확인한다. 기존 단위 테스트 64개가 전부 통과해야 한다.
2. 코드를 다시 읽으며 확인하라:
   - `syncSafe` **호출부를 하나도 빠뜨리지 않았는가?** `grep -rn "syncSafe" app/src/main/java`로 세어보라 — 정의 5개 + 호출부 20개 안팎이며, 인자 없이 남은 호출부가 있으면 컴파일 에러로 드러난다.
   - 배치 연산(`completeTodoWithFinance` / `uncheckTodoWithFinance` / `deleteTodoUnlinkFinance`)에 로그가 **1행씩만** 남는가?
   - 기존 Crashlytics `recordException` + `log("sync_failed")` 블록이 **전부 그대로** 남아 있는가?
   - `SyncLogger`가 `AuthRepository`를 주입받지 않는가? (주입하면 Hilt 순환으로 컴파일이 깨진다)
   - `AppModule`의 5개 `@Provides`에 `syncLogger` 인자가 추가됐는가?
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`data/sync/`는 신규 디렉토리다 — step 4에서 문서에 반영한다)
   - CLAUDE.md CRITICAL 규칙: `SyncLogger`는 `@Inject constructor`이므로 별도 `@Provides`가 필요 없다. 다만 **Repository provider들의 인자 추가는 필수**다.
   - Room이 SSOT인가? (기록은 Room에만 남는다)
4. 결과에 따라 `phases/23-sync-reliability/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **5벌의 `syncSafe`를 공통 유틸 하나로 통합하지 마라.** 이유: 4개는 `appScope.launch` fire-and-forget이고 `SyncRepository`의 것만 `suspend`다. 동작이 다른 코드를 억지로 합치면 오프라인에서 호출부가 hang되는 회귀가 난다(2026-07-05에 이미 해결한 문제다). 시그니처만 맞추고 사본은 그대로 둔다.
- **Crashlytics 기록을 제거하거나 조건부로 만들지 마라.** 로컬 기록과 원격 기록은 목적이 다르다.
- **`SyncLogger`에 재시도·WorkManager 코드를 넣지 마라.** 재시도는 step 2다. 이 step의 `SyncLogger`는 DAO 하나만 의존한다.
- **`"local_user"` 같은 폴백 userId를 만들지 마라.** 엔티티에 이미 실제 `userId`가 들어 있다.
- **로그 기록 실패가 원본 작업을 롤백하게 만들지 마라.** 진단이 데이터를 이길 수 없다.
- **ViewModel·화면을 만들지 마라.** step 3이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
