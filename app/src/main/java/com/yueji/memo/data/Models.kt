package com.yueji.memo.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

// ---------------------------------------------------------------------------
// Domain models. Everything is persisted as plain JSON by AppRepository, so no
// annotation processor is involved anywhere in this project.
// ---------------------------------------------------------------------------

enum class NoteTint { DEFAULT, SAND, MINT, SKY, LILAC, ROSE }

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val tint: NoteTint = NoteTint.DEFAULT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A one-shot to-do that fires reminders 20 and 10 minutes before [startAt]. */
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val detail: String = "",
    val startAt: Long = System.currentTimeMillis(),
    val done: Boolean = false,
    val remind20: Boolean = true,
    val remind10: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val start: LocalDateTime get() = TimeCodec.toDateTime(startAt)
}

/** A recurring weekly item, e.g. every Mon/Wed/Fri at 19:30. */
data class WeeklyItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val detail: String = "",
    /** ISO day numbers: 1 = Monday ... 7 = Sunday. */
    val days: Set<Int> = setOf(1),
    /** Wall-clock "HH:mm". */
    val time: String = "09:00",
    val enabled: Boolean = true,
    val remind20: Boolean = true,
    val remind10: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val localTime: LocalTime get() = TimeCodec.toTime(time)
    val sortedDays: List<Int> get() = days.filter { it in 1..7 }.sorted()
    val dayLabels: String get() = sortedDays.joinToString(" ") { TimeCodec.shortWeekday(it) }
}

// ---------------------------------------------------------------------------
// Time helpers. minSdk 26 means java.time is available without desugaring.
// ---------------------------------------------------------------------------

object TimeCodec {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val ymdHm: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val weekdayCn = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun toDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun toTime(hhmm: String): LocalTime =
        runCatching { LocalTime.parse(hhmm.trim(), hm) }.getOrDefault(LocalTime.of(9, 0))

    fun formatTime(hhmm: String): String = toTime(hhmm).format(hm)

    fun formatDateTime(millis: Long): String = toDateTime(millis).format(ymdHm)

    fun formatClock(millis: Long): String = toDateTime(millis).format(hm)

    fun formatDate(date: LocalDate): String = date.format(ymd)

    /** "10月3日 周五" */
    fun formatDateCn(date: LocalDate): String =
        "${date.monthValue}月${date.dayOfMonth}日 ${weekdayCn[date.dayOfWeek.value - 1]}"

    /** "今天 / 明天 / 昨天 / 10月3日 周五" */
    fun relativeDay(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.minusDays(1) -> "昨天"
            else -> formatDateCn(date)
        }
    }

    fun shortWeekday(isoDay: Int): String =
        weekdayCn[(isoDay - 1).coerceIn(0, 6)]

    fun fullWeekday(isoDay: Int): String =
        listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[(isoDay - 1).coerceIn(0, 6)]

    /** Next instant (epoch millis) at which [dayOfWeek] + [time] occurs, strictly after [from]. */
    fun nextOccurrence(dayOfWeek: DayOfWeek, time: LocalTime, from: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = LocalDateTime.of(from.toLocalDate(), time)
        var guard = 0
        while ((candidate.dayOfWeek != dayOfWeek || !candidate.isAfter(from)) && guard < 14) {
            candidate = candidate.plusDays(1)
            guard++
        }
        return candidate
    }

    /** Next instant strictly after [from] at the given wall-clock time (daily recurrence). */
    fun nextDaily(time: LocalTime, from: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = LocalDateTime.of(from.toLocalDate(), time)
        if (!candidate.isAfter(from)) candidate = candidate.plusDays(1)
        return candidate
    }

    fun greeting(): String = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}

/** Parses "HH:mm" defensively and returns a normalised string. */
fun normalizeTimeInput(raw: String): String? {
    val cleaned = raw.trim().replace('：', ':').replace('.', ':')
    val parts = cleaned.split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return "%02d:%02d".format(h, m)
}
