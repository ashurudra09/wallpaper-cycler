package com.ashurudra.wallpapercycler.domain.model

import java.time.Instant

data class Schedule(
    val id: String,
    val enabled: Boolean,
    val targets: Set<ScreenTarget>,
    val label: String = "",
    val trigger: Trigger = Trigger.Interval(everyMillis = 0L),
    val source: ImageSourceConfig = ImageSourceConfig.ManagedSet(setId = ""),
    val shuffleEnabled: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val fitMode: FitMode = FitMode.FILL,
    // The moment this schedule was (last) enabled - interval triggers anchor to this,
    // not to wall-clock, so re-enabling restarts the countdown from here.
    val anchoredAt: Instant = Instant.EPOCH,
)
