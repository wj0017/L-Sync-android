# Step 1: auth-viewmodel

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/data/repository/AuthRepository.kt`  ← step 0에서 생성됨
- `app/src/main/java/com/lsync/app/ui/home/HomeViewModel.kt`  ← ViewModel 패턴 참고

## 이전 step 요약

Step 0에서 생성/수정된 파일:
- `di/Qualifiers.kt` — `@ApplicationScope` Qualifier 정의
- `data/repository/AuthRepository.kt` — Firebase Auth 래퍼. `currentUserId`, `authState: StateFlow<FirebaseUser?>`, `signInWithGoogle()`, `signOut()` 제공
- `di/AppModule.kt` — `provideFirebaseAuth()`, `provideApplicationScope()` 추가
- `libs.versions.toml` + `app/build.gradle.kts` — `play-services-auth:21.2.0` 추가됨

## 작업

### 1. `app/src/main/java/com/lsync/app/ui/auth/AuthViewModel.kt` 신규 생성

디렉토리 `ui/auth/`가 없으면 함께 생성한다.

```kotlin
package com.lsync.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isSignedIn: Boolean,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userId: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    // 초기값을 currentUserId 즉시 계산 → 앱 재실행 시 LoginScreen 깜빡임 방지
    private val _uiState = MutableStateFlow(
        AuthUiState(
            isSignedIn = authRepository.currentUserId != null,
            userId = authRepository.currentUserId,
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                _uiState.update {
                    it.copy(
                        isSignedIn = user != null,
                        userId = user?.uid,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.signInWithGoogle(idToken) }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            // 성공 시: authState Flow가 FirebaseUser를 방출 → init 블록의 collect가 isSignedIn=true로 업데이트
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
```

**핵심 설계 규칙:**
- `@HiltViewModel` + `@Inject constructor` 패턴 필수. 없으면 런타임 크래시.
- `collectAsStateWithLifecycle` 금지. UI에서는 반드시 `collectAsState()`를 사용한다.
- `signInWithGoogle` 성공 경로에서 `_uiState.update(isSignedIn=true)`를 직접 호출하지 않는다. `authState` Flow의 방출이 단일 진실 소스(SSOT)이므로 `init`의 `collect`가 자동 처리한다.
- `isLoading=true` → `signInWithGoogle` 호출 → 실패 시 `isLoading=false` + `error` 설정. 성공 시 `authState` collect에서 `isLoading=false` 처리.

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음
./gradlew lintDebug       # 린트 경고 없음
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트를 확인한다:
   - `ui/auth/AuthViewModel.kt`가 생성됐는가?
   - `@HiltViewModel` + `@Inject constructor` 패턴을 따르는가?
   - `AuthUiState` 초기값이 `authRepository.currentUserId != null`로 즉시 계산되는가?
   - `collectAsStateWithLifecycle`를 사용하지 않았는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/7-firebase-auth/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "구체적 사유"` 후 즉시 중단

## 금지사항

- `AppModule.kt`에 `AuthViewModel`용 `@Provides`를 추가하지 마라. 이유: `@HiltViewModel`이 있으면 Hilt가 자동 처리한다.
- `signInWithGoogle` 성공 시 `_uiState`에 `isSignedIn=true`를 직접 설정하지 마라. 이유: `authState` Flow 방출이 SSOT다. 직접 설정하면 상태가 중복으로 업데이트된다.
- `collectAsStateWithLifecycle`을 사용하지 마라. 이유: CLAUDE.md 금지 규칙.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
