package com.ashurudra.wallpapercycler.domain.schedule

import com.ashurudra.wallpapercycler.domain.model.Trigger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun nextTriggerAt(
    trigger: Trigger,
    anchoredAt: Instant,
    from: Instant,
    zone: ZoneId,
): Instant? = when (trigger) {
    is Trigger.Interval -> {
        val diffMillis = from.toEpochMilli() - anchoredAt.toEpochMilli()
        // floorDiv (not truncating division) keeps k correct when anchoredAt is after from.
        val k = maxOf(1L, Math.floorDiv(diffMillis, trigger.everyMillis) + 1)
        anchoredAt.plusMillis(k * trigger.everyMillis)
    }
    is Trigger.TimesOfDay -> nextTimesOfDayTrigger(trigger, from, zone)
}

private fun nextTimesOfDayTrigger(trigger: Trigger.TimesOfDay, from: Instant, zone: ZoneId): Instant? {
    if (trigger.times.isEmpty() || trigger.daysOfWeek.isEmpty()) return null

    val fromDate = from.atZone(zone).toLocalDate()

    // At least one day-of-week is enabled, so a matching (date, time) strictly after
    // `from` is guaranteed within the next 7 days.
    for (offset in 0..7L) {
        val candidateDate = fromDate.plusDays(offset)
        if (candidateDate.dayOfWeek !in trigger.daysOfWeek) continue

        // Resolve every time-of-day for this date and pick the earliest instant that is
        // still after `from`. We cannot return on the first LocalTime-order match: on a
        // DST spring-forward date, ZonedDateTime.of pushes a LocalTime that falls inside
        // the gap forward by the gap length, which can land it AFTER another time-of-day
        // that sorts later as a LocalTime but resolves to an earlier, unambiguous instant.
        // Comparing by resolved instant (not LocalTime order) is what preserves the
        // "earliest occurrence after from" contract across day boundaries too, since a
        // date's occurrences never span past the next date's 00:00 local.
        val earliestForDate = trigger.times
            .asSequence()
            .map { time -> ZonedDateTime.of(LocalDateTime.of(candidateDate, time), zone).toInstant() }
            .filter { it.isAfter(from) }
            .minOrNull()
        if (earliestForDate != null) return earliestForDate
    }
    return null
}
