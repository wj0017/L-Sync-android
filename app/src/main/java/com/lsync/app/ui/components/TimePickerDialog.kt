package com.lsync.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lsync.app.ui.theme.*

// ── 시각 선택 다이얼로그 ────────────────────────────────────────────────────
//
// material3 1.2.x에는 TimePickerDialog 컴포저블이 없어 직접 구성한다.
// AlertDialog는 최대 폭 제약으로 다이얼(약 360dp)이 잘리므로 Dialog + Surface를 쓴다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
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
