# Step 4: data-migration

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/TechSpec.md`  ← AppDatabase 스키마, 마이그레이션 버전 기록
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt`  ← step 0에서 생성됨
- `app/src/main/java/com/lsync/app/data/local/dao/FinanceDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoDao.kt`
- `app/src/main/java/com/lsync/app/data/local/dao/TodoTemplateDao.kt`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`

## 이전 step 요약

Step 0: `di/Qualifiers.kt`, `data/repository/AuthRepository.kt`(`currentUserId`, `authState`, `signInWithGoogle`, `signOut`), `AppModule.kt`(FirebaseAuth + ApplicationScope providers)
Step 1: `ui/auth/AuthViewModel.kt` — `@HiltViewModel`, `AuthUiState` 초기값 즉시 계산
Step 2: `ui/auth/LoginScreen.kt`, `NavGraph.kt`(auth branching), `MainActivity.kt`(permission gating)
Step 3: `"local_user"` 7곳 모두 `authRepository.currentUserId`로 교체, `WidgetState.empty()` 추가

## 배경

Phase 1~6에서 생성된 Room 데이터의 `userId` 필드가 모두 `"local_user"`로 되어 있다. 사용자가 Google 로그인하면 실제 Firebase UID가 발급되는데, Room에 있는 기존 데이터를 새 UID로 일괄 업데이트해야 앱이 데이터를 계속 표시할 수 있다.

이 작업은 AppDatabase 스키마를 변경하지 않으므로 Room Migration 객체가 불필요하다.

**마이그레이션 타이밍 (race condition 방지):**
- 신규 로그인: `AuthRepository.signInWithGoogle()` 내부에서 마이그레이션을 **동기 await**한 후 반환 → NavGraph가 HomeScreen으로 전환될 때 이미 마이그레이션 완료
- 앱 재실행(자동 로그인): `AuthRepository.init`에서 `scope.launch`로 비동기 실행 (이미 마이그레이션됐으면 멱등성으로 즉시 종료)

## 작업

### 1. 각 DAO에 `migrateUserId` 쿼리 추가

**기존 메서드를 수정하지 않고** 각 DAO에 추가만 한다.

**`FinanceDao.kt`에 추가:**
```kotlin
@Query("UPDATE finance SET userId = :newId WHERE userId = :oldId")
suspend fun migrateUserId(oldId: String, newId: String)
```

**`TodoDao.kt`에 추가:**
```kotlin
@Query("UPDATE todos SET userId = :newId WHERE userId = :oldId")
suspend fun migrateUserId(oldId: String, newId: String)
```

**`TodoTemplateDao.kt`에 추가:**
```kotlin
@Query("UPDATE todo_templates SET userId = :newId WHERE userId = :oldId")
suspend fun migrateUserId(oldId: String, newId: String)
```

> `EventEntity`에는 `userId` 필드가 없으므로 제외한다. `ReadingPlanEntity`와 `MemoEntity`도 `userId` 없음.

### 2. `app/src/main/java/com/lsync/app/data/repository/AuthMigrationHelper.kt` 신규 생성

```kotlin
package com.lsync.app.data.repository

import android.content.Context
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val financeDao: FinanceDao,
    private val todoDao: TodoDao,
    private val todoTemplateDao: TodoTemplateDao,
) {
    suspend fun migrate(newUserId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY, null) == newUserId) return  // 멱등성: 이미 마이그레이션됨

        financeDao.migrateUserId(LEGACY_USER_ID, newUserId)
        todoDao.migrateUserId(LEGACY_USER_ID, newUserId)
        todoTemplateDao.migrateUserId(LEGACY_USER_ID, newUserId)

        prefs.edit().putString(KEY, newUserId).apply()
    }

    companion object {
        private const val PREFS = "auth_migration"
        private const val KEY = "migrated_uid"
        private const val LEGACY_USER_ID = "local_user"
    }
}
```

### 3. `AuthRepository.kt` 수정 — `AuthMigrationHelper` 주입 및 마이그레이션 호출

```kotlin
@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val migrationHelper: AuthMigrationHelper,  // 추가
    @ApplicationScope private val scope: CoroutineScope,
) {
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    val authState: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, firebaseAuth.currentUser)

    init {
        // 앱 재실행 시 자동 로그인: 이미 currentUser가 있으면 마이그레이션 시도 (멱등성으로 안전)
        firebaseAuth.currentUser?.uid?.let { uid ->
            scope.launch { migrationHelper.migrate(uid) }
        }
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("signIn succeeded but user is null")
        // 신규 로그인: 마이그레이션 동기 완료 후 반환 → NavGraph 전환 시 데이터 준비됨
        migrationHelper.migrate(user.uid)
        return user
    }

    suspend fun signOut() { firebaseAuth.signOut() }
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
   - `FinanceDao`, `TodoDao`, `TodoTemplateDao`에 `migrateUserId`가 추가됐는가?
   - `AuthMigrationHelper.kt`가 생성됐는가?
   - `AuthMigrationHelper.migrate()`가 SharedPreferences 플래그로 멱등성을 보장하는가?
   - `AuthRepository.signInWithGoogle()`이 마이그레이션을 동기 await한 후 반환하는가?
   - `AuthRepository.init`에서 자동 로그인 시 `scope.launch`로 마이그레이션을 시도하는가?
   - AppDatabase Room 마이그레이션 객체를 추가하지 않았는가? (스키마 변경 없음)
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 4를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- Room AppDatabase 버전을 올리지 마라. 이유: `userId` 컬럼은 이미 존재한다. 값만 변경하므로 스키마 변경 없음.
- `EventEntity`, `ReadingPlanEntity`, `MemoEntity`에 `migrateUserId`를 추가하지 마라. 이유: 해당 테이블에 `userId` 필드가 없다.
- `AppModule.kt`에 `AuthMigrationHelper`용 `@Provides`를 추가하지 마라. 이유: `@Inject constructor`가 있으면 Hilt 자동 처리.
- `signInWithGoogle`에서 마이그레이션 실패 시 로그인 자체를 취소하지 마라. 이유: 마이그레이션 실패는 데이터 누락이지 로그인 실패가 아니다. 예외는 로그 기록 후 진행한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
