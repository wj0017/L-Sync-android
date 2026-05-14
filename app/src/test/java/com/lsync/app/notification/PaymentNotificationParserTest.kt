package com.lsync.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {

    private val toss    = "viva.republica.toss"
    private val tossBank = "com.viva.finance"
    private val unknown  = "com.unknown.app"

    // ── 스크린샷 케이스 ───────────────────────────────────────────────────────
    @Test fun `토스뱅크 결제 - 잔액 아닌 결제금액 추출`() {
        val result = PaymentNotificationParser.parse(
            packageName = tossBank,
            title       = "1,417원 결제",
            text        = "토스뱅크 체크카드 | 토스페이_게임_TOSS\n잔액 1,865,204원",
        )!!
        assertEquals(1_417L,      result.amount)
        assertEquals("EXPENSE",   result.type)
        assertEquals("토스페이_게임_TOSS", result.note)
    }

    // ── 입금 감지 ─────────────────────────────────────────────────────────────
    @Test fun `토스뱅크 입금 - INCOME 분류`() {
        val result = PaymentNotificationParser.parse(
            packageName = tossBank,
            title       = "50,000원 입금",
            text        = "토스뱅크 | 홍길동\n잔액 1,915,204원",
        )!!
        assertEquals(50_000L,   result.amount)
        assertEquals("INCOME",  result.type)
    }

    // ── 지원하지 않는 앱 ──────────────────────────────────────────────────────
    @Test fun `미지원 패키지 - null 반환`() {
        val result = PaymentNotificationParser.parse(unknown, "1,000원 결제", "결제 완료")
        assertNull(result)
    }

    // ── 키워드 없는 알림 ──────────────────────────────────────────────────────
    @Test fun `결제 키워드 없는 알림 - null 반환`() {
        val result = PaymentNotificationParser.parse(toss, "오늘의 날씨", "맑음")
        assertNull(result)
    }

    // ── extractAmountFromTitle ────────────────────────────────────────────────
    @Test fun `제목에서 금액 추출 - 결제`() {
        assertEquals(1_417L, PaymentNotificationParser.extractAmountFromTitle("1,417원 결제"))
    }
    @Test fun `제목에서 금액 추출 - 승인`() {
        assertEquals(15_000L, PaymentNotificationParser.extractAmountFromTitle("15,000원 승인"))
    }
    @Test fun `제목에서 금액 추출 - 입금`() {
        assertEquals(50_000L, PaymentNotificationParser.extractAmountFromTitle("50,000원 입금"))
    }
    @Test fun `제목에 금액 없음 - null`() {
        assertNull(PaymentNotificationParser.extractAmountFromTitle("결제 완료"))
    }

    // ── 알림성 메시지 false positive 방지 ─────────────────────────────────────
    @Test fun `예상 환급액 안내 - null 반환`() {
        val result = PaymentNotificationParser.parse(
            packageName = toss,
            title       = "납세자 이우진님",
            text        = "저장된 예상 환급액 137,280원이 있어요.",
        )
        assertNull(result)
    }
    @Test fun `통신비 도착 안내 - null 반환`() {
        val result = PaymentNotificationParser.parse(
            packageName = toss,
            title       = "통신비 도착",
            text        = "KT 56,070원 지금 낼 수 있어요.",
        )
        assertNull(result)
    }
    @Test fun `출금 안내 - null 반환`() {
        val result = PaymentNotificationParser.parse(
            packageName = toss,
            title       = "출금 안내",
            text        = "내일은 쿠팡 이용료 나가는 날이에요.",
        )
        assertNull(result)
    }

    // ── extractMerchant ───────────────────────────────────────────────────────
    @Test fun `파이프 뒤 가맹점명 추출`() {
        assertEquals(
            "토스페이_게임_TOSS",
            PaymentNotificationParser.extractMerchant("토스뱅크 체크카드 | 토스페이_게임_TOSS\n잔액 1,865,204원"),
        )
    }
    @Test fun `파이프 없으면 첫 번째 비잔액 줄 반환`() {
        assertEquals(
            "스타벅스 강남점",
            PaymentNotificationParser.extractMerchant("스타벅스 강남점\n잔액 200,000원"),
        )
    }

    // ── 카테고리 추론 ─────────────────────────────────────────────────────────
    @Test fun `카페 카테고리`() {
        assertEquals("카페/간식", PaymentNotificationParser.inferCategory("스타벅스 강남역"))
    }
    @Test fun `구독 카테고리 - 게임`() {
        assertEquals("구독", PaymentNotificationParser.inferCategory("토스페이_게임_TOSS"))
    }
    @Test fun `교통 카테고리`() {
        assertEquals("교통", PaymentNotificationParser.inferCategory("카카오T 택시"))
    }
}
