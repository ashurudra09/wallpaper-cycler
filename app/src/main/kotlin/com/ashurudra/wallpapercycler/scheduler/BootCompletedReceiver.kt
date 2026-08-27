package com.ashurudra.wallpapercycler.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Alarms do not survive reboot on Android, so every enabled schedule's alarm must be re-armed
 * once the system finishes booting.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = AppDatabase.getInstance(context).scheduleDao().observeAll().first()
                    .map { it.toDomain() }
                    .filter { it.enabled }

                val alarmScheduler = AlarmScheduler(context)
                for (schedule in schedules) {
                    try {
                        alarmScheduler.scheduleNext(schedule)
                    } catch (_: Exception) {
                        // Any failure re-arming this schedule (missing exact-alarm permission,
                        // an invalid trigger, etc.) should not prevent the rest of the
                        // schedules from still being re-armed.
                    }
                }
            } catch (_: Exception) {
                // Never let a boot-time failure crash the process - pendingResult.finish()
                // below still runs via the finally block regardless.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
