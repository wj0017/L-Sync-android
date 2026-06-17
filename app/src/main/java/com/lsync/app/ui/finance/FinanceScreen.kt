package com.lsync.app.ui.finance

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.FinanceEntity
import androidx.compose.ui.text.font.FontStyle
import com.lsync.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FinanceScreen(viewModel: FinanceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val reimbursementForm by viewModel.reimbursementForm.collectAsState()
    val linkSettlement by viewModel.linkSettlement.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDashboard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportedCsv.collect { csv ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, csv)
                putExtra(Intent.EXTRA_SUBJECT, "L-Sync 가계부 ${uiState.yearMonth}")
            }
            context.startActivity(Intent.createChooser(intent, "내보내기"))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = uiState.yearMonth.year.toString(),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${uiState.yearMonth.monthValue}월 가계부",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        letterSpacing = (-0.035).em,
                        color = FgPrimary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // CSV export button
                    IconButton(
                        onClick = { viewModel.triggerExport() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, CircleShape),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "내보내기", tint = FgPrimary, modifier = Modifier.size(18.dp))
                    }
                    // Add transaction button
                    IconButton(
                        onClick = { viewModel.openCreateForm(LocalDate.now().toString()) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, CircleShape),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "추가", tint = FgPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // View toggle: 거래 목록 | 통계 대시보드 (ghost chip)
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(false to "거래", true to "통계").forEach { (isDash, label) ->
                    val sel = showDashboard == isDash
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) FgPrimary else Color.Transparent)
                            .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                            .clickable { showDashboard = isDash }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (sel) BgPrimary else FgSecondary,
                        )
                    }
                }
            }

            if (showDashboard) {
                FinanceDashboard(modifier = Modifier.fillMaxSize())
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Month switcher
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.shiftMonth(-1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 달", tint = FgSecondary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "${uiState.yearMonth.year}년 ${uiState.yearMonth.monthValue}월",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            color = FgSecondary,
                        )
                        Spacer(Modifier.width(14.dp))
                        IconButton(onClick = { viewModel.shiftMonth(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 달", tint = FgSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Summary card
                item {
                    SummaryCard(
                        net = uiState.net,
                        income = uiState.income,
                        expense = uiState.expense - uiState.reimbursed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }

                // Filter chips
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            FinanceFilter.ALL to "전체",
                            FinanceFilter.EXPENSE to "지출",
                            FinanceFilter.INCOME to "수입",
                        ).forEach { (filter, label) ->
                            val sel = uiState.filter == filter
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (sel) FgPrimary else Color.Transparent)
                                    .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                                    .clickable { viewModel.setFilter(filter) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (sel) BgPrimary else FgSecondary,
                                )
                            }
                        }
                    }
                }

                // Empty state
                if (uiState.byDate.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("거래 없음", fontSize = 14.sp, color = FgDisabled, fontFamily = Pretendard)
                        }
                    }
                } else {
                    // Day-grouped list
                    uiState.byDate.forEach { (date, txList) ->
                        item(key = "header_$date") {
                            DateGroupHeader(date = date, transactions = txList)
                        }
                        item(key = "card_$date") {
                            TransactionGroupCard(
                                transactions = txList,
                                settlementSummaries = uiState.settlementSummaries,
                                openSettlements = uiState.openSettlements,
                                onTap = { finance ->
                                    if (finance.sourceTodoId != null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Todo 연동 항목은 수정할 수 없습니다")
                                        }
                                    } else {
                                        viewModel.openEditForm(finance)
                                    }
                                },
                                onDelete = { id -> viewModel.deleteTransaction(id) },
                                onOpenReimbursementForm = { groupId -> viewModel.openReimbursementForm(groupId) },
                                onOpenLinkForm = { incomeId -> viewModel.openLinkSettlement(incomeId) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (formState.isVisible) {
        TransactionFormSheet(
            formState = formState,
            onTypeChange = { viewModel.updateFormField(type = it) },
            onAmountChange = { viewModel.updateFormField(amount = it) },
            onCategoryChange = { viewModel.updateFormField(category = it) },
            onDateChange = { viewModel.updateFormField(date = it) },
            onNoteChange = { viewModel.updateFormField(note = it) },
            onSettlementChange = { viewModel.updateFormField(isSettlement = it) },
            onSave = viewModel::saveTransaction,
            onDismiss = viewModel::closeForm,
        )
    }

    if (reimbursementForm.isVisible) {
        ReimbursementSheet(
            formState = reimbursementForm,
            onAmountChange = { viewModel.updateReimbursementField(amount = it) },
            onDateChange = { viewModel.updateReimbursementField(date = it) },
            onNoteChange = { viewModel.updateReimbursementField(note = it) },
            onSave = viewModel::saveReimbursement,
            onDismiss = viewModel::closeReimbursementForm,
        )
    }

    if (linkSettlement.isVisible) {
        val income = uiState.transactions.find { it.id == linkSettlement.incomeId }
        val linkable = if (income != null) {
            uiState.openSettlements.filter { (expense, s) -> s.remaining >= income.amount && expense.date <= income.date }
        } else {
            uiState.openSettlements
        }
        LinkSettlementDialog(
            openSettlements = linkable,
            onSelect = { groupId -> viewModel.linkToSettlement(groupId) },
            onDismiss = viewModel::closeLinkSettlement,
        )
    }
}

@Composable
private fun SummaryCard(net: Long, income: Long, expense: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        // Net balance
        Text(
            text = "이번 달 잔액",
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.12.em,
            color = FgTertiary,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // +/− sign — Instrument Serif Italic (프리미엄 강세)
            Text(
                text = if (net >= 0) "+" else "−",
                fontFamily = InstrumentSerif,
                fontWeight = FontWeight.Normal,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 30.sp,
                color = (if (net >= 0) AccentGreen else AccentRed80).copy(alpha = 0.85f),
                lineHeight = 30.sp,
            )
            Spacer(Modifier.width(2.dp))
            // ₩ symbol — Instrument Serif Italic
            Text(
                text = "₩",
                fontFamily = InstrumentSerif,
                fontWeight = FontWeight.Normal,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 30.sp,
                color = (if (net >= 0) AccentGreen else AccentRed80).copy(alpha = 0.85f),
                lineHeight = 30.sp,
            )
            // Amount
            Text(
                text = "%,d".format(Math.abs(net)),
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                letterSpacing = (-0.035).em,
                color = if (net >= 0) AccentGreen else AccentRed80,
                lineHeight = 36.sp,
            )
        }

        // Divider
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Spacer(Modifier.height(14.dp))

        // Income / Expense split
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("수입", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
                Text(
                    text = "+₩%,d".format(income),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.01).em,
                    color = AccentGreen,
                )
            }
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Divider).align(Alignment.CenterVertically))
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("지출", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
                Text(
                    text = "−₩%,d".format(expense),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.01).em,
                    color = FgPrimary,
                )
            }
        }

    }
}

@Composable
private fun DateGroupHeader(date: String, transactions: List<FinanceEntity>) {
    val dayTotal = transactions.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val label = parsed?.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)) ?: date

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label.uppercase(), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
        Text(
            text = "${if (dayTotal >= 0) "+" else "−"}₩%,d".format(Math.abs(dayTotal)),
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.12.em,
            color = if (dayTotal >= 0) AccentGreen else FgSecondary,
        )
    }
}

@Composable
private fun TransactionGroupCard(
    transactions: List<FinanceEntity>,
    settlementSummaries: Map<String, SettlementSummary>,
    openSettlements: List<Pair<FinanceEntity, SettlementSummary>>,
    onTap: (FinanceEntity) -> Unit,
    onDelete: (String) -> Unit,
    onOpenReimbursementForm: (String) -> Unit,
    onOpenLinkForm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp)),
    ) {
        transactions.forEachIndexed { i, tx ->
            if (i > 0) Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Divider))
            val summary = tx.settlementGroupId?.let { settlementSummaries[it] }
            // 이 수입을 연결할 만한 정산이 있을 때만 "정산에 연결" 노출:
            // 받을 잔액이 이 수입 금액 이상이고, 지출일이 수입일보다 앞선 정산.
            val canLinkSettlement = tx.type == "INCOME" && tx.settlementGroupId == null &&
                openSettlements.any { (expense, s) -> s.remaining >= tx.amount && expense.date <= tx.date }
            TransactionRow(
                tx = tx,
                settlementSummary = summary,
                canLinkSettlement = canLinkSettlement,
                onTap = { onTap(tx) },
                onDelete = if (tx.sourceTodoId == null) { { onDelete(tx.id) } } else null,
                onOpenReimbursementForm = { summary?.let { onOpenReimbursementForm(it.groupId) } },
                onOpenLinkForm = { onOpenLinkForm(tx.id) },
            )
        }
    }
}

@Composable
private fun TransactionRow(
    tx: FinanceEntity,
    settlementSummary: SettlementSummary?,
    canLinkSettlement: Boolean,
    onTap: () -> Unit,
    onDelete: (() -> Unit)?,
    onOpenReimbursementForm: () -> Unit,
    onOpenLinkForm: () -> Unit,
) {
    val isIncome = tx.type == "INCOME"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Category tag (pill)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isIncome) AccentGreen.copy(alpha = 0.15f)
                        else FgPrimary.copy(alpha = 0.06f)
                    )
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    text = (tx.category.ifBlank { "미분류" }).uppercase(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.08.em,
                    color = if (isIncome) AccentGreen else FgSecondary,
                )
            }

            // Body
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.note ?: (if (isIncome) "수입" else "지출"),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = (-0.005).em,
                    color = FgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tx.sourceTodoId != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "· 할 일 연동".uppercase(),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                }
            }

            // Amount — 수입은 AccentGreen, 지출은 FgPrimary (빨강 남용 회피)
            Text(
                text = "${if (isIncome) "+" else "−"}₩%,d".format(tx.amount),
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = (-0.01).em,
                color = if (isIncome) AccentGreen else FgPrimary,
            )

            // Delete icon — Todo-linked items excluded
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "삭제",
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Settlement footer: EXPENSE with active settlement group
        if (!isIncome && settlementSummary != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Divider))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (settlementSummary.isComplete) "정산 완료"
                           else "정산 받은 금액 ₩%,d / ₩%,d".format(settlementSummary.receivedAmount, settlementSummary.totalExpense),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (settlementSummary.isComplete) FgTertiary else AccentGreen,
                )
                if (!settlementSummary.isComplete) {
                    TextButton(
                        onClick = onOpenReimbursementForm,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "+ 받기",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = AccentGreen,
                        )
                    }
                }
            }
        }

        // Settlement footer: INCOME with no group and a linkable settlement exists
        if (canLinkSettlement) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Divider))
            TextButton(
                onClick = onOpenLinkForm,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Text(
                        "정산에 연결 →",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = AccentBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReimbursementSheet(
    formState: ReimbursementFormState,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                "정산 받기",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = FgPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = formState.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("받은 금액", fontFamily = Pretendard, fontSize = 12.sp) },
                    suffix = {
                        Text(
                            "원",
                            fontFamily = InstrumentSerif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 14.sp,
                            color = FgSecondary,
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )
                OutlinedTextField(
                    value = formState.date,
                    onValueChange = onDateChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("날짜 (YYYY-MM-DD)", fontFamily = Pretendard, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )
                OutlinedTextField(
                    value = formState.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모 (선택)", fontFamily = Pretendard, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )
                if (formState.errorMessage != null) {
                    Text(
                        formState.errorMessage,
                        fontFamily = Pretendard,
                        fontSize = 12.sp,
                        color = AccentRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !formState.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentGreen),
            ) {
                Text(
                    if (formState.isSaving) "저장 중..." else "저장",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FgSecondary),
            ) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        },
    )
}

@Composable
private fun LinkSettlementDialog(
    openSettlements: List<Pair<FinanceEntity, SettlementSummary>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                "정산 연결",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = FgPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "어떤 정산 항목에 연결할까요?",
                    fontFamily = Pretendard,
                    fontSize = 13.sp,
                    color = FgSecondary,
                )
                Spacer(Modifier.height(4.dp))
                openSettlements.forEach { (expense, summary) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgElevated)
                            .clickable { onSelect(summary.groupId) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.note ?: "지출",
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = FgPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = expense.date,
                                fontFamily = Pretendard,
                                fontSize = 11.sp,
                                color = FgTertiary,
                            )
                        }
                        Text(
                            text = "−₩%,d".format(summary.remaining),
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = FgPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FgSecondary),
            ) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        },
    )
}
