package com.ashurudra.wallpapercycler.domain.usecase

import android.content.Context
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.db.toEntity
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.schedule.nextTriggerAt
import com.ashurudra.wallpapercycler.domain.target.TargetArbiter
import com.ashurudra.wallpapercycler.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Creates/edits a schedule. A brand-new schedule is always forced to enabled = false - it
 * must instead be enabled afterwards through [ToggleScheduleUseCase.enable] from the schedule
 * list, so [com.ashurudra.wallpapercycler.domain.target.TargetArbiter]'s conflict rule is
 * never bypassed by a save that flips a switch on directly.
 */
class SaveScheduleUseCase(private val context: Context) {

    private val database = AppDatabase.getInstance(context)

    sealed interface SaveResult {
        data object Saved : SaveResult
        data class Rejected(val reason: String) : SaveResult
    }

    suspend fun save(schedule: Schedule): SaveResult {
        val existing = database.scheduleDao().getById(schedule.id)
        val isNew = existing == null
        val toSave = if (isNew) schedule.copy(enabled = false) else schedule

        if (!isNew && toSave.enabled) {
            // Editing an already-enabled schedule can leave its trigger unsatisfiable (e.g. all
            // days unchecked) - reject up front, exactly like ToggleScheduleUseCase.enable does,
            // rather than writing enabled = true and letting AlarmScheduler.scheduleNext()
            // silently cancel the alarm with no user-visible signal.
            val triggerAt = nextTriggerAt(toSave.trigger, toSave.anchoredAt, Instant.now(), ZoneId.systemDefault())
            if (triggerAt == null) {
                return SaveResult.Rejected(
                    "This schedule has no valid trigger - add a time or turn on at least one day",
                )
            }
        }

        if (!isNew && toSave.enabled) {
            // Re-apply the single-owner-per-target conflict rule: editing an already-enabled
            // schedule's targets can newly overlap another enabled schedule's targets, which
            // must be resolved exactly like ToggleScheduleUseCase.enable does - a plain upsert
            // here would silently let two enabled schedules own the same target.
            val allSchedules = database.scheduleDao().observeAll().first().map { it.toDomain() }
                .map { if (it.id == toSave.id) toSave else it }
            val previouslyEnabledIds = allSchedules.filter { it.enabled }.map { it.id }.toSet()
            val resolved = TargetArbiter.resolveEnable(allSchedules, toSave.id)

            database.scheduleDao().upsertAll(resolved.map { it.toEntity() })

            val nowDisabledIds = resolved.filter { !it.enabled }.map { it.id }.toSet()
            val justDisabledIds = (previouslyEnabledIds - toSave.id) intersect nowDisabledIds
            for (id in justDisabledIds) {
                try {
                    AlarmScheduler(context).cancel(id)
                } catch (e: SecurityException) {
                    // Cancelling doesn't require the exact-alarm permission, but guard anyway.
                }
            }
        } else {
            database.scheduleDao().upsert(toSave.toEntity())
        }

        if (!isNew && toSave.enabled) {
            val existingDomain = existing!!.toDomain()
            val sourceOrModeChanged = existingDomain.source != toSave.source ||
                existingDomain.shuffleEnabled != toSave.shuffleEnabled ||
                existingDomain.sortOrder != toSave.sortOrder
            if (sourceOrModeChanged) {
                database.cycleDao().deleteByScheduleId(toSave.id)
            }
        }

        // A brand-new schedule is never enabled (see above), so there is nothing to (re-)arm
        // an alarm for yet.
        if (!isNew && toSave.enabled) {
            try {
                AlarmScheduler(context).scheduleNext(toSave)
            } catch (e: SecurityException) {
                // Exact-alarm permission missing - the schedule's trigger is still saved;
                // the user is guided to grant it via the onboarding/diagnostics screens.
            }
        }

        return SaveResult.Saved
    }

    fun newScheduleId(): String = UUID.randomUUID().toString()
}
