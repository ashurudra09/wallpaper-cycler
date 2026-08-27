package com.ashurudra.wallpapercycler.domain.usecase

import android.content.Context
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.db.toEntity
import com.ashurudra.wallpapercycler.domain.schedule.nextTriggerAt
import com.ashurudra.wallpapercycler.domain.target.TargetArbiter
import com.ashurudra.wallpapercycler.scheduler.AlarmScheduler
import com.ashurudra.wallpapercycler.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Flips a schedule's enabled switch. Enabling re-anchors the schedule to "now" (per the plan:
 * the cadence starts counting from the moment the switch flips on), applies the plan's
 * single-owner-per-target conflict rule via [TargetArbiter], arms/re-arms the underlying
 * alarm(s), and applies a wallpaper immediately so the effect is visible right away. Disabling
 * only cancels the alarm - the currently-applied wallpaper is deliberately left in place.
 */
class ToggleScheduleUseCase(private val context: Context) {

    private val database = AppDatabase.getInstance(context)

    sealed interface ToggleResult {
        data object Enabled : ToggleResult
        data class Rejected(val reason: String) : ToggleResult
        data object Disabled : ToggleResult
    }

    suspend fun enable(scheduleId: String): ToggleResult {
        val allSchedules = database.scheduleDao().observeAll().first().map { it.toDomain() }
        val target = allSchedules.find { it.id == scheduleId }
            ?: return ToggleResult.Rejected("schedule not found")

        // Using Instant.now() as both anchoredAt and from mirrors the re-anchor we are about
        // to perform below - if the trigger can't ever fire from "now", it can't ever fire
        // from the anchor we're about to give it either.
        val now = Instant.now()
        val triggerAt = nextTriggerAt(target.trigger, now, now, ZoneId.systemDefault())
        if (triggerAt == null) {
            return ToggleResult.Rejected(
                "This schedule has no valid trigger - add a time or turn on at least one day",
            )
        }

        val previouslyEnabledIds = allSchedules.filter { it.enabled }.map { it.id }.toSet()

        val resolved = TargetArbiter.resolveEnable(allSchedules, scheduleId)
        val updatedTarget = resolved.first { it.id == scheduleId }.copy(anchoredAt = now)
        val finalList = resolved.map { if (it.id == scheduleId) updatedTarget else it }

        database.scheduleDao().upsertAll(finalList.map { it.toEntity() })

        // Any other schedule TargetArbiter just flipped enabled -> disabled needs its alarm
        // cancelled; one merely downgraded in targets keeps its trigger/anchoredAt untouched,
        // so its alarm needs no change.
        val nowDisabledIds = finalList.filter { !it.enabled }.map { it.id }.toSet()
        val justDisabledIds = (previouslyEnabledIds - scheduleId) intersect nowDisabledIds

        try {
            AlarmScheduler(context).scheduleNext(updatedTarget)
        } catch (e: SecurityException) {
            // Exact-alarm permission missing - the schedule still stays enabled in the DB;
            // the user is guided to grant it via the onboarding/diagnostics screens.
        }
        for (id in justDisabledIds) {
            try {
                AlarmScheduler(context).cancel(id)
            } catch (e: SecurityException) {
                // Cancelling doesn't require the exact-alarm permission, but guard anyway.
            }
        }

        try {
            ApplyWallpaperUseCase(context).applyNext(scheduleId)
        } catch (e: Exception) {
            // A failed immediate-apply must not block the enable action or leave the schedule
            // un-enabled - ApplyWallpaperUseCase's own failure-counting path already covers
            // persistent failures (including auto-disable after repeated ones).
        }

        return ToggleResult.Enabled
    }

    suspend fun disable(scheduleId: String): ToggleResult {
        val entity = database.scheduleDao().getById(scheduleId)
            ?: return ToggleResult.Rejected("schedule not found")

        database.scheduleDao().upsert(entity.copy(enabled = false))
        try {
            AlarmScheduler(context).cancel(scheduleId)
        } catch (e: SecurityException) {
            // Cancelling doesn't require the exact-alarm permission, but guard anyway.
        }
        // enable() reaches WidgetRefresher via applyNext(); disabling never calls that, so a
        // widget bound to this schedule's scope needs its own explicit nudge to drop to the
        // empty state instead of showing a wallpaper that's no longer this schedule's to show.
        WidgetRefresher.requestUpdateAll(context)
        return ToggleResult.Disabled
    }
}
