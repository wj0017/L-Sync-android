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

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        if (packageName !in PAYMENT_PACKAGES) return null

        val combined = "$title $text"
        val hasPaymentKeyword = listOf("결제", "승인", "출금").any { it in combined }
        if (!hasPaymentKeyword) return null

        val amount = extractAmount(text) ?: return null
        val category = inferCategory(text)
        val note = extractNote(text)

        return ParsedPayment(amount, note, category)
    }

    internal fun extractAmount(text: String): Long? {
        val regex = Regex("""(\d{1,3}(?:,\d{3})*)원""")
        val match = regex.find(text) ?: return null
        return runCatching { match.groupValues[1].replace(",", "").toLong() }.getOrNull()
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
        listOf("넷플릭스", "유튜브", "스포티파이", "구독", "Apple", "Google").any { it in text } ->
            FinanceCategory.SUBSCRIPTION
        else -> FinanceCategory.ETC
    }

    internal fun extractNote(text: String): String = text.take(100)
}
