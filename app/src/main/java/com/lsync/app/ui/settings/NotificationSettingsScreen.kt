package com.lsync.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.ui.PermissionHelper
import com.lsync.app.ui.theme.*

// 요일 라벨 — index 0 = 월요일(java.time.DayOfWeek.value 1)
private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 어떤 시각 선택 다이얼로그를 띄울지. null이면 닫힘.
    var editingTime by remember { mutableStateOf<TimeTarget?>(null) }

    val needsNotification = PermissionHelper.needsNotificationPermission(context)
    val needsExactAlarm = PermissionHelper.needsExactAlarmPermission(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "topbar") { SettingsTopBar(onBack = onBack) }

        // 미허용 권한이 있을 때만 노출 — 모두 허용이면 화면이 깔끔하게 유지된다(홈 메뉴와 같은 방식).
        if (needsNotification) {
            item(key = "perm_notification") {
                PermissionNotice(
                    text = "알림 권한이 꺼져 있어 리마인더가 표시되지 않습니다.",
                    actionLabel = "알림 권한 열기",
                    onClick = { PermissionHelper.openAppNotificationSettings(context) },
                )
            }
        }
        if (needsExactAlarm) {
            item(key = "perm_exact") {
                PermissionNotice(
                    text = "정확한 알람 권한이 없으면 지정한 시각보다 늦게 올 수 있습니다.",
                    actionLabel = "정확한 알람 설정 열기",
                    onClick = { PermissionHelper.openExactAlarmSettings(context) },
                )
            }
        }

        // ── 성경 통독 ──────────────────────────────────────────────────────
        item(key = "bible") {
            Column {
                SectionHeader("성경 통독")
                SettingsCard {
                    ToggleRow(
                        label = "통독 리마인더",
                        description = "오늘 분량을 다 읽은 날에는 보내지 않아요",
                        checked = state.bibleEnabled,
                        onCheckedChange = viewModel::setBibleEnabled,
                    )
                    if (state.bibleEnabled) {
                        RowDivider()
                        TimeRow(
                            label = "알림 시각",
                            hour = state.bibleHour,
                            minute = state.bibleMinute,
                            onClick = { editingTime = TimeTarget.BIBLE },
                        )
                    }
                }
            }
        }

        // ── 소비 요약 ──────────────────────────────────────────────────────
        item(key = "spending") {
            Column {
                SectionHeader("소비 요약")
                SettingsCard {
                    ToggleRow(
                        label = "매일 요약",
                        description = "오늘 지출과 이번 달 누적·예산 대비",
                        checked = state.dailySpendingEnabled,
                        onCheckedChange = viewModel::setDailySpendingEnabled,
                    )
                    if (state.dailySpendingEnabled) {
                        RowDivider()
                        TimeRow(
                            label = "알림 시각",
                            hour = state.dailySpendingHour,
                            minute = state.dailySpendingMinute,
                            onClick = { editingTime = TimeTarget.DAILY_SPENDING },
                        )
                    }

                    RowDivider()

                    ToggleRow(
                        label = "주간 요약",
                        description = "최근 7일 지출과 직전 7일 대비 증감",
                        checked = state.weeklySpendingEnabled,
                        onCheckedChange = viewModel::setWeeklySpendingEnabled,
                    )
                    if (state.weeklySpendingEnabled) {
                        RowDivider()
                        WeekdayRow(
                            selected = state.weeklySpendingDayOfWeek,
                            onSelect = viewModel::setWeeklySpendingDay,
                        )
                        RowDivider()
                        TimeRow(
                            label = "알림 시각",
                            hour = state.weeklySpendingHour,
                            minute = state.weeklySpendingMinute,
                            onClick = { editingTime = TimeTarget.WEEKLY_SPENDING },
                        )
                    }
                }
            }
        }

        // ── 미리보기 ───────────────────────────────────────────────────────
        item(key = "preview") {
            val anyEnabled = state.bibleEnabled || state.dailySpendingEnabled || state.weeklySpendingEnabled
            Column {
                SectionHeader("확인")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (anyEnabled) Modifier.clickable { viewModel.sendPreview() }
                                else Modifier,
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "지금 미리보기",
                                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (anyEnabled) AccentBlue else FgDisabled,
                            )
                            Text(
                                if (anyEnabled) "켜 둔 알림을 지금 한 번 보내 봅니다"
                                else "알림을 하나 이상 켜면 사용할 수 있어요",
                                fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editingTime?.let { target ->
        val (hour, minute) = when (target) {
            TimeTarget.BIBLE -> state.bibleHour to state.bibleMinute
            TimeTarget.DAILY_SPENDING -> state.dailySpendingHour to state.dailySpendingMinute
            TimeTarget.WEEKLY_SPENDING -> state.weeklySpendingHour to state.weeklySpendingMinute
        }
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { editingTime = null },
            onConfirm = { h, m ->
                when (target) {
                    TimeTarget.BIBLE -> viewModel.setBibleTime(h, m)
                    TimeTarget.DAILY_SPENDING -> viewModel.setDailySpendingTime(h, m)
                    TimeTarget.WEEKLY_SPENDING -> viewModel.setWeeklySpendingTime(h, m)
                }
                editingTime = null
            },
        )
    }
}

private enum class TimeTarget { BIBLE, DAILY_SPENDING, WEEKLY_SPENDING }

// ── 상단 바 ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
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
            text = "알림 설정",
            fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, letterSpacing = (-0.02).em, color = FgPrimary,
        )
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
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(Divider),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = FgPrimary,
            )
            Text(
                description,
                fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FgPrimary,
                checkedTrackColor = AccentBlue,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = FgSecondary,
                uncheckedTrackColor = BgElevated,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun TimeRow(label: String, hour: Int, minute: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = Pretendard, fontSize = 14.sp, color = FgSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatTime(hour, minute),
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, color = FgPrimary,
        )
    }
}

// Ghost chip 패턴 — outline only, active = FgPrimary solid
@Composable
private fun WeekdayRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "요일",
            fontFamily = Pretendard, fontSize = 14.sp, color = FgSecondary,
            modifier = Modifier.weight(1f),
        )
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val value = index + 1 // DayOfWeek.value — 1=월 … 7=일
            val active = value == selected
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (active) Modifier.background(FgPrimary)
                        else Modifier.border(1.dp, FgDisabled, CircleShape),
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (active) BgPrimary else FgSecondary,
                )
            }
        }
    }
}

@Composable
private fun PermissionNotice(text: String, actionLabel: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(text, fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
        Text(
            actionLabel,
            fontFamily = Pretendard, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, color = AccentBlue,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ── 시각 선택 다이얼로그 ────────────────────────────────────────────────────
//
// material3 1.2.x에는 TimePickerDialog 컴포저블이 없어 직접 구성한다.
// AlertDialog는 최대 폭 제약으로 다이얼(약 360dp)이 잘리므로 Dialog + Surface를 쓴다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = BgElevated,
                        clockDialSelectedContentColor = BgPrimary,
                        clockDialUnselectedContentColor = FgPrimary,
                        selectorColor = AccentBlue,
                        containerColor = BgCard,
                        timeSelectorSelectedContainerColor = AccentBlue,
                        timeSelectorSelectedContentColor = FgPrimary,
                        timeSelectorUnselectedContainerColor = BgElevated,
                        timeSelectorUnselectedContentColor = FgPrimary,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "취소",
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, color = FgSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        "확인",
                        fontFamily = Pretendard, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, color = AccentBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onConfirm(timeState.hour, timeState.minute) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
