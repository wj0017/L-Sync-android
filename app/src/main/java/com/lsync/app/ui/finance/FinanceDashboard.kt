package com.lsync.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.ui.theme.*

@Composable
fun FinanceDashboard(
    modifier: Modifier = Modifier,
    viewModel: FinanceDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // 예산 편집 다이얼로그 상태 — null이면 닫힘, 값이면 해당 카테고리로 진입.
    var editorCategory by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            DashboardSummaryCard(
                expense = state.totalExpense,
                income = state.totalIncome,
                reimbursed = state.totalReimbursed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // 예산 — 섹션 헤더의 "설정" 액션으로 진입. 설정된 예산이 있으면 진행바 카드 노출.
        item {
            SectionHeaderWithAction(
                text = "예산",
                actionLabel = "설정",
                onAction = { editorCategory = BudgetEntity.TOTAL_CATEGORY },
            )
        }
        if (state.budgets.isNotEmpty()) {
            item {
                BudgetProgressCard(
                    budgets = state.budgets,
                    onEdit = { category -> editorCategory = category },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        item {
            SectionHeader("최근 6개월 추세")
        }
        item {
            MonthlyTrendCard(
                points = state.monthly,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        item {
            SectionHeader("카테고리별 지출")
        }
        item {
            CategoryBreakdownCard(
                slices = state.categoryBreakdown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    editorCategory?.let { category ->
        BudgetEditDialog(
            initialCategory = category,
            existingLimits = state.budgets.associate { it.category to it.limit },
            onSave = { cat, amount ->
                viewModel.setBudget(cat, amount)
                editorCategory = null
            },
            onDelete = { cat ->
                viewModel.deleteBudget(cat)
                editorCategory = null
            },
            onDismiss = { editorCategory = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = Pretendard,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.12.em,
        color = FgTertiary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionHeaderWithAction(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.12.em,
            color = FgTertiary,
        )
        Text(
            text = actionLabel,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.04.em,
            color = AccentBlue,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DashboardSummaryCard(
    expense: Long,
    income: Long,
    reimbursed: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            text = "최근 6개월 합계",
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.12.em,
            color = FgTertiary,
        )
        Spacer(Modifier.height(8.dp))
        // 순지출 — InstrumentSerif Italic ₩ 기호 + Pretendard 숫자 (지출은 FgPrimary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "−",
                fontFamily = InstrumentSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
                color = FgSecondary,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "₩",
                fontFamily = InstrumentSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
                color = FgSecondary,
                lineHeight = 30.sp,
            )
            Text(
                text = "%,d".format(expense),
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                letterSpacing = (-0.035).em,
                color = FgPrimary,
                lineHeight = 36.sp,
            )
        }
        Text(
            text = "기간 순지출",
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = FgTertiary,
        )

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryStat(label = "수입", text = "+₩%,d".format(income), color = AccentGreen, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(Divider).align(Alignment.CenterVertically))
            SummaryStat(
                label = "정산 받음",
                text = if (reimbursed > 0) "+₩%,d".format(reimbursed) else "₩0",
                color = if (reimbursed > 0) AccentBlue else FgSecondary,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
        Text(
            text = text,
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = (-0.01).em,
            color = color,
        )
    }
}

@Composable
private fun MonthlyTrendCard(
    points: List<MonthlyPoint>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        val maxExpense = points.maxOfOrNull { it.expense } ?: 0L
        if (points.isEmpty() || maxExpense <= 0L) {
            EmptyHint("표시할 지출이 없습니다")
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                // 정규화: 막대 영역 100dp 기준. 0인 달도 안전.
                val fraction = (point.expense.toFloat() / maxExpense.toFloat()).coerceIn(0f, 1f)
                val barHeight = (100f * fraction).dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentBlue.copy(alpha = 0.55f)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${point.yearMonth.takeLast(2).trimStart('0')}월",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = FgTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        val top = slices.take(6)
        val maxAmount = top.maxOfOrNull { it.amount } ?: 0L
        if (top.isEmpty() || maxAmount <= 0L) {
            EmptyHint("표시할 지출이 없습니다")
            return@Column
        }

        top.forEachIndexed { i, slice ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            val fraction = (slice.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = slice.category.ifBlank { "미분류" },
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = FgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "−₩%,d".format(slice.amount),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.01).em,
                    color = FgPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            // 비율 바 — 트랙 위에 채움. 지출이라 AccentRed 미사용(FgPrimary 계열).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Divider),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(FgSecondary),
                )
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(
    budgets: List<BudgetProgress>,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 전체 한도(TOTAL)를 맨 위로, 나머지는 한도 큰 순으로. 시각적 구분.
    val total = budgets.firstOrNull { it.category == BudgetEntity.TOTAL_CATEGORY }
    val categories = budgets
        .filter { it.category != BudgetEntity.TOTAL_CATEGORY }
        .sortedByDescending { it.limit }
    val ordered = listOfNotNull(total) + categories

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        ordered.forEachIndexed { i, budget ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            val isTotal = budget.category == BudgetEntity.TOTAL_CATEGORY
            // 전체 항목과 카테고리 항목 사이에 구분선.
            if (i > 0 && total != null && i == 1) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
                Spacer(Modifier.height(14.dp))
            }
            BudgetProgressRow(budget = budget, isTotal = isTotal, onEdit = { onEdit(budget.category) })
        }
    }
}

@Composable
private fun BudgetProgressRow(budget: BudgetProgress, isTotal: Boolean, onEdit: () -> Unit) {
    // 항목 탭 = 편집 진입(해당 카테고리 한도 수정).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (isTotal) "전체" else budget.category.ifBlank { "미분류" },
                fontFamily = Pretendard,
                fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (isTotal) FgPrimary else FgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "₩%,d / ₩%,d".format(budget.spent, budget.limit),
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = (-0.01).em,
                color = FgPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 비율 바 — 1.0으로 클램프. 정상=AccentBlue(지출이라 AccentRed 미사용), 초과=AccentRed 절제 강조.
        val fraction = budget.ratio.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (budget.isOver) AccentRed else AccentBlue),
            )
        }
        if (budget.isOver) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "한도 초과 ₩%,d".format(budget.spent - budget.limit),
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = AccentRed,
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    initialCategory: String,
    existingLimits: Map<String, Long>,
    onSave: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 카테고리 선택지: 전체 월 한도(TOTAL)를 맨 앞, 이어서 FinanceCategory.all.
    val categories = listOf(BudgetEntity.TOTAL_CATEGORY) + FinanceCategory.all
    var category by remember { mutableStateOf(initialCategory) }
    // 기존 한도가 있으면 prefill. 카테고리 변경 시 해당 카테고리 한도로 재설정.
    var amount by remember {
        mutableStateOf(existingLimits[initialCategory]?.takeIf { it > 0 }?.toString() ?: "")
    }
    val parsed = amount.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val hasExisting = existingLimits.containsKey(category)

    fun labelFor(cat: String) = if (cat == BudgetEntity.TOTAL_CATEGORY) "전체 월 한도" else cat

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(18.dp),
        titleContentColor = FgPrimary,
        textContentColor = FgPrimary,
        title = {
            Text(
                text = "예산 설정",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = FgPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Category selector (horizontal scroll chips) — 전체 월 한도가 맨 앞.
                Column {
                    Text(
                        "카테고리",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEach { cat ->
                            val selected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) AccentBlue else Color.Transparent)
                                    .border(1.dp, if (selected) AccentBlue else Divider, CircleShape)
                                    .clickable {
                                        category = cat
                                        amount = existingLimits[cat]?.takeIf { it > 0 }?.toString() ?: ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    labelFor(cat),
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (selected) FgPrimary else FgSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // Amount input (한도, 원)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("월 한도", fontFamily = Pretendard, fontSize = 12.sp)
                    },
                    suffix = {
                        Text(
                            "원",
                            fontFamily = InstrumentSerif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = FgSecondary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                if (parsed > 0) {
                    Text(
                        text = "₩%,d / 월".format(parsed),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = FgTertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed > 0) onSave(category, parsed) },
                enabled = parsed > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
            ) {
                Text("저장", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // 기존 한도가 있을 때만 삭제 노출.
                if (hasExisting) {
                    TextButton(
                        onClick = { onDelete(category) },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed),
                    ) {
                        Text("삭제", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = FgSecondary),
                ) {
                    Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        },
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Pretendard, fontSize = 13.sp, color = FgDisabled)
    }
}
