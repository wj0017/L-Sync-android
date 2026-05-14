package com.lsync.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.ui.schedule.ScheduleItem
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 성경 책 영문명 (book 번호 → 영문, HomeScreen 통독 카드용)
private val BOOK_NAMES_SHORT = mapOf(
    1 to "Gen", 2 to "Exo", 3 to "Lev", 4 to "Num", 5 to "Deu",
    6 to "Jos", 7 to "Jdg", 8 to "Rut", 9 to "1Sa", 10 to "2Sa",
    11 to "1Ki", 12 to "2Ki", 13 to "1Ch", 14 to "2Ch",
    15 to "Ezr", 16 to "Neh", 17 to "Est", 18 to "Job", 19 to "Psa",
    20 to "Pro", 21 to "Ecc", 22 to "Sng", 23 to "Isa",
    24 to "Jer", 25 to "Lam", 26 to "Eze", 27 to "Dan",
    28 to "Hos", 29 to "Joe", 30 to "Amo", 31 to "Oba", 32 to "Jon",
    33 to "Mic", 34 to "Nah", 35 to "Hab", 36 to "Zep",
    37 to "Hag", 38 to "Zec", 39 to "Mal",
    40 to "Mat", 41 to "Mar", 42 to "Luk", 43 to "Joh", 44 to "Act",
    45 to "Rom", 46 to "1Co", 47 to "2Co", 48 to "Gal",
    49 to "Eph", 50 to "Phi", 51 to "Col",
    52 to "1Th", 53 to "2Th", 54 to "1Ti", 55 to "2Ti",
    56 to "Tit", 57 to "Phm", 58 to "Heb", 59 to "Jas",
    60 to "1Pe", 61 to "2Pe", 62 to "1Jn", 63 to "2Jn",
    64 to "3Jn", 65 to "Jud", 66 to "Rev",
)

@Composable
fun HomeScreen(
    onNavigateToSchedule: () -> Unit,
    onNavigateToBible: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgPrimary),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // ── 날짜 헤더 ────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)) {
                Text(
                    text = uiState.today.year.toString(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.12.em,
                    color = FgTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = uiState.today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.035).em,
                    color = FgPrimary,
                    lineHeight = (30 * 1.08).sp,
                )
            }
        }

        // ── 통독 카드 ─────────────────────────────────────────────────────────
        item {
            ReadingPlanCard(
                state = uiState.readingPlan,
                today = uiState.today,
                onChapterToggle = { book, chapter, isRead -> viewModel.markChapterRead(book, chapter, isRead) },
                onStart = { viewModel.startReadingPlan() },
                onNavigateToBible = onNavigateToBible,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }

        // ── 오늘 일정 & 할 일 ─────────────────────────────────────────────────
        item {
            HomeSectionHeader(
                label = "오늘 · 일정 & 할 일",
                count = uiState.todayItems.size,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        if (uiState.todayItems.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("오늘 일정·할 일이 없어요", fontSize = 14.sp, color = FgDisabled, fontFamily = Pretendard)
                }
            }
        } else {
            items(uiState.previewItems, key = {
                when (it) {
                    is ScheduleItem.Event -> "e_${it.entity.id}"
                    is ScheduleItem.Todo  -> "t_${it.entity.id}"
                }
            }) { item ->
                HomeScheduleRow(item = item, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
            }
            if (uiState.todayItems.size > 5) {
                item {
                    TextButton(
                        onClick = onNavigateToSchedule,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            "더 보기 (${uiState.todayItems.size - 5}개) →",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = AccentBlue,
                        )
                    }
                }
            }
        }

        // ── 가계부 요약 ───────────────────────────────────────────────────────
        item {
            HomeSectionHeader(
                label = "${uiState.today.monthValue}월 가계부",
                modifier = Modifier.padding(horizontal = 22.dp).padding(top = 20.dp, bottom = 6.dp),
            )
        }
        item {
            FinanceSummaryCard(
                income = uiState.monthIncome,
                expense = uiState.monthExpense,
                net = uiState.monthNet,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
            )
        }
    }
}

// ── 섹션 헤더 ─────────────────────────────────────────────────────────────────

@Composable
private fun HomeSectionHeader(label: String, count: Int = -1, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (count >= 0) "$label · $count" else label,
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.12.em,
            color = FgTertiary,
        )
    }
}

// ── 통독 카드 ─────────────────────────────────────────────────────────────────

@Composable
private fun ReadingPlanCard(
    state: ReadingPlanSectionState,
    today: LocalDate,
    onChapterToggle: (Int, Int, Boolean) -> Unit,
    onStart: () -> Unit,
    onNavigateToBible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .clickable(enabled = state.hasStarted, onClick = onNavigateToBible)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (!state.hasStarted) {
            // 시작 전 CTA
            Text("성경 통독", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
            Spacer(Modifier.height(8.dp))
            Text("1년 통독을 시작해보세요", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = FgPrimary)
            Spacer(Modifier.height(6.dp))
            Text("매일 3~4챕터, 365일 완독", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("오늘부터 시작", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            }
        } else {
            // 진행 중
            val startDate = state.startDate!!
            val dayNum = java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt() + 1

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("성경 통독", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
                Text(
                    "Day $dayNum / 365",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.05.em,
                    color = if (state.todayDoneCount == state.todayTotal && state.todayTotal > 0) AccentGreen else FgTertiary,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 오늘 챕터 목록
            if (state.entries.isEmpty()) {
                Text("오늘 통독 분량 없음", fontFamily = Pretendard, fontSize = 13.sp, color = FgDisabled)
            } else {
                state.entries.forEach { entry ->
                    ChapterCheckRow(
                        entry = entry,
                        onToggle = { onChapterToggle(entry.book, entry.chapter, !entry.isRead) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // 연간 진행 바
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("전체 진행", fontFamily = Pretendard, fontSize = 10.sp, letterSpacing = 0.1.em, color = FgTertiary)
                    Text("${state.totalRead} / ${ReadingPlanRepository.TOTAL_CHAPTERS}", fontFamily = Pretendard, fontSize = 10.sp, color = FgTertiary)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.overallProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AccentBlue,
                    trackColor = Divider,
                )
            }
        }
    }
}

@Composable
private fun ChapterCheckRow(entry: ReadingPlanEntity, onToggle: () -> Unit) {
    val bookShort = BOOK_NAMES_SHORT[entry.book] ?: "Bk${entry.book}"
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (entry.isRead) AccentGreen else Color.Transparent)
                .then(if (!entry.isRead) Modifier.border(1.5.dp, FgDisabled, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.isRead) Text("✓", fontFamily = Pretendard, fontSize = 10.sp, color = Color.White)
        }
        Text(
            text = "$bookShort ${entry.chapter}",
            fontFamily = Pretendard,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (entry.isRead) FgDisabled else FgPrimary,
            textDecoration = if (entry.isRead) TextDecoration.LineThrough else null,
        )
    }
}

// ── 오늘 일정·할일 미리보기 행 ────────────────────────────────────────────────

@Composable
private fun HomeScheduleRow(item: ScheduleItem, modifier: Modifier = Modifier) {
    when (item) {
        is ScheduleItem.Event -> {
            val event = item.entity
            val timeText = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .background(AccentBlue)
                )
                Spacer(Modifier.width(12.dp))
                Text(event.title, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(timeText, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.08.em, color = FgTertiary, modifier = Modifier.padding(end = 14.dp))
            }
        }
        is ScheduleItem.Todo -> {
            val todo = item.entity
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (todo.isCompleted) AccentBlue else Color.Transparent)
                        .then(if (!todo.isCompleted) Modifier.border(1.5.dp, FgDisabled, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (todo.isCompleted) Text("✓", fontFamily = Pretendard, fontSize = 9.sp, color = Color.White)
                }
                Text(
                    text = todo.title,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (todo.isCompleted) FgDisabled else FgPrimary,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (todo.financeIsLinked) {
                    val amt = todo.financeAmount?.let { "₩%,d".format(it) } ?: "미정"
                    Text(
                        text = amt,
                        fontFamily = InstrumentSerif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        color = if (todo.financeType == "INCOME") AccentGreen else FgSecondary,
                    )
                }
            }
        }
    }
}

// ── 가계부 요약 카드 ──────────────────────────────────────────────────────────

@Composable
private fun FinanceSummaryCard(
    income: Long,
    expense: Long,
    net: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // 잔액
        Text("이번 달 잔액", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (net >= 0) "+" else "−",
                fontFamily = InstrumentSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 28.sp,
                color = if (net >= 0) AccentGreen else AccentRed,
                modifier = Modifier.padding(bottom = 3.dp, end = 2.dp),
            )
            Text(
                text = "₩%,d".format(Math.abs(net)),
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                letterSpacing = (-0.03).em,
                color = FgPrimary,
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Divider, thickness = 0.5.dp)
        Spacer(Modifier.height(14.dp))

        // 수입 / 지출
        Row(modifier = Modifier.fillMaxWidth()) {
            FinanceAmountLine(label = "수입", amount = income, positive = true, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(32.dp).background(Divider).align(Alignment.CenterVertically))
            FinanceAmountLine(label = "지출", amount = expense, positive = false, modifier = Modifier.weight(1f), alignEnd = true)
        }
    }
}

@Composable
private fun FinanceAmountLine(
    label: String,
    amount: Long,
    positive: Boolean,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(modifier = modifier.padding(horizontal = 12.dp), horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.1.em, color = FgTertiary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "₩%,d".format(amount),
            fontFamily = Pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = (-0.015).em,
            color = if (positive) AccentGreen else FgSecondary,
        )
    }
}
