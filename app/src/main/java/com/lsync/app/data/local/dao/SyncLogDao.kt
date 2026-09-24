package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    // 최근 로그 (화면 목록용) — createdAt 내림차순
    @Query("SELECT * FROM sync_log WHERE userId = :userId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(userId: String, limit: Int): Flow<List<SyncLogEntity>>

    // 미해결 실패 건수
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND success = 0 AND resolvedAt IS NULL")
    fun observeUnresolvedCount(userId: String): Flow<Int>

    // 에러율 분모 — since 이후 시도 수
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since")
    fun observeAttemptsSince(userId: String, since: Long): Flow<Int>

    // 에러율 분자 — since 이후 실패 수
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since AND success = 0")
    fun observeFailuresSince(userId: String, since: Long): Flow<Int>

    // 재시도 대상 (오래된 것부터)
    @Query("SELECT * FROM sync_log WHERE userId = :userId AND success = 0 AND resolvedAt IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getUnresolvedFailures(userId: String): List<SyncLogEntity>

    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("UPDATE sync_log SET resolvedAt = :now WHERE id = :id")
    suspend fun markResolved(id: Long, now: Long)

    // 보존 기간 초과 행 삭제
    @Query("DELETE FROM sync_log WHERE createdAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
