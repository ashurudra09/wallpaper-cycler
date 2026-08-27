package com.ashurudra.wallpapercycler.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ashurudra.wallpapercycler.data.prefs.WidgetPreferences

/**
 * Registers for system widget lifecycle broadcasts and the custom prev/next actions fired by
 * the widget's own buttons. All actual work (DB reads, bitmap decode, RemoteViews building) is
 * off-loaded to [WidgetActionWorker] - AppWidgetProvider callbacks run with a short execution
 * window, same reasoning as [com.ashurudra.wallpapercycler.scheduler.WallpaperAlarmReceiver].
 */
class CyclerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        enqueue(context, WidgetActionWorker.ACTION_UPDATE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val widgetPreferences = WidgetPreferences(context)
        appWidgetIds.forEach { widgetPreferences.remove(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = when (intent.action) {
            ACTION_NEXT -> WidgetActionWorker.ACTION_NEXT
            ACTION_PREVIOUS -> WidgetActionWorker.ACTION_PREVIOUS
            else -> return
        }
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        enqueue(context, action, targetAppWidgetId = appWidgetId)
    }

    private fun enqueue(context: Context, action: String, targetAppWidgetId: Int? = null) {
        val data = workDataOf(
            WidgetActionWorker.KEY_ACTION to action,
            WidgetActionWorker.KEY_TARGET_APP_WIDGET_ID to (targetAppWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID),
        )
        val request = OneTimeWorkRequestBuilder<WidgetActionWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_action_${targetAppWidgetId ?: "all"}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION_NEXT = "com.ashurudra.wallpapercycler.widget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.ashurudra.wallpapercycler.widget.ACTION_PREVIOUS"
    }
}
