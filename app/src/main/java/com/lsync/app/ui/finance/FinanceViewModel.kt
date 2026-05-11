package com.lsync.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

enum class FinanceFilter { ALL, INCOME, EXPENSE }

data class FormUiState(
    val isVisible: Boolean = false,
    val isEditing: Boolean = false,
    val editId: String? = null,
    val type: String = "EXPENSE",
    val amount: String = "",
    val category: String = FinanceCategory.ETC,
    val date: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

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

    private val _formState = MutableStateFlow(FormUiState())
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    private val _exportedCsv = MutableSharedFlow<String>()
    val exportedCsv: SharedFlow<String> = _exportedCsv.asSharedFlow()

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

    fun openCreateForm(defaultDate: String) {
        _formState.value = FormUiState(isVisible = true, date = defaultDate)
    }

    fun openEditForm(finance: FinanceEntity) {
        if (finance.sourceTodoId != null) {
            _formState.update { it.copy(errorMessage = "Todo 연동 항목은 수정할 수 없습니다") }
            return
        }
        _formState.value = FormUiState(
            isVisible = true,
            isEditing = true,
            editId = finance.id,
            type = finance.type,
            amount = finance.amount.toString(),
            category = finance.category,
            date = finance.date,
            note = finance.note ?: "",
        )
    }

    fun closeForm() {
        _formState.value = FormUiState()
    }

    fun updateFormField(
        type: String? = null,
        amount: String? = null,
        category: String? = null,
        date: String? = null,
        note: String? = null,
    ) {
        _formState.update { current ->
            current.copy(
                type = type ?: current.type,
                amount = amount ?: current.amount,
                category = category ?: current.category,
                date = date ?: current.date,
                note = note ?: current.note,
                errorMessage = null,
            )
        }
    }

    fun saveTransaction() {
        val form = _formState.value
        val amountLong = form.amount.toLongOrNull()
        if (amountLong == null || amountLong <= 0) {
            _formState.update { it.copy(errorMessage = "금액을 올바르게 입력하세요") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                if (form.isEditing && form.editId != null) {
                    repository.update(
                        id = form.editId,
                        type = form.type,
                        amount = amountLong,
                        category = form.category,
                        date = form.date,
                        note = form.note.ifBlank { null },
                    )
                } else {
                    repository.create(
                        type = form.type,
                        amount = amountLong,
                        category = form.category,
                        date = form.date,
                        note = form.note.ifBlank { null },
                    )
                }
            }.onSuccess {
                closeForm()
                loadMonth(_uiState.value.yearMonth)
            }.onFailure { e ->
                _formState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.delete(id)
            }.onFailure { e ->
                android.util.Log.w("FinanceViewModel", "deleteTransaction failed: ${e.message}")
            }
        }
    }

    fun triggerExport() {
        viewModelScope.launch {
            val key = _uiState.value.monthKey
            runCatching { repository.exportCsv(key) }
                .onSuccess { csv -> _exportedCsv.emit(csv) }
        }
    }
}
