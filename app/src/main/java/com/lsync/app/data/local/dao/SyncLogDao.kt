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

    // 게이트 분모 — since 이후 사용자 작업(push) 시도 수.
    // 자동 pull은 앱 시작·로그인마다 엔티티 그룹 4건씩 쌓여 분모를 부풀리므로 제외한다.
    // pullTarget은 호출부가 SyncTarget.PULL.key를 넘긴다(문자열 사본 방지).
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since AND target != :pullTarget")
    fun observePushAttemptsSince(userId: String, since: Long, pullTarget: String): Flow<Int>

    // 게이트 분자 — since 이후 사용자 작업(push) 실패 수
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since AND target != :pullTarget AND success = 0")
    fun observePushFailuresSince(userId: String, since: Long, pullTarget: String): Flow<Int>

    // 자동 복원(pull) — 게이트와 분리해 참고 수치로만 보여준다
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since AND target = :pullTarget")
    fun observePullAttemptsSince(userId: String, since: Long, pullTarget: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND createdAt >= :since AND target = :pullTarget AND success = 0")
    fun observePullFailuresSince(userId: String, since: Long, pullTarget: String): Flow<Int>

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
