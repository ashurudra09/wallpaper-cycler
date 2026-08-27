package com.ashurudra.wallpapercycler.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** ARGB color int, or null to fall back to the fixed light/dark palette accents. */
    val customAccent: Flow<Int?> = context.settingsDataStore.data.map { prefs -> prefs[Keys.CUSTOM_ACCENT] }

    suspend fun setCustomAccent(colorArgb: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (colorArgb == null) prefs.remove(Keys.CUSTOM_ACCENT) else prefs[Keys.CUSTOM_ACCENT] = colorArgb
        }
    }

    val onboardingComplete: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete() {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
    }
}
