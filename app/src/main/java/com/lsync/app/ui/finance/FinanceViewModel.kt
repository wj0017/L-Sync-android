package com.lsync.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

enum class FinanceFilter { ALL, INCOME, EXPENSE }

data class FinanceUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val transactions: List<FinanceEntity> = emptyList(),
    val filter: FinanceFilter = FinanceFilter.ALL,
) {
    val monthKey: String get() = "%04d-%02d".format(yearMonth.year, yearMonth.monthValue)
    val income:  Long get() = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expense: Long get() = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val net:     Long get() = income - expense

    val visible: List<FinanceEntity> get() = when (filter) {
        FinanceFilter.ALL     -> transactions
        FinanceFilter.INCOME  -> transactions.filter { it.type == "INCOME" }
        FinanceFilter.EXPENSE -> transactions.filter { it.type == "EXPENSE" }
    }

    val byDate: Map<String, List<FinanceEntity>> get() =
        visible.groupBy { it.date }.toSortedMap(reverseOrder())
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    init { loadMonth(YearMonth.now()) }

    fun shiftMonth(delta: Int) {
        val next = _uiState.value.yearMonth.plusMonths(delta.toLong())
        _uiState.update { it.copy(yearMonth = next) }
        loadMonth(next)
    }

    fun setFilter(filter: FinanceFilter) = _uiState.update { it.copy(filter = filter) }

    private fun loadMonth(ym: YearMonth) {
        viewModelScope.launch {
            val key = "%04d-%02d".format(ym.year, ym.monthValue)
            repository.observeByMonth(key)
                .collect { list -> _uiState.update { it.copy(transactions = list) } }
        }
    }
}
