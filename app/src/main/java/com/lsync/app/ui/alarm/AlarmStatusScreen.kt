package com.lsync.app.ui.alarm

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.alarm.GateVerdict
import com.lsync.app.data.local.entity.AlarmLogEntity
import com.lsync.app.ui.theme.AccentRed
import com.lsync.app.ui.theme.BgCard
import com.lsync.app.ui.theme.BgPrimary
import com.lsync.app.ui.theme.FgPrimary
import com.lsync.app.ui.theme.FgSecondary
import com.lsync.app.ui.theme.FgTertiary
import com.lsync.app.ui.theme.HairlineWhite
import com.lsync.app.ui.theme.Pretendard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREA)

@Composable
fun AlarmStatusScreen(
    onBack: () -> Unit,
    viewModel: AlarmStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgPrimary),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", tint = FgPrimary,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp).size(22.dp),
                )
                Text("알람 신뢰성", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, color = FgPrimary)
            }
        }
        item {
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { days ->
                    val selected = state.windowDays == days
                    Text(
                        "$days 일", fontFamily = Pretendard, fontSize = 13.sp,
                        color = if (selected) BgPrimary else FgSecondary,
                        modifier = Modifier
                            .background(if (selected) FgPrimary else BgCard, RoundedCornerShape(50))
                            .border(1.dp, HairlineWhite, RoundedCornerShape(50))
                            .clickable { viewModel.setWindow(days) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("새로고침", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary,
                    modifier = Modifier.clickable { viewModel.refresh() }.padding(7.dp))
            }
        }
        if (state.loading) {
            item { CircularProgressIndicator(modifier = Modifier.padding(20.dp).size(24.dp)) }
        } else if (state.error) {
            item { BodyText("알람 기록을 읽지 못했습니다. 새로고침을 눌러 다시 시도하세요.") }
        } else {
            item {
                Section("일정·할 일 · 최근 ${state.windowDays}일") {
                    val summary = state.summary
                    val verdict = when {
                        state.coverageIncomplete || state.coverageGaps > 0 || summary == null ||
                            summary.fired + summary.missed == 0 -> "판정 불가"
                        summary.verdict == GateVerdict.REFERENCE_PASS -> "참고치 · 5% 미만"
                        summary.verdict == GateVerdict.REFERENCE_EXCEEDED -> "참고치 · 5% 이상"
                        else -> "판정 불가"
                    }
                    Text(verdict, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp, color = if (summary?.verdict == GateVerdict.REFERENCE_EXCEEDED) AccentRed else FgPrimary)
                    Text("예상 ${state.expected} · 발화 ${summary?.fired ?: 0} · 누락 ${summary?.missed ?: 0} · 게시 차단 ${summary?.blocked ?: 0}",
                        fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary)
                    summary?.rate?.let {
                        Text("누락·차단율 ${String.format(Locale.KOREA, "%.1f", it * 100)}%  (누락 + 차단) / (발화 + 누락)",
                            fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary)
                    }
                    if (state.coverageIncomplete || state.coverageGaps > 0) {
                        Text("관측 공백 ${state.coverageGaps}건 · 총 ${state.coverageGapMillis / 60000}분 · 판정 불가",
                            fontFamily = Pretendard, fontSize = 12.sp, color = AccentRed)
                    } else if (summary?.fired == 0 && summary?.missed == 0) {
                        Text("판정할 발화·누락 기록이 없습니다.", fontFamily = Pretendard,
                            fontSize = 12.sp, color = FgSecondary)
                    }
                    Text("기기 실측 전 5% 기준은 참고용입니다. 발화는 수신기 도착 기준이며 화면 표시를 보증하지 않습니다.",
                        fontFamily = Pretendard, fontSize = 12.sp, color = FgTertiary)
                }
            }
            item {
                Section("지연 및 원인") {
                    Text("유예 시간 초과 발화 ${state.late}건 · 정확한 알람 15분 / 비정확한 알람 2시간",
                        fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary)
                    if (state.causes.isEmpty()) {
                        Text("기록된 누락·게시 실패 원인이 없습니다.", fontFamily = Pretendard,
                            fontSize = 12.sp, color = FgTertiary)
                    } else {
                        state.causes.forEach { (cause, count) ->
                            Text("${causeLabel(cause)} $count", fontFamily = Pretendard,
                                fontSize = 12.sp, color = FgSecondary)
                        }
                    }
                }
            }
            item {
                Section("정기 리마인더 · 참고 기록") {
                    Text("기대 ${state.reminderExpected} · 발화 ${state.reminderFired} · 누락 ${state.reminderMissed}",
                        fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary)
                    Text("게시 차단 ${state.reminderBlocked} · 보낼 내용 없음 ${state.reminderSkipped}",
                        fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary)
                    Text("통독·소비 요약은 일정·할 일의 5% 판정에 포함되지 않습니다.",
                        fontFamily = Pretendard, fontSize = 12.sp, color = FgTertiary)
                }
            }
            item { BodyText("최근 기록 · 선택한 기간 내 최대 50건") }
            if (state.recent.isEmpty()) {
                item { BodyText("알람 기록이 없습니다.") }
            } else {
                items(state.recent, key = { it.id }) { row -> RecentRow(row) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, color = FgTertiary)
        Column(
            modifier = Modifier.fillMaxWidth().background(BgCard, RoundedCornerShape(14.dp))
                .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun BodyText(value: String) {
    Text(value, modifier = Modifier.padding(horizontal = 20.dp), fontFamily = Pretendard,
        fontSize = 12.sp, color = FgTertiary)
}

@Composable
private fun RecentRow(row: AlarmLogEntity) {
    val kind = when {
        row.alarmKey.startsWith("event:") -> "일정"
        row.alarmKey.startsWith("todo:") -> "할 일"
        else -> "알람"
    }
    val status = when (row.status) {
        "FIRED" -> if (row.posted == false) "발화 · 게시 실패" else "발화"
        "MISSED" -> "누락"
        "PENDING" -> "대기"
        "SUPERSEDED" -> "대체됨"
        "CANCELLED" -> "취소됨"
        else -> row.status
    }
    val whenText = Instant.ofEpochMilli(row.expectedAt).atZone(ZoneId.systemDefault()).format(timeFormat)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$whenText · $kind", fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
        Text(status, fontFamily = Pretendard, fontSize = 12.sp,
            color = if (row.status == "MISSED" || row.posted == false) AccentRed else FgSecondary)
    }
}

private fun causeLabel(cause: String): String = when (cause) {
    "NOTIFICATIONS_OFF" -> "알림 권한 꺼짐"
    "CHANNEL_BLOCKED" -> "알림 채널 차단"
    "NOTIFY_FAILED" -> "게시 호출 실패"
    "NOT_REGISTERED" -> "등록되지 않음"
    "BEFORE_BOOT" -> "기록 시작 전"
    else -> cause
}
