package com.lsync.app.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.alarm.AlarmReliabilityPolicy
import com.lsync.app.data.alarm.AlarmReliabilitySummary
import com.lsync.app.data.local.dao.AlarmLogDao
import com.lsync.app.data.local.entity.AlarmLogEntity
import com.lsync.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class AlarmStatusUiState(
    val windowDays: Int = 7,
    val loading: Boolean = true,
    val error: Boolean = false,
    val expected: Int = 0,
    val summary: AlarmReliabilitySummary? = null,
    val late: Int = 0,
    val causes: List<Pair<String, Int>> = emptyList(),
    val recent: List<AlarmLogEntity> = emptyList(),
    val reminderExpected: Int = 0,
    val reminderFired: Int = 0,
    val reminderMissed: Int = 0,
    val reminderBlocked: Int = 0,
    val reminderSkipped: Int = 0,
    val coverageGaps: Int = 0,
    val coverageGapMillis: Long = 0,
    val coverageIncomplete: Boolean = true,
)

@HiltViewModel
class AlarmStatusViewModel @Inject constructor(
    private val dao: AlarmLogDao,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlarmStatusUiState())
    val uiState: StateFlow<AlarmStatusUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun setWindow(days: Int) {
        if (days !in listOf(7, 30) || days == _uiState.value.windowDays) return
        _uiState.update { it.copy(windowDays = days) }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val days = _uiState.value.windowDays
        val userId = authRepository.currentUserId
        if (userId == null) {
            _uiState.update { AlarmStatusUiState(windowDays = days, loading = false) }
            return
        }
        _uiState.update { it.copy(loading = true, error = false) }
        loadJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val from = now - TimeUnit.DAYS.toMillis(days.toLong())
                val result = withContext(Dispatchers.IO) {
                    val rows = dao.getExpectedInWindow(userId, from, now)
                    val gaps = dao.getCoverageGaps(from, now)
                    val coverage = dao.getCoverageState()
                    val reminders = dao.getReminderExpectedInWindow(from, now)
                    Pair(Triple(rows, gaps, coverage), reminders)
                }
                val (alarmResult, reminders) = result
                val rows = alarmResult.first
                val summary = AlarmReliabilityPolicy.summarize(
                    rows, alarmResult.second, from, now, alarmResult.third?.coveredThroughAt,
                    calibratedOnDevice = false,
                    coveredFromAt = alarmResult.third?.coveredFromAt,
                )
                val coverage = alarmResult.third
                val gapIntervals = (alarmResult.second.mapNotNull { gap ->
                    val start = maxOf(from, gap.fromAt)
                    val end = minOf(now, gap.toAt)
                    if (start < end) start to end else null
                } + if (coverage == null) {
                    listOf(from to now)
                } else {
                    listOfNotNull(
                        (from to minOf(now, coverage.coveredFromAt)).takeIf { it.first < it.second },
                        (maxOf(from, coverage.coveredThroughAt) to now).takeIf { it.first < it.second },
                    )
                }).sortedBy { it.first }
                var gapMillis = 0L
                var lastEnd = from
                gapIntervals.forEach { (start, end) ->
                    gapMillis += (end - maxOf(start, lastEnd)).coerceAtLeast(0)
                    lastEnd = maxOf(lastEnd, end)
                }
                val active = rows.filter {
                    it.missReason != "BEFORE_BOOT" && it.status in setOf("PENDING", "FIRED", "MISSED")
                }
                val late = active.count {
                    it.status == "FIRED" && it.firedAt != null &&
                        AlarmReliabilityPolicy.deadlinePassed(it.expectedAt, it.exact, it.firedAt)
                }
                val causes = active.flatMap { row ->
                    buildList {
                        if (row.status == "MISSED") add(row.missReason ?: "원인 미기록")
                        if (row.status == "FIRED" && row.posted == false) add(row.skipReason ?: "게시 실패")
                    }
                }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
                _uiState.update {
                    it.copy(
                        loading = false,
                        expected = active.size,
                        summary = summary,
                        late = late,
                        causes = causes,
                        recent = rows.sortedByDescending(AlarmLogEntity::expectedAt).take(50),
                        reminderExpected = reminders.size,
                        reminderFired = reminders.count { it.status == "FIRED" },
                        reminderMissed = reminders.count { it.status == "MISSED" },
                        reminderBlocked = reminders.count { it.skipReason in setOf("NOTIFICATIONS_OFF", "CHANNEL_BLOCKED") },
                        reminderSkipped = reminders.count { it.skipReason == "NO_CONTENT" },
                        coverageGaps = gapIntervals.size,
                        coverageGapMillis = gapMillis,
                        coverageIncomplete = alarmResult.second.isNotEmpty() ||
                            alarmResult.third?.let { it.coveredFromAt > from || it.coveredThroughAt < now } ?: true,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(loading = false, error = true, summary = null) }
            }
        }
    }
}
