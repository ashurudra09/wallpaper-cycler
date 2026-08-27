package com.ashurudra.wallpapercycler.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
 * bound schedule could have changed ([WidgetRefresher]). A tap only applies the wallpaper here;
 * it deliberately does NOT also rebuild RemoteViews in the same run; applyNext/applyPrevious
 * already enqueue a follow-up [WidgetRefresher] update, so rebuilding here too would render
 * every widget twice - once now, once moments later - and, since two rebuilds are always
 * separated by an extra unrequested call to [ApplyWallpaperUseCase.peek], the second rebuild can
 * land on a different simulated shuffle look-ahead than the first, showing a visibly different
 * "next" thumbnail for the same tap.
 */
class WidgetActionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val action = inputData.getString(KEY_ACTION) ?: ACTION_UPDATE
        val targetAppWidgetId = inputData.getInt(KEY_TARGET_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (action != ACTION_UPDATE && targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val widgetPreferences = WidgetPreferences(applicationContext)
            val applyWallpaperUseCase = ApplyWallpaperUseCase(applicationContext)
            val schedules = AppDatabase.getInstance(applicationContext).scheduleDao().getAll().map { it.toDomain() }
            val scope = widgetPreferences.getScope(targetAppWidgetId)
            val matched = scope?.let { s -> schedules.find { it.enabled && s.matches(it.targets) } }
            if (matched != null) {
                when (action) {
                    ACTION_NEXT -> applyWallpaperUseCase.applyNext(matched.id)
                    ACTION_PREVIOUS -> applyWallpaperUseCase.applyPrevious(matched.id)
                }
            }
            return@withContext Result.success()
        }

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val allIds = appWidgetManager.getAppWidgetIds(ComponentName(applicationContext, CyclerWidgetProvider::class.java))
        val widgetPreferences = WidgetPreferences(applicationContext)
        val applyWallpaperUseCase = ApplyWallpaperUseCase(applicationContext)
        val schedules = AppDatabase.getInstance(applicationContext).scheduleDao().getAll().map { it.toDomain() }

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

        val peek = schedule?.let { applyWallpaperUseCase.peek(it.id) }
        val previousBitmap = decodePreview(context, peek?.current?.uri)
        val nextBitmap = decodePreview(context, peek?.next?.uri)

        if (previousBitmap == null && nextBitmap == null) {
            views.setViewVisibility(R.id.widget_previews, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_empty_text,
                if (schedule == null) "No active schedule for this widget" else (peek?.errorMessage ?: "No images found"),
            )
        } else {
            views.setViewVisibility(R.id.widget_previews, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            setPreview(views, R.id.widget_image_previous, previousBitmap)
            setPreview(views, R.id.widget_image_next, nextBitmap)
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

    private fun decodePreview(context: Context, uri: Uri?): Bitmap? {
        uri ?: return null
        return runCatching {
            WallpaperImageDecoder.decode(context.contentResolver, uri, THUMBNAIL_SIZE, THUMBNAIL_SIZE, FitMode.FILL)
        }.getOrNull()
    }

    private fun setPreview(views: RemoteViews, imageId: Int, bitmap: Bitmap?) {
        if (bitmap != null) {
            views.setImageViewBitmap(imageId, bitmap)
            views.setViewVisibility(imageId, View.VISIBLE)
        } else {
            views.setViewVisibility(imageId, View.INVISIBLE)
        }
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
