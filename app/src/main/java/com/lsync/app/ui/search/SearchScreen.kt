package com.lsync.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.ui.theme.*

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToSchedule: () -> Unit,   // 일정/할일 결과 탭 시
    onNavigateToFinance: () -> Unit,    // 가계부 결과 탭 시
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val results = uiState.results

    val focusRequester = remember { FocusRequester() }
    // 진입 시 검색 바에 자동 포커스 → 키보드 노출
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        // ── 상단 검색 바 ──────────────────────────────────────────────
        SearchBar(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::clearQuery,
            onBack = onBack,
            focusRequester = focusRequester,
        )

        when {
            // 빈 쿼리 → 초기 안내
            uiState.query.isBlank() -> EmptyMessage("검색어를 입력하세요")
            // 검색은 했지만 결과 없음
            uiState.hasSearched && results.isEmpty -> EmptyMessage("검색 결과가 없어요")
            // 결과 목록
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (results.events.isNotEmpty()) {
                    item(key = "header_events") { SearchSectionHeader("일정") }
                    items(results.events, key = { "e_${it.id}" }) { event ->
                        EventRow(event, onClick = onNavigateToSchedule)
                    }
                }
                if (results.todos.isNotEmpty()) {
                    item(key = "header_todos") { SearchSectionHeader("할 일") }
                    items(results.todos, key = { "t_${it.id}" }) { todo ->
                        TodoRow(todo, onClick = onNavigateToSchedule)
                    }
                }
                if (results.finances.isNotEmpty()) {
                    item(key = "header_finances") { SearchSectionHeader("가계부") }
                    items(results.finances, key = { "f_${it.id}" }) { finance ->
                        FinanceRow(finance, onClick = onNavigateToFinance)
                    }
                }
            }
        }
    }
}

// ── 상단 검색 바 ──────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
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
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "일정 · 할 일 · 가계부 검색",
                    fontFamily = Pretendard, fontWeight = FontWeight.Normal,
                    fontSize = 16.sp, color = FgDisabled,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 16.sp, color = FgPrimary,
                ),
                cursorBrush = SolidColor(AccentBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "지우기",
                tint = FgSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
    }
}

// ── 섹션 헤더 ──────────────────────────────────────────────────────────────

@Composable
private fun SearchSectionHeader(label: String) {
    Text(
        text = label,
        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

// ── 결과 행 ────────────────────────────────────────────────────────────────

@Composable
private fun EventRow(event: EventEntity, onClick: () -> Unit) {
    val timeText = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp).height(44.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(AccentBlue),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            event.title,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            timeText,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp,
            letterSpacing = 0.08.em, color = FgTertiary,
            modifier = Modifier.padding(end = 14.dp),
        )
    }
}

@Composable
private fun TodoRow(todo: TodoEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
            text = todo.title,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
            color = if (todo.isCompleted) FgDisabled else FgPrimary,
            textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (todo.financeIsLinked) {
            val amt = todo.financeAmount?.let { "₩%,d".format(it) } ?: "미정"
            Text(
                amt, fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 12.sp,
                color = if (todo.financeType == "INCOME") AccentGreen else FgSecondary,
            )
        }
    }
}

@Composable
private fun FinanceRow(finance: FinanceEntity, onClick: () -> Unit) {
    val isIncome = finance.type == "INCOME"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                finance.category,
                fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!finance.note.isNullOrBlank()) {
                Text(
                    finance.note,
                    fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 11.sp,
                    color = FgSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "₩%,d".format(finance.amount),
            fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 13.sp,
            color = if (isIncome) AccentGreen else FgSecondary,
        )
    }
}

// ── 빈 상태 ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp,
            color = FgDisabled,
        )
    }
}
