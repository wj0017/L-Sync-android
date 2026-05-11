package com.lsync.app.data.local

object FinanceCategory {
    const val FOOD = "식비"
    const val TRANSPORT = "교통"
    const val CAFE = "카페/간식"
    const val SHOPPING = "쇼핑"
    const val MEDICAL = "의료"
    const val SUBSCRIPTION = "구독"
    const val ETC = "기타"

    val all: List<String> = listOf(FOOD, TRANSPORT, CAFE, SHOPPING, MEDICAL, SUBSCRIPTION, ETC)
}
