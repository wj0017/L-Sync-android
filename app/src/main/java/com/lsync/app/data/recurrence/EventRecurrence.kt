package com.lsync.app.data.recurrence

// 반복 일정(Event)을 iCal식 "읽기-전개"로 다루는 순수 Kotlin 엔진 (Phase 15).
// 단일 Event row의 rrule을 가시 범위로 전개(expand)해 가상 발생(EventOccurrence)을 만든다.
// 별도 인스턴스 row를 만들지 않는다(Todo Materialization과 다른 전략).
//
// 규칙(핵심):
//  - 종일 일정은 Floating Date(YYYY-MM-DD). 타임존 변환 금지.
//  - 시간지정 발생은 날짜 부분만 발생일로 치환하고 시각·offset 문자열은 그대로 보존한다.
//  - override 키는 "발생 원본 날짜". override는 발생 날짜를 옮기지 않는다(시각만 변경).
//  - 무한 rrule 방어(범위 상한 + MAX_ITERATIONS). 파싱 실패는 예외 없이 흡수한다.

import com.lsync.app.data.local.entity.EventEntity
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.LocalDate

private const val MS_PER_DAY = 86400000L
private const val MAX_ITERATIONS = 10_000

data class EventOccurrence(
    val masterId: String,      // 원본 EventEntity.id
    val date: String,          // YYYY-MM-DD — 발생 날짜(전개 키)
    val startDate: String,     // 유효 startDate. 시간지정이면 date 부분만 발생일로 치환, 시각·offset 보존
    val title: String,         // 유효 제목(override 반영)
    val isAllDay: Boolean,
    val hasAlarm: Boolean,     // 유효 알람 여부(override 반영)
    val isOverridden: Boolean, // 이 발생에 override가 적용됐는가
    val isRecurring: Boolean,  // rrule에서 나온 발생인가(단발 false)
)

data class EventOverride(
    val title: String? = null,
    val startDate: String? = null, // 시각만 바꿈. 날짜 부분은 발생일과 동일해야 함
    val hasAlarm: Boolean? = null,
)

// ── 전개 ──────────────────────────────────────────────────────────────────────

// 단일 이벤트를 [from, to] (둘 다 inclusive, YYYY-MM-DD)로 전개.
// 비반복(rrule==null): startDate 날짜가 범위 안이면 1개(override/exdate 무시), 아니면 0개.
// 반복: rrule 전개 → 범위 필터 → exdates 제외 → overrides 적용.
fun expandEvent(event: EventEntity, from: String, to: String): List<EventOccurrence> {
    val rrule = event.rrule?.trim()
    if (rrule.isNullOrBlank()) {
        val date = event.startDate.take(10)
        return if (date in from..to) {
            listOf(singleOccurrence(event, date, isRecurring = false))
        } else {
            emptyList()
        }
    }

    val exdates = parseExdates(event.exdatesJson)
    val overrides = parseOverrides(event.overridesJson)

    val fromDate = parseLocalDateOrNull(from) ?: return emptyList()
    val toDate = parseLocalDateOrNull(to) ?: return emptyList()
    if (toDate.isBefore(fromDate)) return emptyList()
    val dtStart = parseLocalDateOrNull(event.startDate.take(10)) ?: return emptyList()

    val result = mutableListOf<EventOccurrence>()
    try {
        val rule = RecurrenceRule(rrule.removePrefix("RRULE:"))
        val iter = rule.iterator(DateTime(dtStart.toEpochDay() * MS_PER_DAY))
        var safety = 0
        while (iter.hasNext() && safety < MAX_ITERATIONS) {
            safety++
            val occDate = LocalDate.ofEpochDay(iter.nextMillis() / MS_PER_DAY)
            if (occDate.isAfter(toDate)) break
            if (occDate.isBefore(fromDate)) continue

            val dateStr = occDate.toString()
            if (dateStr in exdates) continue
            result.add(buildOccurrence(event, dateStr, overrides[dateStr]))
        }
    } catch (e: Exception) {
        // 깨진 rrule은 빈/부분 결과로 흡수 (UI/알람 크래시 방지)
    }
    return result
}

// 여러 이벤트 전개 후 평탄화(ViewModel 편의).
fun expandEvents(events: List<EventEntity>, from: String, to: String): List<EventOccurrence> =
    events.flatMap { expandEvent(it, from, to) }

// afterDate(YYYY-MM-DD) "다음" 발생을 1개 반환(알람용). exdates 제외·overrides 반영. 없으면 null.
fun nextOccurrence(event: EventEntity, afterDate: String): EventOccurrence? {
    val rrule = event.rrule?.trim()
    if (rrule.isNullOrBlank()) {
        val date = event.startDate.take(10)
        return if (date > afterDate) singleOccurrence(event, date, isRecurring = false) else null
    }

    val exdates = parseExdates(event.exdatesJson)
    val overrides = parseOverrides(event.overridesJson)
    val dtStart = parseLocalDateOrNull(event.startDate.take(10)) ?: return null

    return try {
        val rule = RecurrenceRule(rrule.removePrefix("RRULE:"))
        val iter = rule.iterator(DateTime(dtStart.toEpochDay() * MS_PER_DAY))
        var safety = 0
        var found: EventOccurrence? = null
        while (iter.hasNext() && safety < MAX_ITERATIONS) {
            safety++
            val occDate = LocalDate.ofEpochDay(iter.nextMillis() / MS_PER_DAY)
            val dateStr = occDate.toString()
            if (dateStr <= afterDate) continue
            if (dateStr in exdates) continue
            found = buildOccurrence(event, dateStr, overrides[dateStr])
            break
        }
        found
    } catch (e: Exception) {
        null
    }
}

// ── 발생 빌더 ─────────────────────────────────────────────────────────────────

private fun singleOccurrence(event: EventEntity, date: String, isRecurring: Boolean) =
    EventOccurrence(
        masterId = event.id,
        date = date,
        startDate = if (event.isAllDay) date else effectiveTimeStart(event, date, null),
        title = event.title,
        isAllDay = event.isAllDay,
        hasAlarm = event.hasAlarm,
        isOverridden = false,
        isRecurring = isRecurring,
    )

private fun buildOccurrence(
    event: EventEntity,
    date: String,
    override: EventOverride?,
): EventOccurrence {
    val startDate = if (event.isAllDay) date else effectiveTimeStart(event, date, override)
    return EventOccurrence(
        masterId = event.id,
        date = date,
        startDate = startDate,
        title = override?.title ?: event.title,
        isAllDay = event.isAllDay,
        hasAlarm = override?.hasAlarm ?: event.hasAlarm,
        isOverridden = override != null,
        isRecurring = true,
    )
}

// 시간지정 발생의 startDate: 날짜는 발생일로 치환, 시각·offset 문자열은 보존.
// override.startDate가 있으면 시각 부분만 그것으로 교체(날짜는 항상 발생일).
private fun effectiveTimeStart(event: EventEntity, date: String, override: EventOverride?): String {
    val source = override?.startDate?.takeIf { it.length >= 10 } ?: event.startDate
    val timePart = if (source.length > 10) source.substring(10) else ""
    return date + timePart
}

private fun parseLocalDateOrNull(s: String): LocalDate? =
    try {
        LocalDate.parse(s)
    } catch (e: Exception) {
        null
    }

// ── exdates JSON 헬퍼 (문자열 배열) ───────────────────────────────────────────

fun parseExdates(json: String?): Set<String> {
    if (json.isNullOrBlank()) return emptySet()
    return try {
        val parsed = JsonParser(json).parse()
        if (parsed is List<*>) parsed.filterIsInstance<String>().toSet() else emptySet()
    } catch (e: Exception) {
        emptySet()
    }
}

// date를 추가한 JSON 배열 문자열을 반환(정렬 — 결정론적 출력).
fun withExdate(json: String?, date: String): String {
    val dates = (parseExdates(json) + date).sorted()
    return dates.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
}

// ── overrides JSON 헬퍼 (맵: date → {title?, startDate?, hasAlarm?}) ───────────

fun parseOverrides(json: String?): Map<String, EventOverride> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val parsed = JsonParser(json).parse()
        if (parsed !is Map<*, *>) return emptyMap()
        parsed.entries.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val obj = v as? Map<*, *> ?: return@mapNotNull null
            key to EventOverride(
                title = obj["title"] as? String,
                startDate = obj["startDate"] as? String,
                hasAlarm = obj["hasAlarm"] as? Boolean,
            )
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

// date에 override를 추가/병합한 JSON 맵 문자열을 반환(키 정렬 — 결정론적 출력).
fun withOverride(json: String?, date: String, override: EventOverride): String {
    val merged = parseOverrides(json).toMutableMap()
    merged[date] = override
    return merged.entries
        .sortedBy { it.key }
        .joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
            "\"${escapeJson(k)}\":${encodeOverride(v)}"
        }
}

private fun encodeOverride(o: EventOverride): String {
    val fields = mutableListOf<String>()
    o.title?.let { fields.add("\"title\":\"${escapeJson(it)}\"") }
    o.startDate?.let { fields.add("\"startDate\":\"${escapeJson(it)}\"") }
    o.hasAlarm?.let { fields.add("\"hasAlarm\":$it") }
    return fields.joinToString(separator = ",", prefix = "{", postfix = "}")
}

// ── 최소 JSON 인코딩/파싱 (Android org.json 미사용 — 순수 JVM 테스트 가능) ──────

private fun escapeJson(s: String): String {
    val sb = StringBuilder(s.length + 2)
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

// object/array/string/boolean/null/number를 지원하는 소형 재귀하강 파서.
private class JsonParser(private val s: String) {
    private var i = 0

    fun parse(): Any? {
        val v = parseValue()
        skipWs()
        return v
    }

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun parseValue(): Any? {
        skipWs()
        require(i < s.length) { "unexpected end" }
        return when (s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        i++ // {
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return map }
        while (true) {
            skipWs()
            require(i < s.length && s[i] == '"') { "expected key" }
            val key = parseString()
            skipWs()
            require(i < s.length && s[i] == ':') { "expected :" }
            i++
            map[key] = parseValue()
            skipWs()
            require(i < s.length) { "unterminated object" }
            when (s[i]) {
                ',' -> { i++; }
                '}' -> { i++; return map }
                else -> throw IllegalArgumentException("expected , or }")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        val list = ArrayList<Any?>()
        i++ // [
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return list }
        while (true) {
            list.add(parseValue())
            skipWs()
            require(i < s.length) { "unterminated array" }
            when (s[i]) {
                ',' -> { i++; }
                ']' -> { i++; return list }
                else -> throw IllegalArgumentException("expected , or ]")
            }
        }
    }

    private fun parseString(): String {
        i++ // opening "
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    require(i < s.length) { "bad escape" }
                    when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw IllegalArgumentException("unterminated string")
    }

    private fun parseBoolean(): Boolean = when {
        s.startsWith("true", i) -> { i += 4; true }
        s.startsWith("false", i) -> { i += 5; false }
        else -> throw IllegalArgumentException("bad boolean")
    }

    private fun parseNull(): Any? = if (s.startsWith("null", i)) {
        i += 4; null
    } else {
        throw IllegalArgumentException("bad null")
    }

    private fun parseNumber(): Double {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        return s.substring(start, i).toDouble()
    }
}
