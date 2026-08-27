package com.ashurudra.wallpapercycler.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ashurudra.wallpapercycler.MainActivity
import com.ashurudra.wallpapercycler.R
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.prefs.WidgetPreferences
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.usecase.ApplyWallpaperUseCase
import com.ashurudra.wallpapercycler.wallpaper.WallpaperImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs every widget interaction: the prev/next buttons' own tap (which advances the matched
 * schedule's cycle exactly like the main list's per-card buttons, via the same
 * [ApplyWallpaperUseCase]), and the plain refresh-only path used everywhere else a widget's
 * bound schedule could have changed ([WidgetRefresher]). Always rebuilds every placed widget
 * afterward, not just the one that was tapped, since two widget instances can resolve to the
 * same schedule and both need to show the result.
 */
class WidgetActionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val action = inputData.getString(KEY_ACTION) ?: ACTION_UPDATE
        val targetAppWidgetId = inputData.getInt(KEY_TARGET_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val allIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, CyclerWidgetProvider::class.java))
        val widgetPreferences = WidgetPreferences(applicationContext)
        val applyWallpaperUseCase = ApplyWallpaperUseCase(applicationContext)
        val schedules = AppDatabase.getInstance(applicationContext).scheduleDao().getAll().map { it.toDomain() }

        if (action != ACTION_UPDATE && targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val scope = widgetPreferences.getScope(targetAppWidgetId)
            val matched = scope?.let { s -> schedules.find { it.enabled && s.matches(it.targets) } }
            if (matched != null) {
                when (action) {
                    ACTION_NEXT -> applyWallpaperUseCase.applyNext(matched.id)
                    ACTION_PREVIOUS -> applyWallpaperUseCase.applyPrevious(matched.id)
                }
            }
        }

        for (appWidgetId in allIds) {
            val scope = widgetPreferences.getScope(appWidgetId) ?: continue
            val matched = schedules.find { it.enabled && scope.matches(it.targets) }
            val views = buildRemoteViews(applicationContext, appWidgetId, matched, applyWallpaperUseCase)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        Result.success()
    }

    private suspend fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        schedule: Schedule?,
        applyWallpaperUseCase: ApplyWallpaperUseCase,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cycler)

        val bitmap = schedule?.let {
            val peek = applyWallpaperUseCase.peek(it.id)
            peek.current?.uri?.let { uri ->
                runCatching {
                    WallpaperImageDecoder.decode(context.contentResolver, uri, THUMBNAIL_SIZE, THUMBNAIL_SIZE, FitMode.FILL)
                }.getOrNull()
            }
        }

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_image, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_empty_text,
                if (schedule == null) "No active schedule for this widget" else "No images found",
            )
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context, appWidgetId))
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            actionPendingIntent(context, CyclerWidgetProvider.ACTION_PREVIOUS, appWidgetId),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            actionPendingIntent(context, CyclerWidgetProvider.ACTION_NEXT, appWidgetId),
        )

        return views
    }

    private fun openAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** [action] alone (distinct for prev vs. next) keeps this PendingIntent distinct from the
     * other action on the same widget; [appWidgetId] as the request code keeps it distinct
     * across different widget instances for the same action. */
    private fun actionPendingIntent(context: Context, action: String, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, CyclerWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_TARGET_APP_WIDGET_ID = "target_app_widget_id"
        const val ACTION_UPDATE = "update"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        private const val THUMBNAIL_SIZE = 300
    }
}
