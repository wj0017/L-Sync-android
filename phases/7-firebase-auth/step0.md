# Step 0: auth-setup

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`
- `docs/PRD.md`
- `app/src/main/java/com/lsync/app/di/AppModule.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

## 배경

현재 `userId = "local_user"`가 프로젝트 전체에 하드코딩되어 있다. Firebase Firestore 보안 규칙(`request.auth.uid == resource.data.userId`)을 실제로 작동시키기 위해 Google 로그인으로 실제 Firebase UID를 발급한다. 이 step은 그 기반 레이어(의존성 + Qualifier + AuthRepository 뼈대)를 구축한다.

## 작업

### 1. `gradle/libs.versions.toml`에 의존성 추가

`[versions]` 섹션:
```toml
playServicesAuth = "21.2.0"
```

`[libraries]` 섹션:
```toml
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
```

### 2. `app/build.gradle.kts`에 의존성 추가

`dependencies` 블록에 추가:
```kotlin
implementation(libs.play.services.auth)
```

### 3. `app/src/main/java/com/lsync/app/di/Qualifiers.kt` 신규 생성

```kotlin
package com.lsync.app.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

### 4. `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt` 신규 생성

```kotlin
package com.lsync.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.lsync.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    val authState: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, firebaseAuth.currentUser)

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return firebaseAuth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("signIn succeeded but user is null")
    }

    suspend fun signOut() { firebaseAuth.signOut() }
}
```

> **주의:** `signInWithGoogle`에 마이그레이션 호출은 step 4에서 추가된다. 지금은 기본 로그인 로직만 구현한다.

### 5. `app/src/main/java/com/lsync/app/di/AppModule.kt` 수정

아래 두 `@Provides` 함수를 추가한다. **기존 함수는 수정하지 않는다.**

```kotlin
@Provides
@Singleton
fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

@Provides
@Singleton
@ApplicationScope
fun provideApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

필요한 import:
```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.lsync.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
```

> `AuthRepository`는 `@Inject constructor` + `@Singleton`이므로 별도 `@Provides` 등록 불필요. Hilt가 자동 주입한다.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `di/Qualifiers.kt`가 생성됐는가?
   - `data/repository/AuthRepository.kt`가 생성됐는가?
   - `AppModule.kt`에 `provideFirebaseAuth()`, `provideApplicationScope()` 함수가 추가됐는가?
   - `libs.versions.toml`과 `app/build.gradle.kts`에 `play-services-auth`가 추가됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 0을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- AppModule.kt의 기존 함수를 수정하지 마라. 이유: 기존 Repository들이 해당 함수에 의존하며, 수정 시 Hilt 오류 발생 가능.
- AuthRepository에 `@Provides`를 추가하지 마라. 이유: `@Inject constructor`가 있으면 Hilt가 자동 처리한다. 중복 시 Hilt 충돌.
- Step 4의 마이그레이션 로직을 미리 구현하지 마라. 이유: 이 step은 AuthRepository 뼈대만 구축한다. 마이그레이션 의존성(AuthMigrationHelper)은 아직 존재하지 않는다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
