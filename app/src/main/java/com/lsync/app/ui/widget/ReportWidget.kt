package com.lsync.app.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lsync.app.data.recurrence.expandEvents
import com.lsync.app.data.report.financeTotals
import dagger.hilt.android.EntryPointAccessors
import java.time.YearMonth

// LSyncWidget.kt의 색 상수는 private이라 import할 수 없다. 검증된 파일을 건드리지 않기 위해
// 같은 값을 여기 다시 선언한다(위젯은 Glance/RemoteViews라 앱 테마를 쓸 수 없음 — UI_GUIDE).
private val BgPrimary    = ColorProvider(Color(0xFF0A0A0A))
private val BgCard       = ColorProvider(Color(0xFF161616))
private val BgCardBorder = ColorProvider(Color(0xFF383838))
private val FgPrimary    = ColorProvider(Color(0xFFF5F5F5))
private val FgSecondary  = ColorProvider(Color(0xCCF5F5F5))
private val FgTertiary   = ColorProvider(Color(0x99F5F5F5))
private val AccentBlue   = ColorProvider(Color(0xFF4F7EFF))

// LSyncWidget.kt의 navIntent와 동일한 형태(private top-level이라 import 불가 — 검증된 파일 무수정).
// target별 고유 data Uri로 PendingIntent filterEquals 충돌 방지.
private fun navIntent(context: Context, target: String) = Intent(context, com.lsync.app.MainActivity::class.java).apply {
    putExtra(com.lsync.app.MainActivity.EXTRA_NAV_TARGET, target)
    data = Uri.parse("lsync://nav/$target")
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

private fun formatAmount(amount: Long): String = String.format("%,d", amount)

// 이번 달 월간 리포트 요약 위젯(4×2). 표시 전용 — 데이터는 Room DAO만 사용(Offline-First).
class ReportWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent { ReportWidgetContent(state) }
    }

    private suspend fun loadState(context: Context): ReportWidgetState {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val userId = entryPoint.authRepository().currentUserId
            ?: return ReportWidgetState.empty()

        // Floating Date(YYYY-MM-DD) 문자열 범위 — 타임존 변환 없음.
        // 건수를 세므로 말일은 lengthOfMonth()로 정확히 잡는다.
        val yearMonth = YearMonth.now()
        val from = "%04d-%02d-01".format(yearMonth.year, yearMonth.monthValue)
        val to = "%04d-%02d-%02d".format(yearMonth.year, yearMonth.monthValue, yearMonth.lengthOfMonth())

        val todos = entryPoint.todoDao().getByDueDateRange(from, to)

        // 과거 시작 반복 마스터까지 포함 조회 → 월 범위 발생 전개(행 수가 아닌 발생 수, PRD 2.5)
        val eventMasters = entryPoint.eventDao().getForExpansion(from, to)
        val eventCount = expandEvents(eventMasters, from, to).size

        // 정산 규칙(PRD 2.4) 집계는 MonthlyAggregate 단일 출처를 그대로 사용한다.
        val financeItems = entryPoint.financeDao().getAllByDateRange(userId, from, to)
        val totals = financeTotals(financeItems)

        val readEntries = entryPoint.readingPlanDao().getReadInRange(from, to)

        return ReportWidgetState(
            yearMonth = "%04d.%02d".format(yearMonth.year, yearMonth.monthValue),
            todoTotal = todos.size,
            todoCompleted = todos.count { it.isCompleted },
            eventCount = eventCount,
            expense = totals.expense,
            chaptersRead = readEntries.size,
            daysRead = readEntries.map { it.date }.distinct().size,
        )
    }
}

@Composable
fun ReportWidgetContent(state: ReportWidgetState) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().background(BgPrimary).padding(12.dp)
            // 리포트는 하단 4-tab이 아닌 별도 화면 — NavGraph가 "report" 타깃을 분기해 navigate한다.
            .clickable(actionStartActivity(navIntent(context, "report"))),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            ReportHeader(state.yearMonth)
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                val rate = if (state.todoTotal == 0) 0 else state.todoCompleted * 100 / state.todoTotal
                MetricCard(
                    label = "할 일",
                    value = "$rate%",
                    caption = "${state.todoCompleted}/${state.todoTotal} 완료",
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                MetricCard(
                    label = "일정",
                    value = "${state.eventCount}",
                    caption = "이번 달 발생",
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                // 지출에 AccentRed 미사용(체감 부담 완화 = 디자인 의도, UI_GUIDE)
                MetricCard(
                    label = "순지출",
                    value = "${formatAmount(state.expense)}원",
                    caption = "정산 받음 차감",
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                MetricCard(
                    label = "통독",
                    value = "${state.chaptersRead}장",
                    caption = "${state.daysRead}일 읽음",
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(yearMonth: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "REPORT",
            style = TextStyle(color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (yearMonth.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = yearMonth,
                style = TextStyle(color = FgSecondary, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    caption: String,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().background(BgCardBorder).cornerRadius(12.dp).padding(1.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight().background(BgCard).cornerRadius(11.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Text(
                text = label,
                style = TextStyle(color = AccentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = value,
                style = TextStyle(color = FgPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = caption,
                style = TextStyle(color = FgTertiary, fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}
