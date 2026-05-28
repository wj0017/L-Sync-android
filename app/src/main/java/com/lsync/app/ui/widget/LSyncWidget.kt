package com.lsync.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

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
        val todoDao = entryPoint.todoDao()
        val eventDao = entryPoint.eventDao()
        val financeDao = entryPoint.financeDao()
        val readingPlanDao = entryPoint.readingPlanDao()

        val today = LocalDate.now().toString()
        val monthStart = today.substring(0, 7) + "-01"
        val monthEnd = today.substring(0, 7) + "-31"

        val todos = todoDao.getIncompleteByDate(today)
        val events = eventDao.getByDate(today)

        val financeItems = financeDao.getAllByDateRange("local_user", monthStart, monthEnd)
        val monthExpense = financeItems
            .filter { it.type == "EXPENSE" && it.settlementGroupId == null }
            .sumOf { it.amount }
        val monthIncome = financeItems
            .filter { it.type == "INCOME" && it.settlementGroupId == null }
            .sumOf { it.amount }

        val todayReadingPlan = readingPlanDao.getForDate(today)
        val readCount = todayReadingPlan.count { it.isRead }
        val totalCount = todayReadingPlan.size

        return WidgetState(
            todos = todos,
            events = events,
            monthExpense = monthExpense,
            monthIncome = monthIncome,
            todayReadingPlan = todayReadingPlan,
            readCount = readCount,
            totalCount = totalCount,
        )
    }
}

@Composable
fun WidgetContent(state: WidgetState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            SectionHeader("할 일")
            TodoSection(state.todos)
            Spacer(modifier = GlanceModifier.height(4.dp))
            SectionHeader("일정")
            EventSection(state.events)
            Spacer(modifier = GlanceModifier.height(4.dp))
            SectionHeader("가계부")
            FinanceSection(state.monthExpense, state.monthIncome)
            Spacer(modifier = GlanceModifier.height(4.dp))
            SectionHeader("통독")
            ReadingSection(state.todayReadingPlan, state.readCount, state.totalCount)
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = TextStyle(color = AccentBlue, fontSize = 10.sp)
    )
}

@Composable
private fun TodoSection(todos: List<TodoEntity>) {
    if (todos.isEmpty()) {
        Text(
            text = "오늘 할 일 없음",
            style = TextStyle(color = FgSecondary, fontSize = 11.sp)
        )
        return
    }
    Column {
        todos.take(3).forEach { todo ->
            Text(
                text = "• ${todo.title}",
                style = TextStyle(color = FgPrimary, fontSize = 11.sp),
                maxLines = 1
            )
        }
        if (todos.size > 3) {
            Text(
                text = "+${todos.size - 3}개 더",
                style = TextStyle(color = FgSecondary, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun EventSection(events: List<EventEntity>) {
    if (events.isEmpty()) {
        Text(
            text = "오늘 일정 없음",
            style = TextStyle(color = FgSecondary, fontSize = 11.sp)
        )
        return
    }
    Column {
        events.take(3).forEach { event ->
            val displayTitle = if (!event.isAllDay) {
                try {
                    val tIndex = event.startDate.indexOf('T')
                    if (tIndex >= 0 && event.startDate.length >= tIndex + 6) {
                        "${event.startDate.substring(tIndex + 1, tIndex + 6)} ${event.title}"
                    } else {
                        event.title
                    }
                } catch (e: Exception) {
                    event.title
                }
            } else {
                event.title
            }
            Text(
                text = "• $displayTitle",
                style = TextStyle(color = FgPrimary, fontSize = 11.sp),
                maxLines = 1
            )
        }
        if (events.size > 3) {
            Text(
                text = "+${events.size - 3}개 더",
                style = TextStyle(color = FgSecondary, fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun FinanceSection(monthExpense: Long, monthIncome: Long) {
    Row {
        Text(
            text = "지출 ${formatAmount(monthExpense)}원",
            modifier = GlanceModifier.padding(end = 8.dp),
            style = TextStyle(color = AccentRed, fontSize = 11.sp)
        )
        Text(
            text = "수입 ${formatAmount(monthIncome)}원",
            style = TextStyle(color = AccentGreen, fontSize = 11.sp)
        )
    }
}

@Composable
private fun ReadingSection(
    todayReadingPlan: List<ReadingPlanEntity>,
    readCount: Int,
    totalCount: Int
) {
    if (todayReadingPlan.isEmpty()) {
        Text(
            text = "통독 계획 없음",
            style = TextStyle(color = FgSecondary, fontSize = 11.sp)
        )
        return
    }
    Column {
        Text(
            text = "${readCount}/${totalCount} 완료",
            style = TextStyle(color = FgPrimary, fontSize = 11.sp)
        )
        todayReadingPlan.filter { !it.isRead }.take(2).forEach { plan ->
            val bookName = if (plan.book in 1..66) BOOK_NAMES[plan.book] else "?"
            Text(
                text = "• $bookName ${plan.chapter}장",
                style = TextStyle(color = FgSecondary, fontSize = 10.sp)
            )
        }
    }
}
