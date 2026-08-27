package com.ashurudra.wallpapercycler.domain.usecase

import android.content.Context
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes a schedule and its cycle state. When the schedule owns a gallery-imported managed
 * set, the caller decides (via [deleteManagedSetFiles]) whether the copied files are deleted
 * too - this use case never prompts, that's the UI's job (a confirm dialog before passing
 * true).
 */
class DeleteScheduleUseCase(private val context: Context) {

    private val database = AppDatabase.getInstance(context)

    suspend fun delete(scheduleId: String, deleteManagedSetFiles: Boolean) {
        val entity = database.scheduleDao().getById(scheduleId) ?: return

        try {
            AlarmScheduler(context).cancel(scheduleId)
        } catch (e: Exception) {
            // Cancelling a non-existent or unpermitted alarm must not block the delete.
        }

        database.scheduleDao().deleteById(scheduleId)
        database.cycleDao().deleteByScheduleId(scheduleId)

        val source = entity.toDomain().source
        if (source is ImageSourceConfig.ManagedSet && deleteManagedSetFiles) {
            withContext(Dispatchers.IO) {
                File(context.filesDir, "sets/${source.setId}").deleteRecursively()
            }
        }
    }
}
