package com.lsync.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.ui.theme.*

@Composable
fun FinanceDashboard(
    modifier: Modifier = Modifier,
    viewModel: FinanceDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

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
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Pretendard, fontSize = 13.sp, color = FgDisabled)
    }
}
