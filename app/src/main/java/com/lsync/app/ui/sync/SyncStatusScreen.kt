package com.lsync.app.ui.sync

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.SyncLogEntity
import com.lsync.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WINDOW_OPTIONS = listOf(7, 30)
private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREA)

@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "topbar") { SyncTopBar(onBack = onBack) }

        item(key = "window") {
            WindowChips(selected = state.windowDays, onSelect = viewModel::setWindow)
        }

        // ── 게이트 판정 ────────────────────────────────────────────────────
        item(key = "gate") {
            Column {
                SectionHeader("에러율 · 최근 ${state.windowDays}일")
                GateCard(state)
            }
        }

        // ── 미해결 실패 ────────────────────────────────────────────────────
        item(key = "unresolved") {
            Column {
                SectionHeader("미해결 실패")
                UnresolvedCard(
                    unresolved = state.unresolved,
                    isRetrying = state.isRetrying,
                    onRetry = viewModel::retryNow,
                )
            }
        }

        // ── 최근 로그 ──────────────────────────────────────────────────────
        item(key = "logs_header") { SectionHeader("최근 기록") }
        if (state.logs.isEmpty()) {
            item(key = "logs_empty") {
                Text(
                    "아직 동기화 기록이 없어요",
                    fontFamily = Pretendard, fontSize = 13.sp, color = FgTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.logs, key = { it.id }) { log -> LogRow(log) }
        }
    }
}

// ── 상단 바 ────────────────────────────────────────────────────────────────

@Composable
private fun SyncTopBar(onBack: () -> Unit) {
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
            text = "동기화 상태",
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, letterSpacing = (-0.02).em, color = FgPrimary,
        )
    }
}

// Ghost chip 패턴 — outline only, active = FgPrimary solid
@Composable
private fun WindowChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WINDOW_OPTIONS.forEach { days ->
            val active = days == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (active) Modifier.background(FgPrimary)
                        else Modifier.border(1.dp, FgDisabled, RoundedCornerShape(50)),
                    )
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${days}일",
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (active) BgPrimary else FgSecondary,
                )
            }
        }
    }
}

// ── 게이트 카드 ────────────────────────────────────────────────────────────

@Composable
private fun GateCard(state: SyncStatusUiState) {
    StatusCard {
        if (!state.hasData) {
            Text(
                "기록 없음",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = FgPrimary,
            )
            Text(
                "이 기간에 동기화 시도가 없어 판정할 수 없어요",
                fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            return@StatusCard
        }

        // 게이트를 넘겼을 때만 경고색 — 정상 상태엔 빨강을 쓰지 않는다(예산 진행바와 동일).
        val rateColor = if (state.passesGate) FgPrimary else AccentRed
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(Locale.US, "%.1f", state.errorRate * 100),
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp, letterSpacing = (-0.035).em, color = rateColor,
            )
            Text(
                "%",
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 16.sp, color = rateColor,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp),
            )
        }
        Text(
            "실패 ${state.failures} / 시도 ${state.attempts}",
            fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            if (state.passesGate) "게이트 통과 · 기준 5% 미만" else "게이트 초과 · 기준 5% 미만",
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = if (state.passesGate) AccentGreen else AccentRed,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ── 미해결 실패 카드 ───────────────────────────────────────────────────────

@Composable
private fun UnresolvedCard(unresolved: Int, isRetrying: Boolean, onRetry: () -> Unit) {
    StatusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${unresolved}건",
                    fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp, color = FgPrimary,
                )
                Text(
                    if (unresolved == 0) "모두 전송됐어요" else "네트워크가 연결되면 자동으로 다시 보내요",
                    fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (unresolved > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, HairlineWhite, RoundedCornerShape(50))
                        .background(BgElevated)
                        // 진행 중엔 클릭을 막아 중복 실행 방지
                        .clickable(enabled = !isRetrying, onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            color = AccentBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "지금 재시도",
                            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                            fontSize = 13.sp, color = AccentBlue,
                        )
                    }
                }
            }
        }
    }
}

// ── 로그 행 ────────────────────────────────────────────────────────────────

@Composable
private fun LogRow(log: SyncLogEntity) {
    val time = Instant.ofEpochMilli(log.createdAt).atZone(ZoneId.systemDefault()).format(LOG_TIME_FORMAT)
    val resolved = log.resolvedAt != null
    // 경고색은 미해결 실패에만. 해소된 실패는 흐린 색으로 구분한다.
    val (statusLabel, statusColor) = when {
        log.success -> "성공" to FgSecondary
        resolved -> "해소됨" to FgTertiary
        else -> "실패" to AccentRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$time · ${log.target}",
                fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                statusLabel,
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 12.sp, color = statusColor,
            )
        }
        if (!log.success) {
            val detail = listOfNotNull(log.errorType, log.errorMessage).joinToString(": ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    fontFamily = Pretendard, fontSize = 12.sp,
                    color = if (resolved) FgTertiary else FgSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ── 공통 조각 ──────────────────────────────────────────────────────────────

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
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}
