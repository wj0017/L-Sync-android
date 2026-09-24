package com.lsync.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.dao.SyncLogDao
import com.lsync.app.data.local.entity.SyncLogEntity
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// PRD Phase 1 게이트 — 동기화 에러율 5% 미만
private const val GATE_ERROR_RATE = 0.05f
private const val RECENT_LOG_LIMIT = 50

data class SyncStatusUiState(
    val windowDays: Int = 7,           // 집계 구간 — 7 또는 30
    val attempts: Int = 0,             // 구간 내 전체 시도 수(에러율 분모)
    val failures: Int = 0,             // 구간 내 실패 수(분자)
    val unresolved: Int = 0,           // 미해결 실패 건수(구간 무관)
    val logs: List<SyncLogEntity> = emptyList(),
    val isRetrying: Boolean = false,
) {
    // 0나눗셈 가드 — 시도가 없으면 에러율 0
    val errorRate: Float get() = if (attempts == 0) 0f else failures.toFloat() / attempts
    val hasData: Boolean get() = attempts > 0
    val passesGate: Boolean get() = errorRate < GATE_ERROR_RATE
}

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val syncLogDao: SyncLogDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncStatusUiState())
    val uiState: StateFlow<SyncStatusUiState> = _uiState.asStateFlow()

    // 구간이 가변이므로 이전 구독을 취소하고 새 since로 재구독한다(ReportViewModel monthJob 패턴).
    private var windowJob: Job? = null

    init {
        observeWindow(_uiState.value.windowDays)
    }

    fun setWindow(days: Int) {
        if (days == _uiState.value.windowDays) return
        _uiState.update { it.copy(windowDays = days, attempts = 0, failures = 0) }
        observeWindow(days)
    }

    fun retryNow() {
        if (_uiState.value.isRetrying) return
        val userId = authRepository.currentUserId ?: return
        _uiState.update { it.copy(isRetrying = true) }
        viewModelScope.launch {
            // 결과 수치는 Room Flow가 갱신하므로 RetryResult는 따로 들고 있지 않는다.
            try {
                syncRepository.retryFailed(userId)
            } catch (_: Exception) {
                // 재시도 실패는 로그 목록에 그대로 남는다 — 화면이 죽지 않게만 한다.
            } finally {
                _uiState.update { it.copy(isRetrying = false) }
            }
        }
    }

    private fun observeWindow(days: Int) {
        windowJob?.cancel()
        // 미로그인이면 빈 상태 유지(로그인 화면이 먼저 뜨므로 실사용 경로는 아니다).
        val userId = authRepository.currentUserId ?: return
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        windowJob = viewModelScope.launch {
            combine(
                syncLogDao.observeAttemptsSince(userId, since),
                syncLogDao.observeFailuresSince(userId, since),
                syncLogDao.observeUnresolvedCount(userId),
                syncLogDao.observeRecent(userId, RECENT_LOG_LIMIT),
            ) { attempts, failures, unresolved, logs ->
                _uiState.update {
                    it.copy(
                        attempts = attempts,
                        failures = failures,
                        unresolved = unresolved,
                        logs = logs,
                    )
                }
            }.collect {}
        }
    }
}
