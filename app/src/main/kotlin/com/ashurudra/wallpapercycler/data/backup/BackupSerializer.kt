package com.ashurudra.wallpapercycler.data.backup

import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import com.ashurudra.wallpapercycler.domain.model.Trigger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * JSON backup/restore of everything that CAN be serialized: schedules, settings, and the
 * custom accent. Folder permissions are deliberately excluded - a SAF grant is tied to this
 * install and cannot be exported, so a restored [ImageSourceConfig.LinkedFolder] schedule
 * keeps its old tree URI string but has no read grant for it until the user re-links the
 * folder from the editor. A restored [ImageSourceConfig.ManagedSet] schedule keeps its setId,
 * so it stays populated if restoring onto the same install, but is empty on a fresh install
 * until photos are re-imported (its files were never part of the backup).
 */
object BackupSerializer {

    private const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class Backup(
        val version: Int = CURRENT_VERSION,
        val schedules: List<ScheduleDto> = emptyList(),
        val themeMode: String = ThemeMode.SYSTEM.name,
        val customAccentArgb: Int? = null,
    )

    @Serializable
    data class ScheduleDto(
        val id: String,
        val label: String,
        val targets: List<String>,
        val trigger: TriggerDto,
        val source: SourceDto,
        val shuffleEnabled: Boolean,
        val sortOrder: String,
        val fitMode: String,
    )

    @Serializable
    sealed interface TriggerDto {
        @Serializable
        @SerialName("interval")
        data class Interval(val everyMillis: Long) : TriggerDto

        @Serializable
        @SerialName("times_of_day")
        data class TimesOfDay(val times: List<String>, val daysOfWeek: List<String>) : TriggerDto
    }

    @Serializable
    sealed interface SourceDto {
        @Serializable
        @SerialName("linked_folder")
        data class LinkedFolder(val treeUri: String) : SourceDto

        @Serializable
        @SerialName("managed_set")
        data class ManagedSet(val setId: String) : SourceDto
    }

    data class RestoredData(
        val schedules: List<Schedule>,
        val themeMode: ThemeMode,
        val customAccentArgb: Int?,
    )

    /**
     * Restored schedules are always emitted with enabled = false and anchoredAt = Instant.EPOCH -
     * a restore is a bulk replace, and re-enabling one at a time through the normal enable flow
     * is what re-establishes valid alarms and TargetArbiter's single-owner-per-target invariant
     * (several schedules could have been enabled with overlapping targets at backup time on a
     * now-gone install state, which must not be trusted blindly on restore).
     */
    fun serialize(schedules: List<Schedule>, themeMode: ThemeMode, customAccentArgb: Int?): String {
        val backup = Backup(
            schedules = schedules.map { it.toDto() },
            themeMode = themeMode.name,
            customAccentArgb = customAccentArgb,
        )
        return json.encodeToString(backup)
    }

    fun deserialize(content: String): Result<RestoredData> = runCatching {
        val backup = json.decodeFromString<Backup>(content)
        RestoredData(
            schedules = backup.schedules.map { it.toDomain() },
            themeMode = runCatching { ThemeMode.valueOf(backup.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
            customAccentArgb = backup.customAccentArgb,
        )
    }

    private fun Schedule.toDto(): ScheduleDto = ScheduleDto(
        id = id,
        label = label,
        targets = targets.map { it.name },
        trigger = when (val t = trigger) {
            is Trigger.Interval -> TriggerDto.Interval(t.everyMillis)
            is Trigger.TimesOfDay -> TriggerDto.TimesOfDay(
                times = t.times.map { it.toString() },
                daysOfWeek = t.daysOfWeek.map { it.name },
            )
        },
        source = when (val s = source) {
            is ImageSourceConfig.LinkedFolder -> SourceDto.LinkedFolder(s.treeUri)
            is ImageSourceConfig.ManagedSet -> SourceDto.ManagedSet(s.setId)
        },
        shuffleEnabled = shuffleEnabled,
        sortOrder = sortOrder.name,
        fitMode = fitMode.name,
    )

    private fun ScheduleDto.toDomain(): Schedule = Schedule(
        id = id,
        enabled = false,
        targets = targets.mapNotNull { runCatching { ScreenTarget.valueOf(it) }.getOrNull() }.toSet(),
        label = label,
        trigger = when (val t = trigger) {
            is TriggerDto.Interval -> Trigger.Interval(t.everyMillis)
            is TriggerDto.TimesOfDay -> Trigger.TimesOfDay(
                times = t.times.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() },
                daysOfWeek = t.daysOfWeek.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
            )
        },
        source = when (val s = source) {
            is SourceDto.LinkedFolder -> ImageSourceConfig.LinkedFolder(s.treeUri)
            is SourceDto.ManagedSet -> ImageSourceConfig.ManagedSet(s.setId)
        },
        shuffleEnabled = shuffleEnabled,
        sortOrder = runCatching { SortOrder.valueOf(sortOrder) }.getOrDefault(SortOrder.NAME_ASC),
        fitMode = runCatching { FitMode.valueOf(fitMode) }.getOrDefault(FitMode.FILL),
        anchoredAt = Instant.EPOCH,
    )
}
