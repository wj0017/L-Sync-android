package com.lsync.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.ui.theme.*

@Composable
fun TransactionFormSheet(
    formState: FormUiState,
    onTypeChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(18.dp),
        titleContentColor = FgPrimary,
        textContentColor = FgPrimary,
        title = {
            Text(
                text = if (formState.isEditing) "거래 수정" else "거래 추가",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = FgPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Type selector (Ghost chip pair)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EXPENSE" to "지출", "INCOME" to "수입").forEach { (value, label) ->
                        val selected = formState.type == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) FgPrimary else Color.Transparent)
                                .border(1.dp, if (selected) FgPrimary else Divider, CircleShape)
                                .clickable { onTypeChange(value) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontFamily = Pretendard,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selected) BgPrimary else FgSecondary,
                            )
                        }
                    }
                }

                // Amount input
                OutlinedTextField(
                    value = formState.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("금액", fontFamily = Pretendard, fontSize = 12.sp)
                    },
                    suffix = {
                        Text(
                            "원",
                            fontFamily = InstrumentSerif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = FgSecondary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )

                // Category selector (horizontal scroll chips)
                Column {
                    Text(
                        "카테고리",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FinanceCategory.all.forEach { cat ->
                            val selected = formState.category == cat
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) AccentBlue else Color.Transparent)
                                    .border(1.dp, if (selected) AccentBlue else Divider, CircleShape)
                                    .clickable { onCategoryChange(cat) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cat,
                                    fontFamily = Pretendard,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (selected) FgPrimary else FgSecondary,
                                )
                            }
                        }
                    }
                }

                // Date input (YYYY-MM-DD, no timezone conversion)
                OutlinedTextField(
                    value = formState.date,
                    onValueChange = onDateChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("날짜 (YYYY-MM-DD)", fontFamily = Pretendard, fontSize = 12.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )

                // Note input (optional)
                OutlinedTextField(
                    value = formState.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("메모 (선택)", fontFamily = Pretendard, fontSize = 12.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = FgPrimary,
                        unfocusedTextColor = FgPrimary,
                        cursorColor = AccentBlue,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = FgTertiary,
                    ),
                )

                // Error message
                if (formState.errorMessage != null) {
                    Text(
                        formState.errorMessage,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = AccentRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !formState.isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue),
            ) {
                Text(
                    if (formState.isSaving) "저장 중..." else "저장",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = FgSecondary),
            ) {
                Text(
                    "취소",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        },
    )
}
