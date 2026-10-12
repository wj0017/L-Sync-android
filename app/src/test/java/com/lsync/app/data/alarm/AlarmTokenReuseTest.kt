package com.lsync.app.data.alarm

import com.lsync.app.data.local.entity.AlarmDeliveryTokenEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTokenReuseTest {
    private fun token(id: String = "t", at: Long = 1_000, state: String = "ARMED", delivered: Long? = null) =
        AlarmDeliveryTokenEntity(id, "event:e", at, state = state, createdAt = 0, deliveredAt = delivered)

    private fun reuse(active: List<AlarmDeliveryTokenEntity>, at: Long = 1_000, now: Long = 500,
                      exactNow: Boolean = true, registeredExact: Boolean? = true) =
        AlarmRegistrar.reusableToken(active, at, now, exactNow, registeredExact)

    @Test fun sameTimeAndModeKeepsTheArmedToken() {
        val armed = token()
        assertEquals(armed, reuse(listOf(armed)))
    }

    @Test fun differentTimeReArms() = assertNull(reuse(listOf(token(at = 2_000))))

    @Test fun exactPermissionChangeReArms() {
        assertNull(reuse(listOf(token()), exactNow = true, registeredExact = false))
        assertNull(reuse(listOf(token()), exactNow = false, registeredExact = true))
        assertNull(reuse(listOf(token()), registeredExact = null))
    }

    @Test fun preparedOrDeliveredTokenReArms() {
        assertNull(reuse(listOf(token(state = "PREPARED"))))
        assertNull(reuse(listOf(token(delivered = 400))))
    }

    @Test fun pastTriggerReArms() = assertNull(reuse(listOf(token()), now = 1_000))

    // Two live tokens mean an interrupted replace; re-arm so the older one is retired.
    @Test fun multipleActiveTokensReArm() = assertNull(reuse(listOf(token("a"), token("b"))))

    @Test fun noActiveTokenArms() = assertNull(reuse(emptyList()))
}
