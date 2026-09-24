package com.lsync.app.data.remote

// 재전송은 현재 Room 행이 원격보다 뒤처지지 않을 때만 쓴다.
// null은 원격 문서가 없거나 updatedAt이 없는 기존 문서다.
internal fun shouldWriteRetry(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean =
    remoteUpdatedAt == null || remoteUpdatedAt <= localUpdatedAt
