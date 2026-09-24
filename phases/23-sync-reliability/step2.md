# Step 2: sync-retry

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md` — **§7 복원 동기화(pull)**, **§11 웹 클라이언트 동기화 계약**(tombstone·LWW 규칙)
- `docs/PRD.md` — **2.2 Todo ↔ 가계부 생명주기 정책**(삭제·미완료 시 Finance를 지우지 않는다)
- `app/src/main/java/com/lsync/app/data/sync/SyncLogger.kt` — **step 1에서 생성됨**(`SyncTarget` enum 포함)
- `app/src/main/java/com/lsync/app/data/local/dao/SyncLogDao.kt` — **step 0에서 생성됨**
- `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` — `pullAll` 구조
- `app/src/main/java/com/lsync/app/data/remote/FirestoreDataSource.kt` — **어떤 메서드가 `set(merge)`이고 어떤 것이 `batch.update`인지 반드시 확인하라**
- `app/src/main/java/com/lsync/app/worker/MaterializationTrigger.kt` — 일회성 Work 큐잉 패턴(이 step의 트리거가 본뜰 원형)
- `app/src/main/java/com/lsync/app/worker/AlarmRestoreWorker.kt` — `@HiltWorker` 작성 패턴
- `app/src/main/java/com/lsync/app/di/AppModule.kt` — `provideSyncRepository`

step 0~1에서 만들어진 코드를 꼼꼼히 읽고, 로그 행의 어떤 필드가 재전송 키인지 이해한 뒤 작업하라.

## 배경

step 0~1로 실패가 `sync_log`에 남기 시작했다. 이 step은 그 실패를 **실제로 해소**한다.

### 핵심 설계 결정 1 — 재전송은 "Room의 현재 행을 merge upsert"다

실패한 원래 연산을 재현하지 마라. `FirestoreDataSource`의 `uncheckTodoWithFinance`·`deleteTodoUnlinkFinance`는 `batch.update`를 쓰는데, **`update`는 문서가 없으면 실패한다.** 처음 push가 실패했다면 문서가 아예 없을 가능성이 높으므로 같은 연산을 다시 부르면 또 실패한다.

Room이 SSOT이고 원격 머지는 `updatedAt` 기준 last-write-wins이므로, **지금 Room에 있는 행을 `set(merge)`로 올리면** 그 사이 일어난 모든 변경까지 한 번에 반영된다. 멱등이고 순서에 무관하다.

| `target` | 재전송 방법 |
|---|---|
| `events` | `eventDao.getById(id)` → `remote.upsertEvent(it)` |
| `todos` | `todoDao.getById(id)` → `remote.upsertTodo(it)`, 그리고 **`it.linkedFinanceId != null`이면 그 Finance 행도 `remote.upsertFinance`** |
| `finance` | `financeDao.getById(id)` → `remote.upsertFinance(it)` |
| `budgets` | `budgetDao.getById(id)` → `remote.upsertBudget(it)` |
| `pull` | `pullAll(userId)` **1회** — `pull` 행이 여러 개여도 한 번만 실행하고 전부 resolved 처리 |

Room 행이 없으면(조회 결과 `null`) 그 로그는 **resolved로 처리한다.** 이유: 되살릴 원본이 없으므로 영원히 미해결로 남아 화면의 경고가 꺼지지 않는다. 삭제는 전부 tombstone(`deletedAt`)이라 실제로는 거의 발생하지 않는다.

### 핵심 설계 결정 2 — 재시도 경로는 로그를 남기지 않는다 (무한 루프 차단)

`SyncLogger.recordFailure`가 워커를 큐잉하는데, 워커의 재전송이 또 실패해서 그것도 기록되면 → 다시 큐잉 → **무한 순환**이 된다. `ExistingWorkPolicy.KEEP`은 동시 실행만 막지 실행이 끝난 뒤의 재큐잉은 막지 못한다.

→ **`retryFailed` 안에서는 `SyncLogger`를 절대 호출하지 않는다.** 성공하면 기존 행의 `resolvedAt`만 채우고, 실패하면 행을 그대로 둔 채 워커가 `Result.retry()`를 반환해 WorkManager의 지수 백오프에 맡긴다.

부수 효과로 에러율 통계도 깨끗해진다 — 분모가 **사용자의 실제 액션 수**로 유지되고, 재시도가 수치를 부풀리지 않는다.

## 작업

### 1. 파일: `app/src/main/java/com/lsync/app/worker/SyncRetryTrigger.kt` (신규)

`MaterializationTrigger`와 같은 꼴이다. `SyncLogger`는 DAO만 의존하도록 설계돼 `Context`가 없으므로, 큐잉 책임을 이 클래스가 진다.

```kotlin
// 동기화 실패가 기록되면 네트워크 연결 시 재전송하도록 일회성 Work를 예약한다.
@Singleton
class SyncRetryTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule()
}
```

- `Constraints`: `NetworkType.CONNECTED`
- `enqueueUniqueWork("sync_retry", ExistingWorkPolicy.KEEP, request)`
- `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ...)`

### 2. 파일: `app/src/main/java/com/lsync/app/worker/SyncRetryWorker.kt` (신규)

```kotlin
@HiltWorker
class SyncRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params)
```

`doWork()` 규칙:

- `authRepository.currentUserId == null` → `Result.success()`. 로그인하지 않은 상태에서는 재전송할 대상이 없다. `retry()`를 반환하면 로그인할 때까지 워커가 계속 깨어난다.
- `retryFailed(uid)` 결과에 실패가 남아 있으면 `Result.retry()`, 아니면 `Result.success()`.

### 3. 파일: `app/src/main/java/com/lsync/app/data/repository/SyncRepository.kt` (수정)

```kotlin
data class RetryResult(val attempted: Int, val resolved: Int, val failed: Int)

// 미해결 실패를 Room 현재 상태로 재전송한다.
// 이 경로는 sync_log에 새 행을 남기지 않는다(재시도 루프·통계 오염 방지).
suspend fun retryFailed(userId: String): RetryResult
```

- 생성자에 `syncLogDao: SyncLogDao`를 추가하고 **`AppModule.provideSyncRepository`에도 인자를 추가**한다.
- 항목별 예외는 삼키고 `failed` 카운트만 증가시킨다. 한 건의 실패가 나머지 재전송을 막으면 안 된다.
- 성공 시 `syncLogDao.markResolved(log.id, now)`.

### 4. 파일: `app/src/main/java/com/lsync/app/data/sync/SyncLogger.kt` (수정)

생성자에 `private val retryTrigger: SyncRetryTrigger`를 추가하고, **`recordFailure`에서만** `retryTrigger.schedule()`을 호출한다.

## Acceptance Criteria

```powershell
.\gradlew.bat assembleDebug lintDebug --no-configuration-cache
```

```powershell
.\gradlew.bat testDebugUnitTest --no-configuration-cache
```

**AC 판정 기준:** 출력에 `BUILD SUCCESSFUL`이 **보여야** 통과다. 종료 코드만 보고 판단하지 마라 — Bash로 `./gradlew`를 실행하면 JAVA_HOME을 못 찾고 아무 일도 하지 않은 채 종료 코드 0을 반환한 적이 있다. java를 못 찾으면:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:GRADLE_USER_HOME = "C:\ghome"; .\gradlew.bat assembleDebug lintDebug --no-configuration-cache --console=plain
```

## 검증 절차

1. 위 AC 커맨드를 실행하고 `BUILD SUCCESSFUL`을 확인한다. 기존 단위 테스트 64개가 전부 통과해야 한다.
2. **AC로는 재시도 동작을 검증할 수 없다.** 코드를 다시 읽으며 하나씩 확인하라:
   - `retryFailed` 본문에 `syncLogger`(`recordSuccess`/`recordFailure`) 호출이 **하나도 없는가?** 있으면 무한 루프다.
   - 재전송에 `uncheckTodoWithFinance` / `deleteTodoUnlinkFinance` / `softDeleteTodo`를 쓰지 않았는가? (`upsertEvent`/`upsertTodo`/`upsertFinance`/`upsertBudget`만 써야 한다)
   - `todos` 재전송이 `linkedFinanceId`를 따라 Finance 행까지 올리는가?
   - `pull` 행이 여러 개일 때 `pullAll`이 **한 번만** 호출되는가?
   - Room 행이 `null`일 때 resolved 처리하는가? (안 하면 경고가 영구히 남는다)
   - `enqueueUniqueWork`의 정책이 `KEEP`인가?
   - `SyncRetryWorker`가 미로그인 시 `Result.retry()`가 아니라 `Result.success()`를 반환하는가?
3. 아키텍처 체크리스트:
   - ARCHITECTURE.md 디렉토리 구조를 따르는가? (`worker/`, `data/repository/`)
   - CLAUDE.md CRITICAL 규칙: `userId`는 `AuthRepository.currentUserId`를 쓴다(워커에서). **`AppModule.provideSyncRepository`에 `syncLogDao` 인자를 추가했는가?**
   - Room이 SSOT인가? (재전송의 원천은 항상 Room 행이다)
   - Todo 삭제·미완료 시 Finance를 삭제하지 않는 정책을 재전송이 우회하지 않는가? (Room 현재 상태를 그대로 올리므로 정책은 이미 Room에 반영돼 있다)
4. 결과에 따라 `phases/23-sync-reliability/index.json`의 step 2를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- **`retryFailed` 경로에서 `SyncLogger`를 호출하지 마라.** 이유: 재시도 실패 → 기록 → 큐잉 → 재시도의 무한 순환이 되고, 에러율 분모가 사용자 액션 수와 어긋난다.
- **`ExistingWorkPolicy.REPLACE`를 쓰지 마라.** 이유: 실패가 연달아 기록될 때마다 실행 중인 워커를 죽이고 새로 시작해 지수 백오프가 매번 리셋된다.
- **`PeriodicWorkRequest`를 만들지 마라.** 이유: 실패가 없는 날에도 주기적으로 깨어나 배터리를 쓴다. 실패가 기록될 때만 트리거한다.
- **`batch.update` 기반 메서드로 재전송하지 마라.** 문서가 없으면 실패한다.
- **재전송하면서 `updatedAt`을 새로 찍지 마라.** 이유: LWW 기준값이 바뀌어 다른 기기·웹에서 올린 더 최신 변경을 덮어쓴다. Room의 행을 **그대로** 올린다.
- **`SyncRepository.pullAll`·`clearLocalUserData`의 기존 로직을 수정하지 마라.** 검증된 코드다. `retryFailed`를 추가만 한다.
- **화면·ViewModel을 만들지 마라.** step 3이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
