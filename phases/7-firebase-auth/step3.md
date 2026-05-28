# Step 3: userid-replace

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt`  ← step 0에서 생성됨
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt`
- `app/src/main/java/com/lsync/app/ui/schedule/ScheduleViewModel.kt`
- `app/src/main/java/com/lsync/app/worker/TodoMaterializerWorker.kt`
- `app/src/main/java/com/lsync/app/ui/widget/WidgetEntryPoint.kt`
- `app/src/main/java/com/lsync/app/ui/widget/LSyncWidget.kt`
- `app/src/main/java/com/lsync/app/ui/widget/WidgetState.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

## 이전 step 요약

Step 0: `di/Qualifiers.kt`, `data/repository/AuthRepository.kt`(`currentUserId`, `authState`, `signInWithGoogle`, `signOut`), `AppModule.kt`(FirebaseAuth + ApplicationScope providers), `play-services-auth` 의존성
Step 1: `ui/auth/AuthViewModel.kt` — `AuthUiState`, `@HiltViewModel` 패턴
Step 2: `ui/auth/LoginScreen.kt`, `NavGraph.kt`(auth branching), `MainActivity.kt`(permission gating)

## 배경

현재 프로젝트에 `"local_user"` 하드코딩이 7곳에 존재한다:
1. `FinanceRepository.kt` — `create()`, `addReimbursement()`, `exportCsv()`
2. `ScheduleViewModel.kt` — `createEvent()`, `createTodo()`
3. `TodoMaterializerWorker.kt` — `companion object { const val USER_ID = "local_user" }`
4. `LSyncWidget.kt` — `financeDao.getAllByDateRange("local_user", ...)`

이 step에서 모두 `AuthRepository.currentUserId`로 교체한다.

## 작업

### 1. `FinanceRepository.kt` 수정

`AuthRepository`를 생성자에 추가하고 `"local_user"` 3곳을 교체한다.

```kotlin
@Singleton
class FinanceRepository @Inject constructor(
    private val dao: FinanceDao,
    private val remote: FirestoreDataSource,
    private val authRepository: AuthRepository,
) {
    private val currentUserId: String
        get() = authRepository.currentUserId
            ?: error("User not signed in")
    // create(), addReimbursement(), exportCsv() 내부의 "local_user" → currentUserId로 교체
}
```

### 2. `AppModule.kt` 수정 — `provideFinanceRepository` 시그니처 변경

```kotlin
@Provides
@Singleton
fun provideFinanceRepository(
    dao: FinanceDao,
    remote: FirestoreDataSource,
    authRepository: AuthRepository,
): FinanceRepository = FinanceRepository(dao, remote, authRepository)
```

### 3. `ScheduleViewModel.kt` 수정

`AuthRepository`를 생성자에 추가하고 `"local_user"` 2곳 교체.

```kotlin
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    // 기존 파라미터들...
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val currentUserId: String
        get() = authRepository.currentUserId ?: error("User not signed in")
    // createEvent(), createTodo() 내부의 "local_user" → currentUserId로 교체
}
```

### 4. `TodoMaterializerWorker.kt` 수정

`@HiltWorker` + `@AssistedInject` 패턴을 유지하며 `AuthRepository`를 추가 주입한다.

```kotlin
@HiltWorker
class TodoMaterializerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    // 기존 파라미터들...
    private val authRepository: AuthRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = authRepository.currentUserId ?: return Result.success()
        // 기존 로직에서 USER_ID → userId로 교체
    }

    companion object {
        // USER_ID 상수 삭제
    }
}
```

> **주의:** 미로그인 시 `Result.failure()`가 아닌 `Result.success()`를 반환한다. 이유: `failure`는 WorkManager 재시도를 유발하지만 로그인 전에는 작업이 필요 없다.

### 5. `WidgetEntryPoint.kt` 수정

`AuthRepository` 접근자 추가.

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todoDao(): TodoDao
    fun eventDao(): EventDao
    fun financeDao(): FinanceDao
    fun readingPlanDao(): ReadingPlanDao
    fun authRepository(): AuthRepository  // 추가
}
```

### 6. `LSyncWidget.kt` 수정

`loadWidgetState()` 내부에서 `authRepository.currentUserId`를 사용하고, 미로그인 시 `WidgetState.empty()`를 반환한다.

```kotlin
private suspend fun loadWidgetState(context: Context): WidgetState {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext, WidgetEntryPoint::class.java
    )
    val userId = entryPoint.authRepository().currentUserId
        ?: return WidgetState.empty()
    // financeDao.getAllByDateRange(userId, ...) — "local_user" 교체
    // 나머지 로직 동일
}
```

### 7. `WidgetState.kt` 수정

`empty()` companion object 추가.

```kotlin
data class WidgetState(
    val todos: List<TodoEntity>,
    val events: List<EventEntity>,
    val monthExpense: Long,
    val monthIncome: Long,
    val todayReadingPlan: List<ReadingPlanEntity>,
    val readCount: Int,
    val totalCount: Int,
) {
    companion object {
        fun empty() = WidgetState(
            todos = emptyList(),
            events = emptyList(),
            monthExpense = 0,
            monthIncome = 0,
            todayReadingPlan = emptyList(),
            readCount = 0,
            totalCount = 0,
        )
    }
}
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `"local_user"` 문자열이 코드베이스에서 완전히 제거됐는가? (`grep -r "local_user" app/src/main/java/` 결과가 없어야 함)
   - `provideFinanceRepository`에 `authRepository` 파라미터가 추가됐는가?
   - `TodoMaterializerWorker`가 미로그인 시 `Result.success()`를 반환하는가?
   - `WidgetState.empty()`가 추가됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 3을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `FinanceRepository`에 `@Provides`를 별도로 추가하지 마라. 이유: `provideFinanceRepository`가 이미 존재한다. 중복 시 Hilt 충돌.
- `TodoMaterializerWorker`에서 미로그인 시 `Result.failure()`를 반환하지 마라. 이유: WorkManager 불필요한 재시도 유발.
- `"local_user"` 문자열을 ARCHITECTURE.md나 docs 문서에서 제거하지 마라. 이유: 이 step은 코드 파일만 다룬다. 문서 업데이트는 별도 작업이다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
