package com.ashurudra.wallpapercycler.di

import android.content.Context
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.prefs.SettingsRepository
import com.ashurudra.wallpapercycler.data.source.MediaImporter
import com.ashurudra.wallpapercycler.data.source.UriPermissionManager

/**
 * Manual service locator. Grows with each phase as repositories (Room, DataStore,
 * image sources) are introduced — no DI framework, per the project's tech-stack decision.
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val uriPermissionManager: UriPermissionManager by lazy { UriPermissionManager(appContext) }
    val mediaImporter: MediaImporter by lazy { MediaImporter(appContext) }
}
