package com.lsync.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryWriteDecisionTest {
    @Test fun `원격 문서가 없거나 오래됐으면 현재 Room 행을 쓴다`() {
        assertTrue(shouldWriteRetry(200, null))
        assertTrue(shouldWriteRetry(200, 150))
        assertTrue(shouldWriteRetry(200, 200))
    }

    @Test fun `원격 문서가 최신이면 재전송을 건너뛴다`() {
        assertFalse(shouldWriteRetry(100, 300))
        // Todo와 Finance의 판정은 서로 독립적이다.
        assertTrue(shouldWriteRetry(200, 150))
        assertFalse(shouldWriteRetry(100, 300))
    }
}
