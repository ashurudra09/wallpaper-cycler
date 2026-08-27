package com.ashurudra.wallpapercycler.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * [TimesOfDay] may legitimately hold an empty [times] list or [daysOfWeek] set — that
 * represents a schedule with no valid trigger yet (e.g. every day-of-week toggle off).
 * NextTickCalculator treats that as "no next occurrence" rather than an error.
 */
sealed interface Trigger {
    data class Interval(val everyMillis: Long) : Trigger

    data class TimesOfDay(
        val times: List<LocalTime>,
        val daysOfWeek: Set<DayOfWeek>,
    ) : Trigger
}
