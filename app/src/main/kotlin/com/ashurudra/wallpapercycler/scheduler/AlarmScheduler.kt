package com.ashurudra.wallpapercycler.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.schedule.nextTriggerAt
import java.time.Instant
import java.time.ZoneId

/**
 * Wraps AlarmManager for the wallpaper-cycling schedules. Owns arming (scheduleNext) and
 * disarming (cancel) of the exact alarm backing a single [Schedule]. Rescheduling after an
 * automatic tick is done exclusively by ApplyWallpaperWorker - never call scheduleNext from
 * manual next/previous flows, or the alarm countdown would reset on every manual action.
 */
class AlarmScheduler(private val context: Context) {

    fun scheduleNext(schedule: Schedule) {
        val triggerAt = nextTriggerAt(schedule.trigger, schedule.anchoredAt, Instant.now(), ZoneId.systemDefault())
        if (triggerAt == null) {
            cancel(schedule.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(schedule.id)
        // SecurityException (exact-alarm permission not granted) is intentionally left
        // uncaught here - callers are responsible for handling it.
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
    }

    fun cancel(scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(scheduleId))
    }

    private fun pendingIntentFor(scheduleId: String): PendingIntent {
        val intent = Intent(context, WallpaperAlarmReceiver::class.java).apply {
            // PendingIntent identity/matching is based on the Intent (action, data, component,
            // etc.) plus the request code - NOT the extras. Encoding the schedule id into the
            // data Uri guarantees two different schedules never resolve to the same
            // PendingIntent, even if their (32-bit) hashCode()s collide.
            data = Uri.fromParts("schedule", scheduleId, null)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
