package com.ashurudra.wallpapercycler.domain.model

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
)
