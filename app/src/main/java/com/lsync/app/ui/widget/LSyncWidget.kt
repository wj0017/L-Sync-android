package com.lsync.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

private val BgPrimary   = ColorProvider(Color(0xFF0A0A0A))
private val BgCard      = ColorProvider(Color(0xFF161616))
private val FgPrimary   = ColorProvider(Color(0xFFF5F5F5))
private val FgSecondary = ColorProvider(Color(0x99F5F5F5))
private val AccentBlue  = ColorProvider(Color(0xFF4F7EFF))
private val AccentGreen = ColorProvider(Color(0xFF43A047))
private val AccentRed   = ColorProvider(Color(0xFFE53935))

private val BOOK_NAMES = arrayOf(
    "",
    "창세기", "출애굽기", "레위기", "민수기", "신명기",
    "여호수아", "사사기", "룻기", "사무엘상", "사무엘하",
    "열왕기상", "열왕기하", "역대상", "역대하", "에스라",
    "느헤미야", "에스더", "욥기", "시편", "잠언",
    "전도서", "아가", "이사야", "예레미야", "예레미야애가",
    "에스겔", "다니엘", "호세아", "요엘", "아모스",
    "오바댜", "요나", "미가", "나훔", "하박국",
    "스바냐", "학개", "스가랴", "말라기",
    "마태복음", "마가복음", "누가복음", "요한복음", "사도행전",
    "로마서", "고린도전서", "고린도후서", "갈라디아서", "에베소서",
    "빌립보서", "골로새서", "데살로니가전서", "데살로니가후서", "디모데전서",
    "디모데후서", "디도서", "빌레몬서", "히브리서", "야고보서",
    "베드로전서", "베드로후서", "요한일서", "요한이서", "요한삼서",
    "유다서", "요한계시록"
)

private fun formatAmount(amount: Long): String = String.format("%,d", amount)

private fun extractTimePrefix(startDate: String): String {
    return try {
        val tIndex = startDate.indexOf('T')
        if (tIndex >= 0 && startDate.length >= tIndex + 6) startDate.substring(tIndex + 1, tIndex + 6)
        else ""
    } catch (e: Exception) {
        ""
    }
}

class LSyncWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadWidgetState(context)
        provideContent {
            WidgetContent(state)
        }
    }

    private suspend fun loadWidgetState(context: Context): WidgetState {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val userId = entryPoint.authRepository().currentUserId
            ?: return WidgetState.empty()

        val today = LocalDate.now()
        val todayStr = today.toString()
        val monthStart = todayStr.substring(0, 7) + "-01"
        val monthEnd = todayStr.substring(0, 7) + "-31"

        val todos = entryPoint.todoDao().getIncompleteByDate(todayStr)
        val events = entryPoint.eventDao().getByDate(todayStr)

        val financeItems = entryPoint.financeDao().getAllByDateRange(userId, monthStart, monthEnd)
        val monthExpense = financeItems
            .filter { it.type == "EXPENSE" && it.settlementGroupId == null }
            .sumOf { it.amount }
        val monthIncome = financeItems
            .filter { it.type == "INCOME" && it.settlementGroupId == null }
            .sumOf { it.amount }

        val todayReadingPlan = entryPoint.readingPlanDao().getForDate(todayStr)
        val readCount = todayReadingPlan.count { it.isRead }
        val totalCount = todayReadingPlan.size

        val dayOfWeek = when (today.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            else -> "일"
        }
        val dateLabel = "${today.monthValue}월 ${today.dayOfMonth}일 ($dayOfWeek)"

        return WidgetState(
            todos = todos,
            events = events,
            monthExpense = monthExpense,
            monthIncome = monthIncome,
            todayReadingPlan = todayReadingPlan,
            readCount = readCount,
            totalCount = totalCount,
            dateLabel = dateLabel,
        )
    }
}

@Composable
fun WidgetContent(state: WidgetState) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(BgPrimary).padding(8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetHeader(state.dateLabel)
            Spacer(modifier = GlanceModifier.height(4.dp))
            TodoCard(state.todos)
            Spacer(modifier = GlanceModifier.height(2.dp))
            EventCard(state.events)
            Spacer(modifier = GlanceModifier.height(2.dp))
            FinanceCard(state.monthExpense, state.monthIncome)
            Spacer(modifier = GlanceModifier.height(2.dp))
            ReadingCard(state.todayReadingPlan, state.readCount, state.totalCount)
        }
    }
}

@Composable
private fun WidgetHeader(dateLabel: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "L·SYNC",
            style = TextStyle(color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        if (dateLabel.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = dateLabel,
                style = TextStyle(color = FgSecondary, fontSize = 9.sp),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(text = label, style = TextStyle(color = AccentBlue, fontSize = 9.sp))
    Spacer(modifier = GlanceModifier.height(2.dp))
}

@Composable
private fun TodoCard(todos: List<TodoEntity>) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(BgCard)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SectionLabel("할 일")
        if (todos.isEmpty()) {
            Text("오늘 할 일 없음", style = TextStyle(color = FgSecondary, fontSize = 10.sp))
        } else {
            todos.take(2).forEach { todo ->
                TodoRow(todo.title)
            }
            if (todos.size > 2) {
                Text("+${todos.size - 2}개 더", style = TextStyle(color = FgSecondary, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun TodoRow(title: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(6.dp)
                .height(6.dp)
                .background(AccentBlue)
                .cornerRadius(3.dp),
        ) {}
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(text = title, style = TextStyle(color = FgPrimary, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun EventCard(events: List<EventEntity>) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(BgCard)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SectionLabel("일정")
        if (events.isEmpty()) {
            Text("오늘 일정 없음", style = TextStyle(color = FgSecondary, fontSize = 10.sp))
        } else {
            events.take(2).forEach { event ->
                EventRow(event)
            }
            if (events.size > 2) {
                Text("+${events.size - 2}개 더", style = TextStyle(color = FgSecondary, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun EventRow(event: EventEntity) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(6.dp)
                .height(6.dp)
                .background(AccentGreen)
                .cornerRadius(3.dp),
        ) {}
        Spacer(modifier = GlanceModifier.width(5.dp))
        val timePrefix = if (!event.isAllDay) extractTimePrefix(event.startDate) else ""
        val displayText = if (timePrefix.isNotEmpty()) "$timePrefix ${event.title}" else event.title
        Text(text = displayText, style = TextStyle(color = FgPrimary, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun FinanceCard(monthExpense: Long, monthIncome: Long) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(BgCard)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SectionLabel("가계부")
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("지출", style = TextStyle(color = FgSecondary, fontSize = 9.sp))
                Text(
                    text = "${formatAmount(monthExpense)}원",
                    style = TextStyle(color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("수입", style = TextStyle(color = FgSecondary, fontSize = 9.sp))
                Text(
                    text = "${formatAmount(monthIncome)}원",
                    style = TextStyle(color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun ReadingCard(
    todayReadingPlan: List<ReadingPlanEntity>,
    readCount: Int,
    totalCount: Int,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(BgCard)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SectionLabel("통독")
        if (todayReadingPlan.isEmpty()) {
            Text("통독 계획 없음", style = TextStyle(color = FgSecondary, fontSize = 10.sp))
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$readCount/$totalCount 완료",
                    style = TextStyle(color = FgPrimary, fontSize = 11.sp),
                )
                val firstUnread = todayReadingPlan.firstOrNull { !it.isRead }
                if (firstUnread != null) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    val bookName = if (firstUnread.book in 1..66) BOOK_NAMES[firstUnread.book] else "?"
                    Text(
                        text = "$bookName ${firstUnread.chapter}장",
                        style = TextStyle(color = FgSecondary, fontSize = 9.sp),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            SegmentProgressBar(
                progress = if (totalCount > 0) readCount.toFloat() / totalCount else 0f,
            )
        }
    }
}

@Composable
private fun SegmentProgressBar(progress: Float) {
    val filled = (progress * 10).roundToInt().coerceIn(0, 10)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        repeat(10) { i ->
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .padding(horizontal = 1.dp)
                    .background(if (i < filled) AccentBlue else BgPrimary)
                    .cornerRadius(2.dp),
            ) {}
        }
    }
}
