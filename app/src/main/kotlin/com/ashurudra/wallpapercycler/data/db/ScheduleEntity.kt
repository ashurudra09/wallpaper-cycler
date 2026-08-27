package com.ashurudra.wallpapercycler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.Trigger

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val label: String,
    val enabled: Boolean,
    val targets: Set<ScreenTarget>,
    val trigger: Trigger,
    val source: ImageSourceConfig,
    val shuffleEnabled: Boolean,
    val sortOrder: SortOrder,
    val fitMode: FitMode,
)

fun ScheduleEntity.toDomain(): Schedule = Schedule(
    id = id,
    enabled = enabled,
    targets = targets,
    label = label,
    trigger = trigger,
    source = source,
    shuffleEnabled = shuffleEnabled,
    sortOrder = sortOrder,
    fitMode = fitMode,
)

fun Schedule.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    label = label,
    enabled = enabled,
    targets = targets,
    trigger = trigger,
    source = source,
    shuffleEnabled = shuffleEnabled,
    sortOrder = sortOrder,
    fitMode = fitMode,
)
