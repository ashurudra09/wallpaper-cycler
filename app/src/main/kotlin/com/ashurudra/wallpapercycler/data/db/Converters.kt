package com.ashurudra.wallpapercycler.data.db

import androidx.room.TypeConverter
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.Trigger
import java.time.DayOfWeek
import java.time.LocalTime

/** Unit-separator control character — safe since filenames can legitimately contain commas. */
private const val SEQUENCE_DELIMITER = ""

class Converters {

    @TypeConverter
    fun targetsToString(targets: Set<ScreenTarget>): String = targets.joinToString(",") { it.name }

    @TypeConverter
    fun stringToTargets(value: String): Set<ScreenTarget> =
        if (value.isEmpty()) emptySet() else value.split(",").map(ScreenTarget::valueOf).toSet()

    @TypeConverter
    fun sortOrderToString(sortOrder: SortOrder): String = sortOrder.name

    @TypeConverter
    fun stringToSortOrder(value: String): SortOrder = SortOrder.valueOf(value)

    @TypeConverter
    fun fitModeToString(fitMode: FitMode): String = fitMode.name

    @TypeConverter
    fun stringToFitMode(value: String): FitMode = FitMode.valueOf(value)

    @TypeConverter
    fun sequenceToString(sequence: List<String>): String = sequence.joinToString(SEQUENCE_DELIMITER)

    @TypeConverter
    fun stringToSequence(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEQUENCE_DELIMITER)

    @TypeConverter
    fun triggerToString(trigger: Trigger): String = when (trigger) {
        is Trigger.Interval -> "INTERVAL|${trigger.everyMillis}"
        is Trigger.TimesOfDay -> {
            val times = trigger.times.joinToString(",") { it.toString() }
            val days = trigger.daysOfWeek.joinToString(",") { it.name }
            "TIMES_OF_DAY|$times|$days"
        }
    }

    @TypeConverter
    fun stringToTrigger(value: String): Trigger {
        val parts = value.split("|")
        return when (parts[0]) {
            "INTERVAL" -> Trigger.Interval(everyMillis = parts[1].toLong())
            "TIMES_OF_DAY" -> Trigger.TimesOfDay(
                times = if (parts[1].isEmpty()) emptyList() else parts[1].split(",").map(LocalTime::parse),
                daysOfWeek = if (parts[2].isEmpty()) emptySet() else parts[2].split(",").map(DayOfWeek::valueOf).toSet(),
            )
            else -> error("Unknown trigger type: ${parts[0]}")
        }
    }

    @TypeConverter
    fun sourceToString(source: ImageSourceConfig): String = when (source) {
        is ImageSourceConfig.LinkedFolder -> "LINKED_FOLDER|${source.treeUri}"
        is ImageSourceConfig.ManagedSet -> "MANAGED_SET|${source.setId}"
    }

    @TypeConverter
    fun stringToSource(value: String): ImageSourceConfig {
        val separatorIndex = value.indexOf('|')
        val type = value.substring(0, separatorIndex)
        val data = value.substring(separatorIndex + 1)
        return when (type) {
            "LINKED_FOLDER" -> ImageSourceConfig.LinkedFolder(treeUri = data)
            "MANAGED_SET" -> ImageSourceConfig.ManagedSet(setId = data)
            else -> error("Unknown source type: $type")
        }
    }
}
