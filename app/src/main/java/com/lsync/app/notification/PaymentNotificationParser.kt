package com.lsync.app.notification

import com.lsync.app.data.local.FinanceCategory

data class ParsedPayment(
    val amount: Long,
    val note: String,
    val category: String,
)

object PaymentNotificationParser {

    val PAYMENT_PACKAGES: Set<String> = setOf(
        "viva.republica.toss",
        "com.viva.finance",
        "com.shinhan.smartsalary",
        "com.kbcard.kbcardclient",
        "com.kakaobank.channel",
    )

    private val AMOUNT_REGEX = Regex("""(\d{1,3}(?:,\d{3})*)원""")
    private val TITLE_PAYMENT_REGEX = Regex("""(\d{1,3}(?:,\d{3})*)원\s*(?:결제|승인|출금)""")
    private val PIPE_MERCHANT_REGEX = Regex("""\|\s*(.+)""")

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        if (packageName !in PAYMENT_PACKAGES) return null

        val combined = "$title $text"
        if (listOf("결제", "승인", "출금").none { it in combined }) return null

        // 제목에서 결제금액 우선 추출 — 잔액과 혼동 방지
        val amount = extractAmountFromTitle(title)
            ?: extractAmountExcludingBalance(text)
            ?: return null

        val merchant = extractMerchant(text)
        val category = inferCategory(if (merchant.isNotBlank()) merchant else text)
        val note = merchant.ifBlank {
            text.lines().firstOrNull { "잔액" !in it && it.isNotBlank() }?.trim() ?: title
        }.take(100)

        return ParsedPayment(amount, note, category)
    }

    // "1,417원 결제" / "15,000원 승인" 패턴 — 제목에서만 쓰는 추출
    internal fun extractAmountFromTitle(title: String): Long? {
        val match = TITLE_PAYMENT_REGEX.find(title) ?: return null
        return runCatching { match.groupValues[1].replace(",", "").toLong() }.getOrNull()
    }

    // 잔액 줄을 건너뛰고 첫 번째 금액 추출
    internal fun extractAmountExcludingBalance(text: String): Long? {
        for (line in text.lines()) {
            if ("잔액" in line) continue
            val match = AMOUNT_REGEX.find(line) ?: continue
            return runCatching { match.groupValues[1].replace(",", "").toLong() }.getOrNull()
        }
        return null
    }

    // "토스뱅크 체크카드 | 토스페이_게임_TOSS" → "토스페이_게임_TOSS"
    internal fun extractMerchant(text: String): String {
        PIPE_MERCHANT_REGEX.find(text)?.let { return it.groupValues[1].trim() }
        return text.lines().firstOrNull { "잔액" !in it && it.isNotBlank() }?.trim() ?: ""
    }

    internal fun inferCategory(text: String): String = when {
        listOf("스타벅스", "이디야", "카페", "커피", "베이커리", "빵").any { it in text } ->
            FinanceCategory.CAFE
        listOf("GS25", "CU", "세븐일레븐", "편의점", "마트", "쿠팡", "배달", "식당", "음식").any { it in text } ->
            FinanceCategory.FOOD
        listOf("지하철", "버스", "Tmap", "택시", "카카오T", "주유", "전철").any { it in text } ->
            FinanceCategory.TRANSPORT
        listOf("병원", "약국", "의원", "치과", "한의원").any { it in text } ->
            FinanceCategory.MEDICAL
        listOf("넷플릭스", "유튜브", "스포티파이", "구독", "Apple", "Google", "게임").any { it in text } ->
            FinanceCategory.SUBSCRIPTION
        else -> FinanceCategory.ETC
    }
}
