package com.ashurudra.wallpapercycler.ui.editor

import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.Trigger
import java.time.Instant

/** Default cadence for a brand-new schedule - a reasonable middle-ground interval. */
const val DEFAULT_INTERVAL_MILLIS = 30 * 60 * 1000L

/**
 * The editor's own working copy of a schedule - deliberately NOT the domain [Schedule][com.ashurudra.wallpapercycler.domain.model.Schedule]
 * model. A schedule being created has no image source yet, which the real Schedule model
 * can't represent (it always carries a concrete, non-null [ImageSourceConfig]). [source] here
 * stays null until the user links a folder or imports at least one gallery photo, and [save]
 * requires it to be non-null (with at least one photo, for a managed set) before persisting.
 *
 * [id] doubles as the managed-set directory id ("sets/<id>/") per the plan's 1:1 rule between
 * a schedule and its gallery set - stable for the whole editing session so photos can be
 * imported into it before the schedule itself is ever saved.
 */
data class EditorUiState(
    val id: String,
    val isNewSchedule: Boolean,
    val isLoading: Boolean = true,
    val label: String = "",
    val targets: Set<ScreenTarget> = emptySet(),
    val trigger: Trigger = Trigger.Interval(everyMillis = DEFAULT_INTERVAL_MILLIS),
    val source: ImageSourceConfig? = null,
    val shuffleEnabled: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val fitMode: FitMode = FitMode.FILL,
    // Preserved across an edit so Save doesn't silently flip an already-enabled schedule off -
    // SaveScheduleUseCase force-disables genuinely new schedules regardless.
    val existingEnabled: Boolean = false,
    val anchoredAt: Instant = Instant.EPOCH,
    val validationError: String? = null,
)
