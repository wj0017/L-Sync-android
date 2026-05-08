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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Phase 3 플레이스홀더 — Firestore bible 컬렉션 연동 전 샘플 데이터
private data class Verse(val number: Int, val text: String)

private val sampleVerses = listOf(
    Verse(1,  "태초에 하나님이 천지를 창조하시니라"),
    Verse(2,  "땅이 혼돈하고 공허하며 흑암이 깊음 위에 있고 하나님의 영은 수면 위에 운행하시니라"),
    Verse(3,  "하나님이 이르시되 빛이 있으라 하시니 빛이 있었고"),
    Verse(4,  "빛이 하나님이 보시기에 좋았더라 하나님이 빛과 어둠을 나누사"),
    Verse(5,  "하나님이 빛을 낮이라 부르시고 어둠을 밤이라 부르시니라 저녁이 되고 아침이 되니 이는 첫째 날이니라"),
    Verse(6,  "하나님이 이르시되 물 가운데에 궁창이 있어 물과 물로 나뉘라 하시고"),
    Verse(7,  "하나님이 궁창을 만드사 궁창 아래의 물과 궁창 위의 물로 나뉘게 하시니 그대로 되니라"),
)

private data class SavedMemo(val ref: String, val text: String, val date: String)

@Composable
fun BibleScreen() {
    var chapter    by remember { mutableIntStateOf(1) }
    var anchored   by remember { mutableStateOf<Int?>(null) }
    var memoText   by remember { mutableStateOf("") }
    var savedMemo  by remember { mutableStateOf<SavedMemo?>(null) }

    val today = remember { LocalDate.now() }
    val todayLabel = today.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))
    val anchoredRef = if (anchored != null) "창세기 ${chapter}장 ${anchored}절" else "창세기 ${chapter}장"

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
                    text = "창세기 ${chapter}장",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.035).em,
                    color = FgPrimary,
                )
            }
            IconButton(
                onClick = { /* TODO: 목차 */ },
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
                        onClick = { if (chapter > 1) { chapter--; anchored = null } },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 장", tint = FgSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "$chapter / 50",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = FgSecondary,
                    )
                    Spacer(Modifier.width(14.dp))
                    IconButton(
                        onClick = { chapter++; anchored = null },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 장", tint = FgSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Verses
            items(sampleVerses, key = { it.number }) { verse ->
                val isAnchored = anchored == verse.number
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAnchored) AccentBlue.copy(alpha = 0.07f) else Color.Transparent)
                        .clickable { anchored = if (isAnchored) null else verse.number }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Verse number — Instrument Serif Italic (성경 절 번호 프리미엄 강세)
                    Text(
                        text = verse.number.toString(),
                        fontFamily = InstrumentSerif,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        letterSpacing = 0.02.em,
                        color = if (isAnchored) AccentBlue else FgTertiary,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.End,
                    )
                    // Body text — 1.85 line-height, 한국어 최적 가독성
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
                        // Composer header
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

                        // Textarea
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
                                Text("저장", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                    color = if (memoText.isNotBlank()) AccentBlue else FgDisabled)
                            }
                        }
                    }
                }
            }
        }
    }
}

// BasicTextField import
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
