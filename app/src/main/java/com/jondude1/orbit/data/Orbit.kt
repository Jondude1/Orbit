package com.jondude1.orbit.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The resurfacing rules. Thoughts only come back at a "good time": the review
 * slot the person picked (e.g. 7:30 PM on chosen days). Returns are spaced out
 * like flash cards: each "not now" pushes a thought further out, each
 * "this still excites me" pulls it closer.
 */
object Orbit {
    const val DAY = 24L * 60 * 60 * 1000
    private val ladder = listOf(1, 3, 7, 14, 30, 60, 90)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun nextLonger(days: Int): Int = ladder.firstOrNull { it > days } ?: ladder.last()
    fun nextShorter(days: Int): Int = ladder.lastOrNull { it < days } ?: ladder.first()

    /** The first review slot at or after [from]. */
    fun slotAtOrAfter(from: Long, s: OrbitSettings = Settings.current): Long {
        val time = LocalTime.of(s.reviewHour, s.reviewMinute)
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(from), zone)
        var date = start.toLocalDate()
        repeat(15) {
            val candidate = date.atTime(time)
            if (date.dayOfWeek in s.days && !candidate.isBefore(start)) {
                return candidate.atZone(zone).toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return from + DAY
    }

    /** The review slot on the day [days] from now (or the next allowed day after it). */
    fun slotInDays(days: Int, now: Long = System.currentTimeMillis(), s: OrbitSettings = Settings.current): Long {
        val date = LocalDate.now(zone).plusDays(days.toLong())
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return slotAtOrAfter(maxOf(dayStart, now), s)
    }

    enum class When(val label: String) {
        TONIGHT("Tonight"),
        TOMORROW("Tomorrow"),
        WEEKEND("This weekend"),
        NEXT_WEEK("Next week"),
        NEXT_MONTH("Next month"),
        LATER("In 3 months"),
    }

    /** Returns (surfaceAt, intervalDays) for a "bring it back…" choice. */
    fun at(w: When, now: Long = System.currentTimeMillis(), s: OrbitSettings = Settings.current): Pair<Long, Int> {
        val today = LocalDate.now(zone)
        val time = LocalTime.of(s.reviewHour, s.reviewMinute)
        fun ms(d: LocalDate, t: LocalTime = time) = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        return when (w) {
            When.TONIGHT -> {
                val tonight = ms(today)
                (if (tonight > now) tonight else now + 60 * 60 * 1000L) to 1
            }
            When.TOMORROW -> ms(today.plusDays(1)) to 1
            When.WEEKEND -> {
                var sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                if (ms(sat, LocalTime.of(10, 0)) <= now) sat = sat.plusWeeks(1)
                ms(sat, LocalTime.of(10, 0)) to 7
            }
            When.NEXT_WEEK -> slotInDays(7, now, s) to 7
            When.NEXT_MONTH -> slotInDays(30, now, s) to 30
            When.LATER -> slotInDays(90, now, s) to 90
        }
    }

    // ---- Friendly time text -------------------------------------------------

    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    fun whenText(millis: Long, now: Long = System.currentTimeMillis()): String {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(today, t.toLocalDate())
        val time = t.format(timeFmt)
        return when {
            millis <= now -> "now"
            days == 0L -> "today at $time"
            days == 1L -> "tomorrow at $time"
            days < 7 -> "${t.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} at $time"
            else -> t.format(dayFmt)
        }
    }

    fun agoText(millis: Long, now: Long = System.currentTimeMillis()): String {
        val mins = (now - millis) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            mins < 60 * 24 * 2 -> "yesterday"
            mins < 60 * 24 * 30 -> "${mins / (60 * 24)} days ago"
            mins < 60 * 24 * 365 -> "${mins / (60 * 24 * 30)} months ago"
            else -> "over a year ago"
        }
    }

    fun slotTimeText(s: OrbitSettings): String = LocalTime.of(s.reviewHour, s.reviewMinute).format(timeFmt)
}
