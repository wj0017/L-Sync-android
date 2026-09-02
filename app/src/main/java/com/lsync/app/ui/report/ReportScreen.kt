package com.lsync.app.ui.report

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.export.buildImageShareIntent
import com.lsync.app.data.export.buildTextShareIntent
import com.lsync.app.data.export.savePngForShare
import com.lsync.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // 미래 월 가드는 ViewModel이 하지만, 버튼을 흐리게 해 눌러도 소용없음을 미리 알린다.
    val canGoNext = state.yearMonth.isBefore(YearMonth.now())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 공유는 화면 로컬 동작이라 ReportViewModel에 상태를 두지 않는다.
    // 렌더된 Bitmap은 수 MB라 ViewModel에 두면 화면 회전·백스택 이탈 후에도 붙잡혀 있게 된다.
    var showFormatSheet by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // FinanceScreen의 "L-Sync 가계부 {yearMonth}" 관례를 따른다.
    val subject = "L-Sync 월간 리포트 ${state.yearMonth}"
    val shareText = {
        val intent = buildTextShareIntent(buildReportSummaryText(state), subject)
        context.startActivity(Intent.createChooser(intent, "내보내기"))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "topbar") {
                ReportTopBar(onBack = onBack, onShare = { showFormatSheet = true })
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

        // 1080px 렌더에 수백 ms가 걸린다. 표시가 없으면 앱이 멈춘 것처럼 보인다.
        if (isRendering) RenderingOverlay()

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showFormatSheet) {
        ExportFormatSheet(
            onDismiss = { showFormatSheet = false },
            onImage = {
                showFormatSheet = false
                scope.launch {
                    isRendering = true
                    val bitmap = renderReportCard(context, state)
                    isRendering = false
                    if (bitmap == null) {
                        // 조용히 실패시키지 않는다 — 텍스트 공유를 대안으로 제시한다.
                        val result = snackbarHostState.showSnackbar(
                            message = "이미지를 만들지 못했습니다",
                            actionLabel = "텍스트로 공유",
                        )
                        if (result == SnackbarResult.ActionPerformed) shareText()
                    } else {
                        // 렌더 결과를 검증할 자동화 수단이 없다. 미리보기가 유일한 시각 검증 지점이다.
                        previewBitmap = bitmap
                    }
                }
            },
            onText = {
                showFormatSheet = false
                shareText()
            },
        )
    }

    previewBitmap?.let { bitmap ->
        ReportPreviewDialog(
            bitmap = bitmap,
            // 닫을 때 참조를 놓아 수 MB 비트맵이 GC되게 한다.
            onDismiss = { previewBitmap = null },
            onShare = {
                scope.launch {
                    val uri = savePngForShare(context, bitmap, "report_${state.yearMonth}.png")
                    // 파일은 이미 디스크에 있으므로 여기서 참조를 놓아도 공유에 영향이 없다.
                    // (캐시 파일 자체는 지우지 않는다 — 수신 앱이 비동기로 읽는다.)
                    previewBitmap = null
                    if (uri == null) {
                        val result = snackbarHostState.showSnackbar(
                            message = "이미지를 저장하지 못했습니다",
                            actionLabel = "텍스트로 공유",
                        )
                        if (result == SnackbarResult.ActionPerformed) shareText()
                    } else {
                        context.startActivity(
                            Intent.createChooser(buildImageShareIntent(context, uri, subject), "내보내기"),
                        )
                    }
                }
            },
        )
    }
}

// ── 상단 바 ────────────────────────────────────────────────────────────────

@Composable
private fun ReportTopBar(onBack: () -> Unit, onShare: () -> Unit) {
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
        // 뒤로·제목은 spacedBy(4dp)로 붙어 있다. 공유 아이콘만 오른쪽 끝으로 민다.
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = "내보내기",
            tint = FgPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onShare)
                .padding(8.dp)
                .size(20.dp),
        )
    }
}

// ── 내보내기 (형식 선택 → 미리보기 → 공유) ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatSheet(
    onDismiss: () -> Unit,
    onImage: () -> Unit,
    onText: () -> Unit,
) {
    // 시트 스타일(배경·드래그 핸들)은 HomeScreen의 통독 설정 시트를 따른다.
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
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 40.dp)) {
            Text(
                text = "내보내기",
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp, letterSpacing = (-0.02).em, color = FgPrimary,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            ExportFormatRow("이미지로 공유", "리포트 카드를 PNG로 만듭니다", onImage)
            Spacer(Modifier.height(8.dp))
            ExportFormatRow("텍스트로 공유", "요약을 텍스트로 붙여 넣습니다", onText)
        }
    }
}

@Composable
private fun ExportFormatRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgPrimary)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, letterSpacing = (-0.005).em, color = FgPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = desc,
            fontFamily = Pretendard, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, color = FgSecondary,
        )
    }
}

@Composable
private fun RenderingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 렌더 중 뒤 목록이 눌리지 않게 탭을 흡수한다(리플 없음).
            // clickable(enabled = false)는 입력을 아예 받지 않아 뒤로 그대로 통과한다.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .background(Color(0xCC0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "이미지 만드는 중…",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, color = FgSecondary,
            )
        }
    }
}

@Composable
private fun ReportPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, HairlineWhite, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(
                text = "미리보기",
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, letterSpacing = (-0.02).em, color = FgPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // 가로 폭에 맞춰 축소하고, 세로가 길면 스크롤로 전체를 확인한다.
            Box(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "리포트 미리보기",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("취소", filled = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                DialogButton("공유하기", filled = true, onClick = onShare, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) AccentBlue else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else HairlineWhite, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, color = if (filled) Color.White else FgSecondary,
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

// ── 공유용 카드 ────────────────────────────────────────────────────────────
//
// PNG로 렌더링(ReportCardRenderer.renderReportCard)하기 위한 오프스크린 전용 레이아웃.
// 위의 private 카드 Composable을 그대로 재사용하므로 화면과 공유 이미지가 갈라지지 않는다.
// (같은 파일에 두는 이유 = 카드들을 internal로 승격하지 않기 위해서다.)
//
// 최상위는 반드시 Column — 이 Composable은 높이 UNSPECIFIED MeasureSpec으로 측정되므로
// LazyColumn/verticalScroll을 쓰면 "infinity maximum height constraints" 예외로 죽는다.
// elevation·shadow·blur도 금지: 소프트웨어 Canvas에서 제대로 렌더되지 않는다.
@Composable
internal fun ReportShareCard(state: ReportUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .padding(top = 28.dp, bottom = 24.dp),
    ) {
        // 헤더 — 로케일 의존 포매터 없이 직접 조립(기기 설정과 무관하게 같은 문구).
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "L-Sync 월간 리포트",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "%d년 %d월".format(state.yearMonth.year, state.yearMonth.monthValue),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp, letterSpacing = (-0.035).em, color = FgPrimary,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 인자는 ReportScreen의 LazyColumn과 완전히 동일하게 넘긴다.
        SectionHeader("할 일")
        TodoCard(
            total = state.todoTotal,
            completed = state.todoCompleted,
            rate = state.todoCompletionRate,
        )

        SectionHeader("일정")
        EventCard(count = state.eventCount)

        SectionHeader("가계부")
        FinanceCard(
            expense = state.expense,
            income = state.income,
            net = state.net,
            topCategories = state.topCategories,
        )

        SectionHeader("성경 통독")
        ReadingCard(chaptersRead = state.chaptersRead, daysRead = state.daysRead)

        Spacer(Modifier.height(20.dp))
        Text(
            text = "L-Sync",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 10.sp, letterSpacing = 0.12.em, color = FgDisabled,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
