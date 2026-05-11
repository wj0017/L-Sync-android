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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            books       = state.books,
            currentBook = state.currentBook,
            sheetState  = tocSheetState,
            onNavigate  = { book -> viewModel.navigateTo(book, 1) },
            onDismiss   = { viewModel.toggleTableOfContents() },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
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

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // Chapter nav
            item {
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
                        text = "${state.currentChapter} / ${state.chapterCount}",
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
            }

            // Verses
            items(state.verses, key = { it.idx }) { verse ->
                val isAnchored = anchored == verse.verse
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
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = verse.text,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        lineHeight = 29.6.sp,
                        letterSpacing = (-0.005).em,
                        color = FgPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Saved memo card
            savedMemo?.let { memo ->
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

            // Memo composer (절 선택 시 표시)
            if (anchored != null) {
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
    }
}

@Composable
private fun TableOfContentsSheet(
    books: List<BibleBook>,
    currentBook: Int,
    sheetState: SheetState,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val otBooks = books.filter { it.testament == "구약" }
    val ntBooks = books.filter { it.testament == "신약" }

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
                BookChipRow(row = row, currentBook = currentBook, onNavigate = onNavigate)
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
                BookChipRow(row = row, currentBook = currentBook, onNavigate = onNavigate)
                Spacer(Modifier.height(8.dp))
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
