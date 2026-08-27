package com.ashurudra.wallpapercycler.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Enqueues a refresh of every placed widget instance. Called from anywhere a schedule's
 * enabled state or current image could have changed - [ApplyWallpaperUseCase][com.ashurudra.wallpapercycler.domain.usecase.ApplyWallpaperUseCase]'s
 * applyNext/applyPrevious (covering automatic ticks, manual next/previous, and the widget's own
 * buttons), [ToggleScheduleUseCase.disable][com.ashurudra.wallpapercycler.domain.usecase.ToggleScheduleUseCase],
 * and [DeleteScheduleUseCase][com.ashurudra.wallpapercycler.domain.usecase.DeleteScheduleUseCase] -
 * so a widget bound to that schedule's scope never shows a stale thumbnail or an outdated
 * empty state.
 */
object WidgetRefresher {
    fun requestUpdateAll(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetActionWorker>()
            .setInputData(workDataOf(WidgetActionWorker.KEY_ACTION to WidgetActionWorker.ACTION_UPDATE))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget_action_all",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
