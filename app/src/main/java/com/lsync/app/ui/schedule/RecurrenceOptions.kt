package com.lsync.app.ui.schedule

// 반복 Todo 템플릿의 반복 규칙(rrule) ↔ UI 입력 변환 헬퍼 (순수 Kotlin, Android 의존 없음)
// buildRrule: UI 입력 → RFC 5545 RRULE 본문 (Materializer의 RecurrenceRule 파서 호환)
// describeRrule: rrule → 한국어 요약 (UI 표시용)

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

// Weekday는 RRULE BYDAY 토큰과 매핑 (MO, TU, WE, TH, FR, SA, SU)
enum class Weekday(val token: String) {
    MON("MO"), TUE("TU"), WED("WE"), THU("TH"), FRI("FR"), SAT("SA"), SUN("SU")
}

data class RecurrenceOption(
    val frequency: Frequency,
    val interval: Int = 1,                  // 1 이상. "N마다"
    val weekdays: Set<Weekday> = emptySet(), // WEEKLY일 때만 의미. 비어있으면 BYDAY 생략
)

// 결정론적 출력을 위해 항상 MO,TU,WE,TH,FR,SA,SU 순서로 정렬
private val WEEKDAY_ORDER = listOf(
    Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI, Weekday.SAT, Weekday.SUN,
)

private val FREQ_TOKEN = mapOf(
    Frequency.DAILY to "DAILY",
    Frequency.WEEKLY to "WEEKLY",
    Frequency.MONTHLY to "MONTHLY",
    Frequency.YEARLY to "YEARLY",
)

fun buildRrule(option: RecurrenceOption): String {
    val sb = StringBuilder("FREQ=${FREQ_TOKEN[option.frequency]}")

    // interval >= 2 일 때만 INTERVAL 추가 (1이면 생략)
    if (option.interval >= 2) {
        sb.append(";INTERVAL=${option.interval}")
    }

    // WEEKLY이고 요일이 지정된 경우에만 BYDAY 추가 (정렬해 결정론적 출력 보장)
    if (option.frequency == Frequency.WEEKLY && option.weekdays.isNotEmpty()) {
        val byday = WEEKDAY_ORDER
            .filter { it in option.weekdays }
            .joinToString(",") { it.token }
        sb.append(";BYDAY=$byday")
    }

    return sb.toString()
}

private val FREQ_UNIT = mapOf(
    "DAILY" to "일",
    "WEEKLY" to "주",
    "MONTHLY" to "개월",
    "YEARLY" to "년",
)

private val FREQ_EVERY = mapOf(
    "DAILY" to "매일",
    "WEEKLY" to "매주",
    "MONTHLY" to "매월",
    "YEARLY" to "매년",
)

private val TOKEN_TO_KOREAN = mapOf(
    "MO" to "월", "TU" to "화", "WE" to "수", "TH" to "목",
    "FR" to "금", "SA" to "토", "SU" to "일",
)

private val TOKEN_ORDER = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

// 알 수 없는/깨진 입력은 예외 없이 "반복" 반환 (UI 크래시 방지)
fun describeRrule(rrule: String): String {
    return try {
        val parts = rrule.removePrefix("RRULE:")
            .split(";")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx).uppercase() to part.substring(idx + 1)
            }
            .toMap()

        val freq = parts["FREQ"]?.uppercase() ?: return "반복"
        if (!FREQ_EVERY.containsKey(freq)) return "반복"

        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1

        val base = if (interval >= 2) {
            "${interval}${FREQ_UNIT[freq]}마다"
        } else {
            FREQ_EVERY[freq]!!
        }

        val byday = parts["BYDAY"]
        if (freq == "WEEKLY" && !byday.isNullOrBlank()) {
            val days = byday.split(",")
                .map { it.trim().uppercase() }
                .sortedBy { TOKEN_ORDER.indexOf(it) }
                .mapNotNull { TOKEN_TO_KOREAN[it] }
            if (days.isNotEmpty()) {
                return "$base ${days.joinToString(",")}"
            }
        }

        base
    } catch (e: Exception) {
        "반복"
    }
}
