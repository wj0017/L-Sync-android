package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 원격 동기화 시도 1건의 기록. 로컬 전용 진단 데이터 — Firestore에 동기화하지 않는다.
@Entity(
    tableName = "sync_log",
    indices = [Index(value = ["userId", "createdAt"])],
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val target: String,        // "events" / "todos" / "finance" / "budgets" / "pull"
    val entityId: String?,     // 재전송 키. target이 "pull"이면 null
    val success: Boolean,
    val errorType: String?,    // 예외 클래스 simpleName
    val errorMessage: String?, // 예외 message 앞 200자
    val createdAt: Long,
    val resolvedAt: Long?,     // 재시도로 해소된 시각. null + success=false = 미해결 실패
)
