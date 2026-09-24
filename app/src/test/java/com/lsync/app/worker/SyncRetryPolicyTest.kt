package com.lsync.app.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRetryPolicyTest {
    @Test fun `새로 남은 실패는 한도 전까지 재시도한다`() {
        assertTrue(shouldRetrySync(0, true))
        assertTrue(shouldRetrySync(3, true))
        assertFalse(shouldRetrySync(4, true))
    }

    @Test fun `미해결 실패가 없으면 추가 워커가 필요 없다`() {
        assertFalse(shouldRetrySync(0, false))
    }
}
