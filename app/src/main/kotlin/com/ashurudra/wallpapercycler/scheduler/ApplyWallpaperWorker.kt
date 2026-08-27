package com.ashurudra.wallpapercycler.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.domain.usecase.ApplyWallpaperUseCase

/**
 * Runs one automatic wallpaper-cycle tick for a schedule, then re-arms (or cancels) that
 * schedule's alarm. This is the ONLY place in the app that reschedules the next alarm after a
 * tick - manual next/previous flows call ApplyWallpaperUseCase directly and must never touch
 * AlarmScheduler, or the countdown to the next automatic tick would reset.
 *
 * Per the "no catch-up" design, this always returns success: if the tick failed, the failure is
 * already tracked by the use case (consecutive-failure counting / auto-disable), and the next
 * natural alarm is still armed below - there is nothing for WorkManager retry semantics to add.
 */
class ApplyWallpaperWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.failure()

        val result = ApplyWallpaperUseCase(applicationContext).applyNext(scheduleId)

        val scheduleEntity = AppDatabase.getInstance(applicationContext).scheduleDao().getById(scheduleId)
        if (scheduleEntity == null) {
            return Result.success()
        }
        val schedule = scheduleEntity.toDomain()

        try {
            if (schedule.enabled && !result.autoDisabled) {
                AlarmScheduler(applicationContext).scheduleNext(schedule)
            } else {
                AlarmScheduler(applicationContext).cancel(scheduleId)
            }
        } catch (_: SecurityException) {
            // Exact-alarm permission can be revoked between ticks; skip silently rather than
            // crashing the worker - there is nothing more we can do about it from here.
        }

        return Result.success()
    }

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
    }
}
