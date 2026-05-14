package com.lsync.app.notification

import com.lsync.app.data.local.FinanceCategory

data class ParsedPayment(
    val amount: Long,
    val note: String,
    val category: String,
    val type: String,  // "INCOME" | "EXPENSE"
)

object PaymentNotificationParser {

    val PAYMENT_PACKAGES: Set<String> = setOf(
        "viva.republica.toss",
        "com.viva.finance",
        "com.shinhan.smartsalary",
        "com.kbcard.kbcardclient",
        "com.kakaobank.channel",
    )

    private val TITLE_TRANSACTION_REGEX = Regex("""(\d{1,3}(?:,\d{3})*)원\s*(?:결제|승인|출금|이체|입금|환급|반환)""")
    private val PIPE_MERCHANT_REGEX = Regex("""\|\s*(.+)""")

    private val INCOME_KEYWORDS  = listOf("입금", "환급", "반환", "수신")
    private val EXPENSE_KEYWORDS = listOf("결제", "승인", "출금", "이체")

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        if (packageName !in PAYMENT_PACKAGES) return null

        // 타이틀에 "금액+거래 키워드"가 없으면 알림성 메시지로 간주 (예상 환급액 안내, 통신비 도착 등)
        val amount = extractAmountFromTitle(title) ?: return null

        val combined = "$title $text"
        val type = if (INCOME_KEYWORDS.any { it in combined }) "INCOME" else "EXPENSE"

        val merchant = extractMerchant(text)
        val category = if (type == "INCOME") FinanceCategory.ETC
                       else inferCategory(if (merchant.isNotBlank()) merchant else text)
        val note = merchant.ifBlank {
            text.lines().firstOrNull { "잔액" !in it && it.isNotBlank() }?.trim() ?: title
        }.take(100)

        return ParsedPayment(amount, note, category, type)
    }

    // "1,417원 결제" / "10,000원 입금" 패턴 — 제목에서만 쓰는 추출
    internal fun extractAmountFromTitle(title: String): Long? {
        val match = TITLE_TRANSACTION_REGEX.find(title) ?: return null
        return runCatching { match.groupValues[1].replace(",", "").toLong() }.getOrNull()
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
