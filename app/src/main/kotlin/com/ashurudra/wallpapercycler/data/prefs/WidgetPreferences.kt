package com.ashurudra.wallpapercycler.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.ashurudra.wallpapercycler.widget.WidgetScope

/**
 * Per-widget-instance scope, keyed by App Widget ID. Plain SharedPreferences rather than
 * DataStore - this is small, synchronous, keyed data (one enum per placed widget) read from a
 * WorkManager worker on every widget action, never observed as a Flow anywhere.
 */
class WidgetPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setScope(appWidgetId: Int, scope: WidgetScope) {
        prefs.edit { putString(keyFor(appWidgetId), scope.name) }
    }

    /** Null for a never-configured widget id, or one whose stored value no longer parses. */
    fun getScope(appWidgetId: Int): WidgetScope? =
        prefs.getString(keyFor(appWidgetId), null)?.let { raw -> runCatching { WidgetScope.valueOf(raw) }.getOrNull() }

    fun remove(appWidgetId: Int) {
        prefs.edit { remove(keyFor(appWidgetId)) }
    }

    private fun keyFor(appWidgetId: Int) = "scope_$appWidgetId"

    private companion object {
        const val PREFS_NAME = "widget_prefs"
    }
}
