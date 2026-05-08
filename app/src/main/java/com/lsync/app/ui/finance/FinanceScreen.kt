package com.lsync.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.FinanceEntity
import androidx.compose.ui.text.font.FontStyle
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FinanceScreen(viewModel: FinanceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

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
            IconButton(
                onClick = { /* TODO: 거래 추가 */ },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, CircleShape),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "추가", tint = FgPrimary, modifier = Modifier.size(20.dp))
            }
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
                    expense = uiState.expense,
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
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
private fun TransactionGroupCard(transactions: List<FinanceEntity>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp)),
    ) {
        transactions.forEachIndexed { i, tx ->
            if (i > 0) Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Divider))
            TransactionRow(tx = tx)
        }
    }
}

@Composable
private fun TransactionRow(tx: FinanceEntity) {
    val isIncome = tx.type == "INCOME"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
    }
}
