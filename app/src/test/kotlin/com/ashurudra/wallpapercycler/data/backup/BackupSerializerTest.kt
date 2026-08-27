package com.ashurudra.wallpapercycler.data.backup

import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import com.ashurudra.wallpapercycler.domain.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class BackupSerializerTest {

    private val intervalSchedule = Schedule(
        id = "s1",
        enabled = true,
        targets = setOf(ScreenTarget.HOME, ScreenTarget.LOCK),
        label = "Nature",
        trigger = Trigger.Interval(everyMillis = 1_800_000L),
        source = ImageSourceConfig.LinkedFolder(treeUri = "content://tree/abc"),
        shuffleEnabled = true,
        sortOrder = SortOrder.NAME_ASC,
        fitMode = FitMode.FILL,
        anchoredAt = Instant.ofEpochMilli(1_000_000L),
    )

    private val timesOfDaySchedule = Schedule(
        id = "s2",
        enabled = false,
        targets = setOf(ScreenTarget.HOME),
        label = "",
        trigger = Trigger.TimesOfDay(
            times = listOf(LocalTime.of(7, 30), LocalTime.of(22, 0)),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        ),
        source = ImageSourceConfig.ManagedSet(setId = "set-123"),
        shuffleEnabled = false,
        sortOrder = SortOrder.DATE_DESC,
        fitMode = FitMode.FIT_BLUR,
        anchoredAt = Instant.EPOCH,
    )

    @Test
    fun `round trip preserves every schedule field except enabled and anchoredAt`() {
        val json = BackupSerializer.serialize(
            schedules = listOf(intervalSchedule, timesOfDaySchedule),
            themeMode = ThemeMode.DARK,
            customAccentArgb = 0xFF00FF00.toInt(),
        )

        val restored = BackupSerializer.deserialize(json).getOrThrow()

        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(0xFF00FF00.toInt(), restored.customAccentArgb)
        assertEquals(2, restored.schedules.size)

        val restoredInterval = restored.schedules.first { it.id == "s1" }
        assertEquals(intervalSchedule.label, restoredInterval.label)
        assertEquals(intervalSchedule.targets, restoredInterval.targets)
        assertEquals(intervalSchedule.trigger, restoredInterval.trigger)
        assertEquals(intervalSchedule.source, restoredInterval.source)
        assertEquals(intervalSchedule.shuffleEnabled, restoredInterval.shuffleEnabled)
        assertEquals(intervalSchedule.sortOrder, restoredInterval.sortOrder)
        assertEquals(intervalSchedule.fitMode, restoredInterval.fitMode)
        // Deliberately not preserved - see BackupSerializer's kdoc.
        assertEquals(false, restoredInterval.enabled)
        assertEquals(Instant.EPOCH, restoredInterval.anchoredAt)

        val restoredTimesOfDay = restored.schedules.first { it.id == "s2" }
        assertEquals(timesOfDaySchedule.trigger, restoredTimesOfDay.trigger)
        assertEquals(timesOfDaySchedule.source, restoredTimesOfDay.source)
    }

    @Test
    fun `round trip with no custom accent preserves null`() {
        val json = BackupSerializer.serialize(listOf(intervalSchedule), ThemeMode.LIGHT, customAccentArgb = null)

        val restored = BackupSerializer.deserialize(json).getOrThrow()

        assertEquals(null, restored.customAccentArgb)
        assertEquals(ThemeMode.LIGHT, restored.themeMode)
    }

    @Test
    fun `unknown top-level and nested fields are ignored instead of failing`() {
        val jsonWithExtras = """
            {
              "version": 99,
              "aFutureTopLevelField": "should be ignored",
              "schedules": [
                {
                  "id": "s1",
                  "label": "Nature",
                  "targets": ["HOME"],
                  "trigger": {"type": "interval", "everyMillis": 60000, "aFutureField": 42},
                  "source": {"type": "linked_folder", "treeUri": "content://tree/abc"},
                  "shuffleEnabled": true,
                  "sortOrder": "NAME_ASC",
                  "fitMode": "FILL",
                  "aFutureScheduleField": {"nested": true}
                }
              ],
              "themeMode": "DARK",
              "customAccentArgb": null
            }
        """.trimIndent()

        val result = BackupSerializer.deserialize(jsonWithExtras)

        assertTrue(result.isSuccess)
        val restored = result.getOrThrow()
        assertEquals(1, restored.schedules.size)
        assertEquals("s1", restored.schedules.first().id)
        assertEquals(ThemeMode.DARK, restored.themeMode)
    }

    @Test
    fun `malformed json fails instead of throwing`() {
        val result = BackupSerializer.deserialize("not valid json at all")

        assertTrue(result.isFailure)
    }

    @Test
    fun `unknown enum values fall back to sensible defaults instead of failing`() {
        val jsonWithUnknownEnums = """
            {
              "version": 1,
              "schedules": [
                {
                  "id": "s1",
                  "label": "",
                  "targets": ["HOME", "A_FUTURE_TARGET"],
                  "trigger": {"type": "interval", "everyMillis": 60000},
                  "source": {"type": "linked_folder", "treeUri": "content://tree/abc"},
                  "shuffleEnabled": true,
                  "sortOrder": "A_FUTURE_SORT_ORDER",
                  "fitMode": "A_FUTURE_FIT_MODE"
                }
              ],
              "themeMode": "A_FUTURE_THEME_MODE",
              "customAccentArgb": null
            }
        """.trimIndent()

        val restored = BackupSerializer.deserialize(jsonWithUnknownEnums).getOrThrow()

        assertEquals(ThemeMode.SYSTEM, restored.themeMode)
        val schedule = restored.schedules.first()
        assertEquals(setOf(ScreenTarget.HOME), schedule.targets)
        assertEquals(SortOrder.NAME_ASC, schedule.sortOrder)
        assertEquals(FitMode.FILL, schedule.fitMode)
    }
}
