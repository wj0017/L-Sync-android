package com.lsync.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
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
import com.lsync.app.data.repository.ReadingOrder
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.data.repository.ReadingPlanSettings
import com.lsync.app.ui.schedule.ScheduleItem
import com.lsync.app.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale

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

private val BOOK_NAMES_KO = mapOf(
    1 to "창세기", 2 to "출애굽기", 3 to "레위기", 4 to "민수기", 5 to "신명기",
    6 to "여호수아", 7 to "사사기", 8 to "룻기", 9 to "사무엘상", 10 to "사무엘하",
    11 to "열왕기상", 12 to "열왕기하", 13 to "역대상", 14 to "역대하",
    15 to "에스라", 16 to "느헤미야", 17 to "에스더", 18 to "욥기", 19 to "시편",
    20 to "잠언", 21 to "전도서", 22 to "아가", 23 to "이사야",
    24 to "예레미야", 25 to "예레미야애가", 26 to "에스겔", 27 to "다니엘",
    28 to "호세아", 29 to "요엘", 30 to "아모스", 31 to "오바댜", 32 to "요나",
    33 to "미가", 34 to "나훔", 35 to "하박국", 36 to "스바냐",
    37 to "학개", 38 to "스가랴", 39 to "말라기",
    40 to "마태복음", 41 to "마가복음", 42 to "누가복음", 43 to "요한복음", 44 to "사도행전",
    45 to "로마서", 46 to "고린도전서", 47 to "고린도후서", 48 to "갈라디아서",
    49 to "에베소서", 50 to "빌립보서", 51 to "골로새서",
    52 to "데살로니가전서", 53 to "데살로니가후서", 54 to "디모데전서", 55 to "디모데후서",
    56 to "디도서", 57 to "빌레몬서", 58 to "히브리서", 59 to "야고보서",
    60 to "베드로전서", 61 to "베드로후서", 62 to "요한일서", 63 to "요한이서",
    64 to "요한삼서", 65 to "유다서", 66 to "요한계시록",
)

@Composable
fun HomeScreen(
    onNavigateToSchedule: () -> Unit,
    onNavigateToBible: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showSetupSheet) {
        ReadingPlanSetupSheet(
            settings = uiState.pendingSettings,
            isEditing = uiState.readingPlan.hasStarted,
            onSettingsChange = viewModel::updatePendingSettings,
            onConfirm = { viewModel.applySettings() },
            onDismiss = viewModel::closeSetupSheet,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgPrimary),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        // ── 날짜 헤더 ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 32.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // 앱 이름
                Text(
                    text = "L·SYNC",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = 0.06.em,
                    color = FgPrimary,
                )
                // 날짜 (우측 정렬)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = uiState.today.year.toString(),
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                        fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = uiState.today.format(DateTimeFormatter.ofPattern("MMM d, EEEE", Locale.ENGLISH)),
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, letterSpacing = (-0.01).em, color = FgSecondary,
                    )
                }
            }
        }

        // ── 섹션: 성경 통독 ──────────────────────────────────────────────────
        item { HomeSectionHeader(label = "성경 통독") }
        item {
            ReadingPlanCard(
                state = uiState.readingPlan,
                onChapterToggle = { book, ch, isRead -> viewModel.markChapterRead(book, ch, isRead) },
                onSetupClick = { viewModel.openSetupSheet() },
                onNavigateToBible = onNavigateToBible,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── 구분선 ───────────────────────────────────────────────────────────
        item { HomeSectionDivider() }

        // ── 섹션: 오늘 일정 & 할 일 ─────────────────────────────────────────
        item { HomeSectionHeader(label = "오늘 · 일정 & 할 일", count = uiState.todayItems.size) }

        if (uiState.todayItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                HomeScheduleRow(item = item, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
            }
            if (uiState.todayItems.size > 5) {
                item {
                    TextButton(
                        onClick = onNavigateToSchedule,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text(
                            "더 보기 (${uiState.todayItems.size - 5}개) →",
                            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                            fontSize = 13.sp, color = AccentBlue,
                        )
                    }
                }
            }
        }

        // ── 구분선 ───────────────────────────────────────────────────────────
        item { HomeSectionDivider() }

        // ── 섹션: 가계부 ─────────────────────────────────────────────────────
        item { HomeSectionHeader(label = "${uiState.today.monthValue}월 가계부") }
        item {
            FinanceSummaryCard(
                income = uiState.monthIncome,
                expense = uiState.monthExpense,
                net = uiState.monthNet,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

// ── 통독 설정 시트 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingPlanSetupSheet(
    settings: ReadingPlanSettings,
    isEditing: Boolean,
    onSettingsChange: (ReadingPlanSettings) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 책/장 피커 상태
    var pickerStep by remember { mutableStateOf<PickerStep>(PickerStep.Closed) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        contentColor = FgPrimary,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FgTertiary),
            )
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp)) {
            // 헤더
            item {
                Text(
                    text = if (isEditing) "통독 설정 편집" else "통독 시작",
                    fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp, letterSpacing = (-0.02).em, color = FgPrimary,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            // ── 읽기 순서 ─────────────────────────────────────────────────────
            item {
                SetupSectionLabel("읽기 순서")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReadingOrder.CANONICAL to "창세기 → 계시록",
                        ReadingOrder.NT_FIRST  to "신약 먼저",
                    ).forEach { (order, label) ->
                        val sel = settings.readingOrder == order
                        GhostChip(
                            label = label,
                            selected = sel,
                            onClick = { onSettingsChange(settings.copy(readingOrder = order)) },
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            // ── 일일 읽기량 ───────────────────────────────────────────────────
            item {
                SetupSectionLabel("일일 읽기량")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { n ->
                        val sel = settings.chaptersPerDay == n
                        GhostChip(
                            label = "${n}장",
                            selected = sel,
                            onClick = { onSettingsChange(settings.copy(chaptersPerDay = n)) },
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            // ── 시작 위치 ─────────────────────────────────────────────────────
            item {
                SetupSectionLabel("시작 위치")
                Spacer(Modifier.height(10.dp))

                when (pickerStep) {
                    PickerStep.Closed -> {
                        // 현재 선택 표시 (탭하면 책 선택 열림)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgPrimary)
                                .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp))
                                .clickable { pickerStep = PickerStep.BookPicker }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bookName = BOOK_NAMES_KO[settings.startBook] ?: "창세기"
                            Text("$bookName ${settings.startChapter}장", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgPrimary)
                            Text("변경 →", fontFamily = Pretendard, fontSize = 12.sp, color = AccentBlue)
                        }
                    }
                    PickerStep.BookPicker -> {
                        // 책 선택 그리드
                        Row(
                            modifier = Modifier.padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("책 선택", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
                        }
                        // OT 섹션
                        BookSectionLabel(if (settings.readingOrder == ReadingOrder.NT_FIRST) "신약 (먼저 읽기)" else "구약")
                        Spacer(Modifier.height(6.dp))
                        val firstBooks = if (settings.readingOrder == ReadingOrder.NT_FIRST) (40..66).toList() else (1..39).toList()
                        BookGrid(books = firstBooks, selectedBook = settings.startBook, onSelect = { book ->
                            onSettingsChange(settings.copy(startBook = book, startChapter = 1))
                            pickerStep = PickerStep.ChapterPicker
                        })
                        Spacer(Modifier.height(14.dp))
                        BookSectionLabel(if (settings.readingOrder == ReadingOrder.NT_FIRST) "구약 (나중에 읽기)" else "신약")
                        Spacer(Modifier.height(6.dp))
                        val secondBooks = if (settings.readingOrder == ReadingOrder.NT_FIRST) (1..39).toList() else (40..66).toList()
                        BookGrid(books = secondBooks, selectedBook = settings.startBook, onSelect = { book ->
                            onSettingsChange(settings.copy(startBook = book, startChapter = 1))
                            pickerStep = PickerStep.ChapterPicker
                        })
                    }
                    PickerStep.ChapterPicker -> {
                        val maxChapter = ReadingPlanRepository.CHAPTER_COUNTS[settings.startBook - 1]
                        val bookName = BOOK_NAMES_KO[settings.startBook] ?: ""
                        Row(
                            modifier = Modifier.padding(bottom = 10.dp).clickable { pickerStep = PickerStep.BookPicker },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = FgSecondary, modifier = Modifier.size(16.dp))
                            Text(bookName, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
                        }
                        ChapterGrid(
                            maxChapter = maxChapter,
                            selectedChapter = settings.startChapter,
                            onSelect = { ch ->
                                onSettingsChange(settings.copy(startChapter = ch))
                                pickerStep = PickerStep.Closed
                            },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            // ── 확인 버튼 ─────────────────────────────────────────────────────
            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        if (isEditing) "저장하기" else "통독 시작하기",
                        fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, color = Color.White,
                    )
                }
            }
        }
    }
}

private sealed class PickerStep {
    object Closed : PickerStep()
    object BookPicker : PickerStep()
    object ChapterPicker : PickerStep()
}

@Composable
private fun SetupSectionLabel(text: String) {
    Text(text, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
}

@Composable
private fun BookSectionLabel(text: String) {
    Text(text.uppercase(), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
}

@Composable
private fun GhostChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentBlue else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else HairlineWhite, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = if (selected) Color.White else FgSecondary)
    }
}

@Composable
private fun BookGrid(books: List<Int>, selectedBook: Int, onSelect: (Int) -> Unit) {
    books.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { bookNum ->
                val sel = bookNum == selectedBook
                Box(
                    modifier = Modifier
                        .weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) FgPrimary else Color.Transparent)
                        .border(1.dp, if (sel) Color.Transparent else HairlineWhite, RoundedCornerShape(10.dp))
                        .clickable { onSelect(bookNum) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = BOOK_NAMES_KO[bookNum] ?: "",
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                        color = if (sel) BgPrimary else FgPrimary,
                        maxLines = 1,
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChapterGrid(maxChapter: Int, selectedChapter: Int, onSelect: (Int) -> Unit) {
    (1..maxChapter).toList().chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { ch ->
                val sel = ch == selectedChapter
                Box(
                    modifier = Modifier
                        .weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) FgPrimary else Color.Transparent)
                        .border(1.dp, if (sel) Color.Transparent else HairlineWhite, RoundedCornerShape(10.dp))
                        .clickable { onSelect(ch) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch.toString(), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (sel) BgPrimary else FgPrimary)
                }
            }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── 통독 카드 ─────────────────────────────────────────────────────────────────

@Composable
private fun ReadingPlanCard(
    state: ReadingPlanSectionState,
    onChapterToggle: (Int, Int, Boolean) -> Unit,
    onSetupClick: () -> Unit,
    onNavigateToBible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (!state.hasStarted) {
            Text("성경 통독", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
            Spacer(Modifier.height(8.dp))
            Text("1년 통독을 시작해보세요", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = FgPrimary)
            Spacer(Modifier.height(6.dp))
            Text("시작 위치·순서·읽기량을 자유롭게 설정할 수 있어요", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSetupClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("설정하고 시작", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            }
        } else {
            // 헤더 행
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("성경 통독", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Day ${state.dayNumber} / ${state.estimatedTotalDays}",
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp,
                        color = if (state.todayDoneCount == state.todayTotal && state.todayTotal > 0) AccentGreen else FgTertiary,
                    )
                    // 편집 버튼
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BgPrimary)
                            .border(1.dp, HairlineWhite, CircleShape)
                            .clickable { onSetupClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = FgTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (state.entries.isEmpty()) {
                Text("오늘 분량을 다 읽었어요!", fontFamily = Pretendard, fontSize = 13.sp, color = AccentGreen)
            } else {
                state.entries.forEach { entry ->
                    ChapterCheckRow(
                        entry = entry,
                        onToggle = { onChapterToggle(entry.book, entry.chapter, !entry.isRead) },
                        onNavigate = onNavigateToBible,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
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
private fun ChapterCheckRow(entry: ReadingPlanEntity, onToggle: () -> Unit, onNavigate: () -> Unit) {
    val bookShort = BOOK_NAMES_SHORT[entry.book] ?: "Bk${entry.book}"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (entry.isRead) AccentGreen else Color.Transparent)
                .then(if (!entry.isRead) Modifier.border(1.5.dp, FgDisabled, CircleShape) else Modifier)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (entry.isRead) Text("✓", fontFamily = Pretendard, fontSize = 10.sp, color = Color.White)
        }
        Text(
            text = "$bookShort ${entry.chapter}",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            color = if (entry.isRead) FgDisabled else FgPrimary,
            textDecoration = if (entry.isRead) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).clickable { onNavigate() },
        )
    }
}

// ── 공통 컴포넌트 ─────────────────────────────────────────────────────────────

@Composable
private fun HomeSectionHeader(label: String, count: Int = -1) {
    Text(
        text = if (count >= 0) "$label · $count" else label,
        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp),
    )
}

@Composable
private fun HomeSectionDivider() {
    HorizontalDivider(
        color = Divider,
        thickness = 0.5.dp,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 20.dp),
    )
}

@Composable
private fun HomeScheduleRow(item: ScheduleItem, modifier: Modifier = Modifier) {
    when (item) {
        is ScheduleItem.Event -> {
            val event = item.entity
            val timeText = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5)
            Row(
                modifier = modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)).background(AccentBlue))
                Spacer(Modifier.width(12.dp))
                Text(event.title, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(timeText, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.08.em, color = FgTertiary, modifier = Modifier.padding(end = 14.dp))
            }
        }
        is ScheduleItem.Todo -> {
            val todo = item.entity
            Row(
                modifier = modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape)
                        .background(if (todo.isCompleted) AccentBlue else Color.Transparent)
                        .then(if (!todo.isCompleted) Modifier.border(1.5.dp, FgDisabled, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (todo.isCompleted) Text("✓", fontFamily = Pretendard, fontSize = 9.sp, color = Color.White)
                }
                Text(
                    text = todo.title, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    color = if (todo.isCompleted) FgDisabled else FgPrimary,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (todo.financeIsLinked) {
                    val amt = todo.financeAmount?.let { "₩%,d".format(it) } ?: "미정"
                    Text(amt, fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 12.sp,
                        color = if (todo.financeType == "INCOME") AccentGreen else FgSecondary)
                }
            }
        }
    }
}

// ── 가계부 요약 카드 ──────────────────────────────────────────────────────────

@Composable
private fun FinanceSummaryCard(income: Long, expense: Long, net: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 잔액 행
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "잔액",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (net >= 0) "+" else "−",
                    fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 17.sp,
                    color = if (net >= 0) AccentGreen else AccentRed,
                )
                Text(
                    text = "₩%,d".format(Math.abs(net)),
                    fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp, letterSpacing = (-0.025).em, color = FgPrimary,
                )
            }
        }

        // 수입 / 지출 행
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("수입", fontFamily = Pretendard, fontSize = 10.sp, letterSpacing = 0.08.em, color = FgTertiary)
                Text(
                    "₩%,d".format(income),
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp, letterSpacing = (-0.01).em, color = AccentGreen,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("지출", fontFamily = Pretendard, fontSize = 10.sp, letterSpacing = 0.08.em, color = FgTertiary)
                Text(
                    "₩%,d".format(expense),
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp, letterSpacing = (-0.01).em, color = FgSecondary,
                )
            }
        }
    }
}
