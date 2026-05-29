package com.lsync.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.ui.widget.WidgetRefreshHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class FinanceFilter { ALL, INCOME, EXPENSE }

data class SettlementSummary(
    val groupId: String,
    val expenseEntity: FinanceEntity,
    val totalExpense: Long,
    val receivedAmount: Long,
) {
    val remaining: Long get() = totalExpense - receivedAmount
    val isComplete: Boolean get() = remaining <= 0
}

data class FormUiState(
    val isVisible: Boolean = false,
    val isEditing: Boolean = false,
    val editId: String? = null,
    val type: String = "EXPENSE",
    val amount: String = "",
    val category: String = FinanceCategory.ETC,
    val date: String = "",
    val note: String = "",
    val isSettlement: Boolean = false,
    val currentSettlementGroupId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class ReimbursementFormState(
    val isVisible: Boolean = false,
    val groupId: String = "",
    val amount: String = "",
    val date: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class LinkSettlementState(
    val isVisible: Boolean = false,
    val incomeId: String = "",
)

data class FinanceUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val transactions: List<FinanceEntity> = emptyList(),
    val filter: FinanceFilter = FinanceFilter.ALL,
    val settlementSummaries: Map<String, SettlementSummary> = emptyMap(),
    val settlementExpenses: List<FinanceEntity> = emptyList(),
) {
    val monthKey: String get() = "%04d-%02d".format(yearMonth.year, yearMonth.monthValue)
    // 정산 입금(INCOME + settlementGroupId)은 실제 수입이 아니라 돌려받은 돈 →
    // 수입·지출 어디에도 섞지 않고 별도 집계한다. (지출은 항상 총액, 음수 방지)
    val income:  Long get() = transactions
        .filter { it.type == "INCOME" && it.settlementGroupId == null }
        .sumOf { it.amount }
    val reimbursed: Long get() = transactions
        .filter { it.type == "INCOME" && it.settlementGroupId != null }
        .sumOf { it.amount }
    val expense: Long get() = transactions
        .filter { it.type == "EXPENSE" }
        .sumOf { it.amount }
    val net:     Long get() = income + reimbursed - expense

    val visible: List<FinanceEntity> get() = when (filter) {
        FinanceFilter.ALL     -> transactions
        FinanceFilter.INCOME  -> transactions.filter { it.type == "INCOME" }
        FinanceFilter.EXPENSE -> transactions.filter { it.type == "EXPENSE" }
    }

    val byDate: Map<String, List<FinanceEntity>> get() =
        visible.groupBy { it.date }.toSortedMap(reverseOrder())

    val openSettlements: List<Pair<FinanceEntity, SettlementSummary>> get() =
        settlementExpenses.mapNotNull { e ->
            val gid = e.settlementGroupId ?: return@mapNotNull null
            val s = settlementSummaries[gid] ?: return@mapNotNull null
            if (!s.isComplete) e to s else null
        }
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val widgetRefreshHelper: WidgetRefreshHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(FormUiState())
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    private val _exportedCsv = MutableSharedFlow<String>()
    val exportedCsv: SharedFlow<String> = _exportedCsv.asSharedFlow()

    private val _reimbursementForm = MutableStateFlow(ReimbursementFormState())
    val reimbursementForm: StateFlow<ReimbursementFormState> = _reimbursementForm.asStateFlow()

    private val _linkSettlement = MutableStateFlow(LinkSettlementState())
    val linkSettlement: StateFlow<LinkSettlementState> = _linkSettlement.asStateFlow()

    private var monthJob: Job? = null

    init {
        loadMonth(YearMonth.now())
        observeSettlements()
    }

    private fun observeSettlements() {
        viewModelScope.launch {
            repository.observeAllSettlementItems().collect { items ->
                val expenses = items.filter { it.type == "EXPENSE" }
                val summaries = expenses.associate { e ->
                    val gid = e.settlementGroupId!!
                    val received = items
                        .filter { it.type == "INCOME" && it.settlementGroupId == gid }
                        .sumOf { it.amount }
                    gid to SettlementSummary(gid, e, e.amount, received)
                }
                _uiState.update { it.copy(settlementSummaries = summaries, settlementExpenses = expenses) }
            }
        }
    }

    fun shiftMonth(delta: Int) {
        val next = _uiState.value.yearMonth.plusMonths(delta.toLong())
        _uiState.update { it.copy(yearMonth = next) }
        loadMonth(next)
    }

    fun setFilter(filter: FinanceFilter) = _uiState.update { it.copy(filter = filter) }

    private fun loadMonth(ym: YearMonth) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
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
            isSettlement = finance.settlementGroupId != null,
            currentSettlementGroupId = finance.settlementGroupId,
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
        isSettlement: Boolean? = null,
    ) {
        _formState.update { current ->
            current.copy(
                type = type ?: current.type,
                amount = amount ?: current.amount,
                category = category ?: current.category,
                date = date ?: current.date,
                note = note ?: current.note,
                isSettlement = isSettlement ?: current.isSettlement,
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
                    if (form.isSettlement && form.currentSettlementGroupId == null && form.type == "EXPENSE") {
                        repository.startSettlement(form.editId)
                    } else Unit
                } else {
                    val settlementGroupId = if (form.isSettlement && form.type == "EXPENSE") {
                        java.util.UUID.randomUUID().toString()
                    } else null
                    repository.create(
                        type = form.type,
                        amount = amountLong,
                        category = form.category,
                        date = form.date,
                        note = form.note.ifBlank { null },
                        settlementGroupId = settlementGroupId,
                    )
                }
            }.onSuccess {
                closeForm()
                widgetRefreshHelper.requestUpdate()
            }.onFailure { e ->
                _formState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.delete(id)
            }.onSuccess {
                widgetRefreshHelper.requestUpdate()
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

    fun openReimbursementForm(groupId: String) {
        _reimbursementForm.value = ReimbursementFormState(
            isVisible = true,
            groupId = groupId,
            date = LocalDate.now().toString(),
        )
    }

    fun updateReimbursementField(amount: String? = null, date: String? = null, note: String? = null) {
        _reimbursementForm.update { f ->
            f.copy(
                amount = amount ?: f.amount,
                date = date ?: f.date,
                note = note ?: f.note,
                errorMessage = null,
            )
        }
    }

    fun saveReimbursement() {
        val form = _reimbursementForm.value
        val amountLong = form.amount.toLongOrNull()
        if (amountLong == null || amountLong <= 0) {
            _reimbursementForm.update { it.copy(errorMessage = "금액을 올바르게 입력하세요") }
            return
        }
        viewModelScope.launch {
            _reimbursementForm.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.addReimbursement(form.groupId, amountLong, form.date, form.note.ifBlank { null })
            }.onSuccess {
                _reimbursementForm.value = ReimbursementFormState()
                widgetRefreshHelper.requestUpdate()
            }.onFailure { e ->
                _reimbursementForm.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun closeReimbursementForm() {
        _reimbursementForm.value = ReimbursementFormState()
    }

    fun openLinkSettlement(incomeId: String) {
        _linkSettlement.value = LinkSettlementState(isVisible = true, incomeId = incomeId)
    }

    fun closeLinkSettlement() {
        _linkSettlement.value = LinkSettlementState()
    }

    fun linkToSettlement(groupId: String) {
        val incomeId = _linkSettlement.value.incomeId
        viewModelScope.launch {
            runCatching { repository.linkToSettlement(incomeId, groupId) }
                .onSuccess {
                    _linkSettlement.value = LinkSettlementState()
                    widgetRefreshHelper.requestUpdate()
                }
        }
    }
}
