package com.lsync.app.ui.bible

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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.dao.BibleBook
import com.lsync.app.data.local.entity.BibleVerseEntity
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class SavedMemo(val ref: String, val text: String, val date: String)

@Composable
fun BibleScreen(viewModel: BibleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    var anchored  by remember { mutableStateOf<Int?>(null) }
    var memoText  by remember { mutableStateOf("") }
    var savedMemo by remember { mutableStateOf<SavedMemo?>(null) }

    LaunchedEffect(state.currentBook, state.currentChapter) {
        anchored  = null
        memoText  = ""
        savedMemo = null
    }

    val today      = remember { LocalDate.now() }
    val todayLabel = today.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))
    val bookName   = state.books.find { it.book == state.currentBook }?.bookName ?: ""
    val anchoredRef = if (anchored != null) "$bookName ${state.currentChapter}장 ${anchored}절"
                      else "$bookName ${state.currentChapter}장"

    val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (state.isTableOfContentsOpen) {
        TableOfContentsSheet(
            books         = state.books,
            chapterCounts = state.chapterCounts,
            currentBook   = state.currentBook,
            currentChapter = state.currentChapter,
            sheetState    = tocSheetState,
            onNavigate    = { book, chapter -> viewModel.navigateTo(book, chapter) },
            onDismiss     = { viewModel.toggleTableOfContents() },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        if (state.isSearchActive) {
            // Search header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = FgSecondary, modifier = Modifier.size(22.dp))
                }
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = FgPrimary,
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    "성경 구절 검색",
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp,
                                    color = FgDisabled,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "지우기", tint = FgSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            // Normal header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "개역개정",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (bookName.isEmpty()) "" else "$bookName ${state.currentChapter}장",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        letterSpacing = (-0.035).em,
                        color = FgPrimary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ESV 토글
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (state.showEsv) AccentBlue else BgCard)
                            .border(1.dp, if (state.showEsv) Color.Transparent else HairlineWhite, RoundedCornerShape(19.dp))
                            .clickable { viewModel.toggleEsv() }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ESV",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (state.showEsv) Color.White else FgSecondary,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleSearch() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, CircleShape),
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = "검색", tint = FgPrimary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { viewModel.toggleTableOfContents() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, CircleShape),
                    ) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = "목차", tint = FgPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (state.isSearchActive) {
            SearchResults(
                query   = state.searchQuery,
                results = state.searchResults,
                onResultClick = { verse -> viewModel.navigateTo(verse.book, verse.chapter) },
            )
        } else {

        val pagerState = rememberPagerState(
            initialPage = maxOf(0, state.currentChapter - 1),
            pageCount   = { maxOf(1, state.chapterCount) },
        )

        // 페이저 이동 → ViewModel: 스와이프 시작 즉시 로드
        LaunchedEffect(pagerState.targetPage) {
            val ch = pagerState.targetPage + 1
            if (ch != state.currentChapter) viewModel.goToChapter(ch)
        }
        // ViewModel → 페이저: 목차·검색 등 외부 이동 시 점프
        LaunchedEffect(state.currentBook, state.currentChapter) {
            val page = state.currentChapter - 1
            if (pagerState.currentPage != page && !pagerState.isScrollInProgress) {
                pagerState.scrollToPage(page)
            }
        }

        // 장 네비 (페이저 실시간 반영)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { viewModel.prevChapter() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 장", tint = FgSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = "${pagerState.currentPage + 1} / ${state.chapterCount}",
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = FgSecondary,
            )
            Spacer(Modifier.width(14.dp))
            IconButton(
                onClick = { viewModel.nextChapter() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 장", tint = FgSecondary, modifier = Modifier.size(20.dp))
            }
        }

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
        val isCurrentPage = page == state.currentChapter - 1

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {

            // Verses
            items(if (isCurrentPage) state.verses else emptyList(), key = { it.idx }) { verse ->
                val isAnchored = anchored == verse.verse
                val esvVerse = state.esvVerses.find { it.verse == verse.verse }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAnchored) AccentBlue.copy(alpha = 0.07f) else Color.Transparent)
                        .clickable { anchored = if (isAnchored) null else verse.verse }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = verse.verse.toString(),
                        fontFamily = InstrumentSerif,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        letterSpacing = 0.02.em,
                        color = if (isAnchored) AccentBlue else FgTertiary,
                        modifier = Modifier.width(20.dp).padding(top = 2.dp),
                        textAlign = TextAlign.End,
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            text = verse.text,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            lineHeight = 29.6.sp,
                            letterSpacing = (-0.005).em,
                            color = FgPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.showEsv && esvVerse != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = esvVerse.text,
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.em,
                                color = FgSecondary,
                            )
                        }
                    }
                }
            }

            // Saved memo card (현재 장만)
            if (isCurrentPage) savedMemo?.let { memo ->
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "묵상 · ${memo.ref} · ${memo.date}".uppercase(),
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 0.12.em,
                            color = FgTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = memo.text,
                            fontFamily = Pretendard,
                            fontSize = 14.sp,
                            lineHeight = (14 * 1.55).sp,
                            color = FgPrimary,
                        )
                    }
                }
            }

            // Memo composer (현재 장, 절 선택 시)
            if (isCurrentPage && anchored != null) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "묵상 · $anchoredRef".uppercase(),
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.12.em,
                                color = FgTertiary,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "${todayLabel}에 연결".uppercase(),
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.12.em,
                                    color = AccentBlue,
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = Pretendard,
                                fontSize = 14.sp,
                                lineHeight = (14 * 1.55).sp,
                                color = FgPrimary,
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (memoText.isEmpty()) {
                                        Text(
                                            "이 말씀에 대한 묵상을 적어보세요",
                                            fontFamily = Pretendard,
                                            fontSize = 14.sp,
                                            color = FgDisabled,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { memoText = ""; anchored = null }) {
                                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
                            }
                            TextButton(
                                onClick = {
                                    savedMemo = SavedMemo(anchoredRef, memoText.trim(), todayLabel)
                                    memoText = ""
                                    anchored = null
                                },
                                enabled = memoText.isNotBlank(),
                            ) {
                                Text(
                                    "저장",
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (memoText.isNotBlank()) AccentBlue else FgDisabled,
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end LazyColumn
        } // end HorizontalPager + else
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<BibleVerseEntity>,
    onResultClick: (BibleVerseEntity) -> Unit,
) {
    when {
        query.length < 2 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "2자 이상 입력하세요",
                    fontFamily = Pretendard,
                    fontSize = 14.sp,
                    color = FgTertiary,
                )
            }
        }
        results.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "검색 결과가 없습니다",
                    fontFamily = Pretendard,
                    fontSize = 14.sp,
                    color = FgTertiary,
                )
            }
        }
        else -> {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        text = "${results.size}개 결과",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    )
                }
                items(results, key = { it.idx }) { verse ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(verse) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "${verse.bookName} ${verse.chapter}장 ${verse.verse}절",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.12.em,
                            color = AccentBlue,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = verse.text,
                            fontFamily = Pretendard,
                            fontSize = 15.sp,
                            lineHeight = (15 * 1.7).sp,
                            color = FgPrimary,
                        )
                    }
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun TableOfContentsSheet(
    books: List<BibleBook>,
    chapterCounts: Map<Int, Int>,
    currentBook: Int,
    currentChapter: Int,
    sheetState: SheetState,
    onNavigate: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedBook by remember { mutableStateOf<BibleBook?>(null) }

    val otBooks = books.filter { it.testament.startsWith("구") }
    val ntBooks = books.filter { it.testament.startsWith("신") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        contentColor = FgPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FgTertiary),
            )
        },
    ) {
        if (selectedBook == null) {
            // ── 1단계: 책 목록 ───────────────────────────────────────────
            Text(
                text = "목차",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = (-0.02).em,
                color = FgPrimary,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
                item {
                    Text(
                        text = "구약 · ${otBooks.size}권".uppercase(),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )
                }
                items(otBooks.chunked(3)) { row ->
                    BookChipRow(
                        row = row, currentBook = currentBook,
                        onNavigate = { book -> selectedBook = books.find { it.book == book } },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "신약 · ${ntBooks.size}권".uppercase(),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )
                }
                items(ntBooks.chunked(3)) { row ->
                    BookChipRow(
                        row = row, currentBook = currentBook,
                        onNavigate = { book -> selectedBook = books.find { it.book == book } },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // ── 2단계: 장 선택 ───────────────────────────────────────────
            val book = selectedBook!!
            val maxChapter = chapterCounts[book.book] ?: 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 22.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedBook = null }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "뒤로",
                        tint = FgSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = book.bookName,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.02).em,
                    color = FgPrimary,
                )
            }

            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
                items((1..maxChapter).toList().chunked(6)) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { chapter ->
                            val isSelected = book.book == currentBook && chapter == currentChapter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) FgPrimary else Color.Transparent)
                                    .border(1.dp, if (isSelected) Color.Transparent else HairlineWhite, RoundedCornerShape(10.dp))
                                    .clickable { onNavigate(book.book, chapter) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = chapter.toString(),
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) BgPrimary else FgPrimary,
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BookChipRow(
    row: List<BibleBook>,
    currentBook: Int,
    onNavigate: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        row.forEach { book ->
            val selected = book.book == currentBook
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) FgPrimary else Color.Transparent)
                    .border(1.dp, if (selected) Color.Transparent else HairlineWhite, RoundedCornerShape(10.dp))
                    .clickable { onNavigate(book.book) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = book.bookName,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (selected) BgPrimary else FgPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 마지막 행 빈 칸 채우기
        repeat(3 - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        decorationBox = decorationBox,
    )
}
