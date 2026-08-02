package com.lsync.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.ui.theme.*
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // 미래 월 가드는 ViewModel이 하지만, 버튼을 흐리게 해 눌러도 소용없음을 미리 알린다.
    val canGoNext = state.yearMonth.isBefore(YearMonth.now())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "topbar") {
            ReportTopBar(onBack = onBack)
        }

        item(key = "month") {
            MonthSelector(
                yearMonth = state.yearMonth,
                canGoNext = canGoNext,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        }

        item(key = "todo") {
            Column {
                SectionHeader("할 일")
                TodoCard(
                    total = state.todoTotal,
                    completed = state.todoCompleted,
                    rate = state.todoCompletionRate,
                )
            }
        }

        item(key = "event") {
            Column {
                SectionHeader("일정")
                EventCard(count = state.eventCount)
            }
        }

        item(key = "finance") {
            Column {
                SectionHeader("가계부")
                FinanceCard(
                    expense = state.expense,
                    income = state.income,
                    net = state.net,
                    topCategories = state.topCategories,
                )
            }
        }

        item(key = "reading") {
            Column {
                SectionHeader("성경 통독")
                ReadingCard(chaptersRead = state.chaptersRead, daysRead = state.daysRead)
            }
        }
    }
}

// ── 상단 바 ────────────────────────────────────────────────────────────────

@Composable
private fun ReportTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "뒤로",
            tint = FgPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(8.dp)
                .size(22.dp),
        )
        Text(
            text = "리포트",
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, letterSpacing = (-0.02).em, color = FgPrimary,
        )
    }
}

// ── 월 선택 ────────────────────────────────────────────────────────────────

@Composable
private fun MonthSelector(
    yearMonth: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ChevronLeft,
            contentDescription = "이전 달",
            tint = FgSecondary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onPrevious)
                .padding(8.dp)
                .size(22.dp),
        )
        Text(
            text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, letterSpacing = (-0.035).em, color = FgPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = "다음 달",
            // 현재 월이면 더 갈 곳이 없으므로 비활성 표시(클릭도 막음).
            tint = if (canGoNext) FgSecondary else FgDisabled,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = canGoNext, onClick = onNext)
                .padding(8.dp)
                .size(22.dp),
        )
    }
}

// ── 섹션 헤더 / 카드 컨테이너 ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReportCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content,
    )
}

// ── 할 일 — 완료율 링 ────────────────────────────────────────────────────────

@Composable
private fun TodoCard(total: Int, completed: Int, rate: Float) {
    ReportCard {
        if (total == 0) {
            EmptyHint("마감일 있는 할 일 없음")
            return@ReportCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CompletionRing(rate = rate)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$completed",
                        fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp, letterSpacing = (-0.035).em, color = FgPrimary,
                        lineHeight = 30.sp,
                    )
                    Text(
                        text = " / $total 완료",
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, color = FgSecondary,
                    )
                }
                Text(
                    text = "마감일 있는 할 일 기준",
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 11.sp, color = FgTertiary,
                )
            }
        }
    }
}

// 완료율 링 — 차트 라이브러리 미사용, Canvas 직접 드로잉.
@Composable
private fun CompletionRing(rate: Float) {
    val percent = (rate.coerceIn(0f, 1f) * 100).roundToInt()
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // 트랙
            drawArc(
                color = Divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // 진행 — 12시 방향에서 시계 방향
            if (percent > 0) {
                drawArc(
                    color = AccentBlue,
                    startAngle = -90f,
                    sweepAngle = 360f * rate.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "$percent%",
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, letterSpacing = (-0.02).em, color = FgPrimary,
        )
    }
}

// ── 일정 ──────────────────────────────────────────────────────────────────

@Composable
private fun EventCard(count: Int) {
    ReportCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%,d".format(count),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp, letterSpacing = (-0.035).em, color = FgPrimary,
                lineHeight = 36.sp,
            )
            Text(
                text = " 건",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 15.sp, color = FgSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "이달 일정",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, color = FgTertiary,
        )
    }
}

// ── 가계부 ────────────────────────────────────────────────────────────────

@Composable
private fun FinanceCard(
    expense: Long,
    income: Long,
    net: Long,
    topCategories: List<CategorySlice>,
) {
    ReportCard {
        // 순지출 — 지출은 중립색(AccentRed 미사용), 기호만 InstrumentSerif Italic.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "−",
                fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic,
                fontSize = 28.sp, color = FgSecondary, lineHeight = 28.sp,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "₩",
                fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic,
                fontSize = 28.sp, color = FgSecondary, lineHeight = 28.sp,
            )
            Text(
                text = "%,d".format(expense),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp, letterSpacing = (-0.035).em, color = FgPrimary,
                lineHeight = 32.sp,
            )
        }
        Text(
            text = "이달 순지출",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, color = FgTertiary,
        )

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            FinanceStat(
                label = "수입",
                text = "+₩%,d".format(income),
                color = AccentGreen,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.width(1.dp).height(36.dp).background(Divider)
                    .align(Alignment.CenterVertically),
            )
            FinanceStat(
                label = "잔액",
                text = (if (net >= 0) "+₩%,d" else "−₩%,d").format(kotlin.math.abs(net)),
                color = if (net >= 0) FgPrimary else AccentRed,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
        }

        if (topCategories.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "카테고리 상위",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary,
            )
            Spacer(Modifier.height(12.dp))
            CategoryBars(slices = topCategories)
        }
    }
}

@Composable
private fun FinanceStat(
    label: String,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary,
        )
        Text(
            text = text,
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, letterSpacing = (-0.01).em, color = color,
        )
    }
}

// 카테고리 비율 막대 — Canvas 직접 드로잉(트랙 + 채움). 지출이라 AccentRed 미사용.
@Composable
private fun CategoryBars(slices: List<CategorySlice>) {
    val maxAmount = slices.maxOfOrNull { it.amount } ?: 0L
    if (maxAmount <= 0L) {
        EmptyHint("표시할 지출이 없습니다")
        return
    }
    slices.forEachIndexed { i, slice ->
        if (i > 0) Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = slice.category.ifBlank { "미분류" },
                fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "−₩%,d".format(slice.amount),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                letterSpacing = (-0.01).em, color = FgPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        val fraction = (slice.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = Divider, size = size, cornerRadius = radius)
            val filled = size.width * fraction
            if (filled > 0f) {
                drawRoundRect(
                    color = FgSecondary,
                    size = Size(filled.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

// ── 통독 ──────────────────────────────────────────────────────────────────

@Composable
private fun ReadingCard(chaptersRead: Int, daysRead: Int) {
    ReportCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%,d".format(chaptersRead),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp, letterSpacing = (-0.035).em, color = FgPrimary,
                lineHeight = 30.sp,
            )
            Text(
                text = "장 · ${daysRead}일 읽음",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = FgSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "이달 통독 진행",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, color = FgTertiary,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Pretendard, fontSize = 13.sp, color = FgDisabled)
    }
}
