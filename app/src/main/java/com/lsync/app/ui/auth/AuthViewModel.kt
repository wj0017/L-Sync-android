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
