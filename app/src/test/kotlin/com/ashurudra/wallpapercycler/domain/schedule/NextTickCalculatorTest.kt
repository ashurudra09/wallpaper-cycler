package com.ashurudra.wallpapercycler.domain.schedule

import com.ashurudra.wallpapercycler.domain.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

// DST dates used below (America/New_York, 2026, per U.S. rules): spring-forward is
// Sunday 2026-03-08 at 02:00 local (clocks jump to 03:00); fall-back is Sunday
// 2026-11-01 at 02:00 local (clocks fall back to 01:00).
class NextTickCalculatorTest {

    private val allDays = DayOfWeek.values().toSet()

    @Test
    fun interval_advancesToSmallestTickStrictlyAfterFrom() {
        val anchoredAt = Instant.parse("2026-01-01T09:15:00Z")
        val everyMillis = Duration.ofHours(6).toMillis()
        val from = anchoredAt.plus(Duration.ofHours(13)) // 2026-01-01T22:15:00Z
        val expected = anchoredAt.plus(Duration.ofHours(18)) // 2026-01-02T03:15:00Z (k = 3)

        val result = nextTriggerAt(Trigger.Interval(everyMillis), anchoredAt, from, ZoneOffset.UTC)

        assertEquals(expected, result)
    }

    @Test
    fun interval_longGapBetweenAnchorAndFromIsComputedDirectly() {
        val anchoredAt = Instant.parse("2026-01-01T00:00:00Z")
        val everyMillis = Duration.ofHours(6).toMillis()
        val from = anchoredAt.plus(Duration.ofDays(200)).plus(Duration.ofHours(3))
        val expected = anchoredAt.plus(Duration.ofDays(200)).plus(Duration.ofHours(6))

        val result = nextTriggerAt(Trigger.Interval(everyMillis), anchoredAt, from, ZoneOffset.UTC)

        assertEquals(expected, result)
    }

    @Test
    fun interval_fromEqualsAnchoredAtReturnsNextTickNotSameInstant() {
        val anchoredAt = Instant.parse("2026-01-01T09:15:00Z")
        val everyMillis = Duration.ofHours(6).toMillis()
        val expected = anchoredAt.plus(Duration.ofHours(6))

        val result = nextTriggerAt(Trigger.Interval(everyMillis), anchoredAt, anchoredAt, ZoneOffset.UTC)

        assertEquals(expected, result)
    }

    @Test
    fun timesOfDay_crossesMidnightIntoNextDay() {
        val zone = ZoneId.of("America/New_York")
        val trigger = Trigger.TimesOfDay(times = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)), daysOfWeek = allDays)
        val from = ZonedDateTime.of(2026, 6, 15, 23, 30, 0, 0, zone).toInstant()
        val expected = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()

        val result = nextTriggerAt(trigger, Instant.EPOCH, from, zone)

        assertEquals(expected, result)
    }

    @Test
    fun timesOfDay_skipsDisabledDaysAcrossWeekBoundary() {
        val zone = ZoneId.of("America/New_York")
        val time = LocalTime.of(10, 0)
        val trigger = Trigger.TimesOfDay(times = listOf(time), daysOfWeek = setOf(DayOfWeek.MONDAY))
        val fromDate = LocalDate.of(2026, 6, 17) // a Wednesday
        val from = ZonedDateTime.of(fromDate, LocalTime.of(15, 0), zone).toInstant()
        val expectedDate = fromDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) // crosses into the next week
        val expected = ZonedDateTime.of(expectedDate, time, zone).toInstant()

        val result = nextTriggerAt(trigger, Instant.EPOCH, from, zone)

        assertEquals(expected, result)
    }

    @Test
    fun timesOfDay_emptyTimesReturnsNull() {
        val zone = ZoneId.of("America/New_York")
        val trigger = Trigger.TimesOfDay(times = emptyList(), daysOfWeek = setOf(DayOfWeek.MONDAY))

        val result = nextTriggerAt(trigger, Instant.EPOCH, Instant.parse("2026-06-17T15:00:00Z"), zone)

        assertNull(result)
    }

    @Test
    fun timesOfDay_emptyDaysOfWeekReturnsNull() {
        val zone = ZoneId.of("America/New_York")
        val trigger = Trigger.TimesOfDay(times = listOf(LocalTime.of(10, 0)), daysOfWeek = emptySet())

        val result = nextTriggerAt(trigger, Instant.EPOCH, Instant.parse("2026-06-17T15:00:00Z"), zone)

        assertNull(result)
    }

    @Test
    fun timesOfDay_springForwardGapUsesJavaTimeDefaultResolution() {
        val zone = ZoneId.of("America/New_York")
        val gapTime = LocalTime.of(2, 30) // inside the skipped 02:00-03:00 gap on 2026-03-08
        val trigger = Trigger.TimesOfDay(times = listOf(gapTime), daysOfWeek = allDays)
        val from = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, zone).toInstant()
        // Whatever ZonedDateTime.of resolves the gap to is what we assert against, rather
        // than a hand-derived wall-clock instant.
        val expected = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 2, 30), zone).toInstant()

        val result = nextTriggerAt(trigger, Instant.EPOCH, from, zone)

        assertEquals(expected, result)
    }

    @Test
    fun timesOfDay_springForwardShiftsUtcInstantForFixedLocalTime() {
        val zone = ZoneId.of("America/New_York")
        val localTime = LocalTime.of(9, 0)
        val trigger = Trigger.TimesOfDay(times = listOf(localTime), daysOfWeek = allDays)

        val fromBefore = ZonedDateTime.of(2026, 3, 5, 0, 0, 0, 0, zone).toInstant() // still EST
        val expectedBefore = ZonedDateTime.of(2026, 3, 5, 9, 0, 0, 0, zone).toInstant()
        val resultBefore = nextTriggerAt(trigger, Instant.EPOCH, fromBefore, zone)

        val fromAfter = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, zone).toInstant() // already EDT
        val expectedAfter = ZonedDateTime.of(2026, 3, 9, 9, 0, 0, 0, zone).toInstant()
        val resultAfter = nextTriggerAt(trigger, Instant.EPOCH, fromAfter, zone)

        assertEquals(expectedBefore, resultBefore)
        assertEquals(expectedAfter, resultAfter)
        // EST is UTC-5 and EDT is UTC-4, so the same 09:00 local time lands an hour
        // earlier in UTC once EDT takes effect (14:00Z before, 13:00Z after).
        assertEquals(LocalTime.of(14, 0), expectedBefore.atZone(ZoneOffset.UTC).toLocalTime())
        assertEquals(LocalTime.of(13, 0), expectedAfter.atZone(ZoneOffset.UTC).toLocalTime())
    }

    @Test
    fun timesOfDay_fallBackShiftsUtcInstantForFixedLocalTime() {
        val zone = ZoneId.of("America/New_York")
        val localTime = LocalTime.of(9, 0)
        val trigger = Trigger.TimesOfDay(times = listOf(localTime), daysOfWeek = allDays)

        val fromBefore = ZonedDateTime.of(2026, 10, 30, 0, 0, 0, 0, zone).toInstant() // still EDT
        val expectedBefore = ZonedDateTime.of(2026, 10, 30, 9, 0, 0, 0, zone).toInstant()
        val resultBefore = nextTriggerAt(trigger, Instant.EPOCH, fromBefore, zone)

        val fromAfter = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, zone).toInstant() // already EST
        val expectedAfter = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, zone).toInstant()
        val resultAfter = nextTriggerAt(trigger, Instant.EPOCH, fromAfter, zone)

        assertEquals(expectedBefore, resultBefore)
        assertEquals(expectedAfter, resultAfter)
        // EDT is UTC-4 and EST is UTC-5, so the same 09:00 local time lands an hour
        // later in UTC once EST takes effect (13:00Z before, 14:00Z after).
        assertEquals(LocalTime.of(13, 0), expectedBefore.atZone(ZoneOffset.UTC).toLocalTime())
        assertEquals(LocalTime.of(14, 0), expectedAfter.atZone(ZoneOffset.UTC).toLocalTime())
    }

    @Test
    fun timesOfDay_springForwardGapTimeDoesNotShadowAnEarlierUnambiguousInstant() {
        val zone = ZoneId.of("America/New_York")
        // 02:15 falls inside the 2026-03-08 spring-forward gap ([02:00, 03:00) is skipped),
        // so ZonedDateTime.of pushes it forward by the 1-hour gap to 03:15 EDT (07:15Z).
        // 03:00 resolves normally, unambiguously, to 07:00Z -- which is EARLIER, even
        // though 02:15 sorts before 03:00 as a plain LocalTime. The result must be the
        // earliest qualifying instant (07:00Z), not the first LocalTime-order match.
        val trigger = Trigger.TimesOfDay(
            times = listOf(LocalTime.of(2, 15), LocalTime.of(3, 0)),
            daysOfWeek = allDays,
        )
        val from = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone).toInstant() // 2026-03-08T06:30:00Z
        val expected = Instant.parse("2026-03-08T07:00:00Z")

        val result = nextTriggerAt(trigger, Instant.EPOCH, from, zone)

        assertEquals(expected, result)
    }

    @Test
    fun timesOfDay_fromEqualsAValidOccurrenceReturnsNextOneNotSameInstant() {
        val zone = ZoneId.of("America/New_York")
        val trigger = Trigger.TimesOfDay(times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), daysOfWeek = allDays)
        val from = ZonedDateTime.of(2026, 6, 15, 8, 0, 0, 0, zone).toInstant()
        val expected = ZonedDateTime.of(2026, 6, 15, 20, 0, 0, 0, zone).toInstant()

        val result = nextTriggerAt(trigger, Instant.EPOCH, from, zone)

        assertEquals(expected, result)
    }
}
