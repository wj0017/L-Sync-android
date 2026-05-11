package com.lsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lsync.app.ui.PermissionHelper
import com.lsync.app.ui.navigation.NavGraph
import com.lsync.app.ui.theme.AccentBlue
import com.lsync.app.ui.theme.BgCard
import com.lsync.app.ui.theme.FgPrimary
import com.lsync.app.ui.theme.LSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LSyncTheme {
                var showExactAlarmDialog by remember { mutableStateOf(false) }
                var showNotificationListenerDialog by remember { mutableStateOf(false) }

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* 거부해도 앱 동작에 영향 없음 — 알람 기능만 비활성화 */ }

                LaunchedEffect(Unit) {
                    if (PermissionHelper.needsNotificationPermission(this@MainActivity)) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (PermissionHelper.needsExactAlarmPermission(this@MainActivity)) {
                        showExactAlarmDialog = true
                    }
                    if (PermissionHelper.needsNotificationListenerPermission(this@MainActivity)) {
                        showNotificationListenerDialog = true
                    }
                }

                if (showExactAlarmDialog) {
                    AlertDialog(
                        onDismissRequest = { showExactAlarmDialog = false },
                        containerColor = BgCard,
                        title = {
                            Text(text = "정확한 알람 권한 필요", color = FgPrimary)
                        },
                        text = {
                            Text(
                                text = "마감 알림을 정확한 시간에 받으려면 시스템 설정에서 권한을 허용해주세요.",
                                color = FgPrimary
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showExactAlarmDialog = false
                                PermissionHelper.openExactAlarmSettings(this@MainActivity)
                            }) {
                                Text(text = "설정으로 이동", color = AccentBlue)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExactAlarmDialog = false }) {
                                Text(text = "나중에", color = FgPrimary)
                            }
                        }
                    )
                }

                if (showNotificationListenerDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotificationListenerDialog = false },
                        containerColor = BgCard,
                        title = {
                            Text("결제 알림 자동 등록", color = FgPrimary)
                        },
                        text = {
                            Text(
                                "카드·뱅킹 앱 결제 알림을 읽어 가계부에 자동으로 추가합니다.\n설정 > 알림 접근에서 L-Sync를 허용해주세요.",
                                color = FgPrimary,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationListenerDialog = false
                                PermissionHelper.openNotificationListenerSettings(this@MainActivity)
                            }) {
                                Text("설정으로 이동", color = AccentBlue)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationListenerDialog = false }) {
                                Text("나중에", color = FgPrimary)
                            }
                        },
                    )
                }

                NavGraph()
            }
        }
    }
}
