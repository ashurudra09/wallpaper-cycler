package com.ashurudra.wallpapercycler.ui.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-registered (not dynamic) so it still fires and records the result even if the
 * app process was killed while the phone was idle overnight — the actual thing Phase 0
 * needs to prove.
 */
class DiagnosticsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "unknown"
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
        val firedAt = System.currentTimeMillis()

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_LABEL, label)
            .putLong(KEY_LAST_SCHEDULED_AT, scheduledAt)
            .putLong(KEY_LAST_FIRED_AT, firedAt)
            .apply()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
        const val PREFS_NAME = "diagnostics_prefs"
        const val KEY_LAST_LABEL = "last_label"
        const val KEY_LAST_SCHEDULED_AT = "last_scheduled_at"
        const val KEY_LAST_FIRED_AT = "last_fired_at"
    }
}
