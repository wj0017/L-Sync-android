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

    // 워커 계속-재시도 판정용. 원본이 없는 행은 네트워크 재시도로 해소되지 않는다.
    // 화면 건수와 재전송 대상 목록에는 SourceMissing을 계속 포함한다.
    @Query("SELECT COUNT(*) FROM sync_log WHERE userId = :userId AND success = 0 AND resolvedAt IS NULL AND (errorType IS NULL OR errorType != 'SourceMissing')")
    suspend fun getRetryableUnresolvedCount(userId: String): Int

    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("UPDATE sync_log SET resolvedAt = :now WHERE id = :id")
    suspend fun markResolved(id: Long, now: Long)

    // 원본이 사라졌다면 전송 성공으로 위장하지 않고 화면에 원인을 남긴다.
    @Query("UPDATE sync_log SET errorType = 'SourceMissing', errorMessage = :message WHERE id = :id AND resolvedAt IS NULL")
    suspend fun markSourceMissing(id: Long, message: String)

    // 원본 없음은 사용자에게 30일간 보여준 뒤 정리한다. 다른 미해결 실패는 보존한다.
    @Query("DELETE FROM sync_log WHERE createdAt < :cutoff AND (success = 1 OR resolvedAt IS NOT NULL OR errorType = 'SourceMissing')")
    suspend fun pruneOlderThan(cutoff: Long)
}
