package com.ashurudra.wallpapercycler.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Fired by AlarmManager when a schedule's alarm goes off. Hands the tick to WorkManager so the
 * actual (potentially slow, IO-bound) wallpaper-apply work runs off the broadcast dispatch
 * thread. enqueueUniqueWork is a fast, synchronous call, so goAsync() is not needed here.
 */
class WallpaperAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULE_ID) ?: return

        val request = OneTimeWorkRequestBuilder<ApplyWallpaperWorker>()
            .setInputData(workDataOf(ApplyWallpaperWorker.KEY_SCHEDULE_ID to scheduleId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "apply_wallpaper_$scheduleId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
