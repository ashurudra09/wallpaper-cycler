package com.ashurudra.wallpapercycler.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashurudra.wallpapercycler.data.backup.BackupSerializer
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.db.toEntity
import com.ashurudra.wallpapercycler.data.prefs.SettingsRepository
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customAccentArgb: Int? = null,
)

sealed interface RestoreOutcome {
    data class Success(val scheduleCount: Int, val linkedFoldersToRelink: Int) : RestoreOutcome
    data class Failed(val reason: String) : RestoreOutcome
}

/** Backs [SettingsScreen]: theme mode, custom accent, and JSON backup/restore. */
class SettingsViewModel(private val context: Context) : ViewModel() {

    private val settingsRepository = SettingsRepository(context)
    private val database = AppDatabase.getInstance(context)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.customAccent,
    ) { themeMode, customAccent -> SettingsUiState(themeMode, customAccent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCustomAccent(argb: Int?) {
        viewModelScope.launch { settingsRepository.setCustomAccent(argb) }
    }

    /** Writes the full backup JSON to [uri] (from a CreateDocument picker result). */
    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val schedules = database.scheduleDao().observeAll().first().map { it.toDomain() }
            val json = BackupSerializer.serialize(schedules, state.themeMode, state.customAccentArgb)
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("could not open output stream")
                }
            }.isSuccess
            onDone(ok)
        }
    }

    /**
     * Reads and applies a backup JSON from [uri] (from an OpenDocument picker result). This is a
     * full replace, not a merge - every current schedule and its cycle state is deleted first.
     * Restored schedules always come back disabled (see [BackupSerializer]'s kdoc for why); the
     * caller is told how many owned a linked folder so it can explain that those need re-linking.
     */
    fun restoreBackup(uri: Uri, onDone: (RestoreOutcome) -> Unit) {
        viewModelScope.launch {
            val content = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("could not open input stream")
                }
            }.getOrElse {
                onDone(RestoreOutcome.Failed(it.message ?: "could not read the selected file"))
                return@launch
            }

            val restored = BackupSerializer.deserialize(content).getOrElse {
                onDone(RestoreOutcome.Failed("This file isn't a valid backup."))
                return@launch
            }

            database.scheduleDao().deleteAll()
            database.cycleDao().deleteAll()
            database.scheduleDao().upsertAll(restored.schedules.map { it.toEntity() })
            settingsRepository.setThemeMode(restored.themeMode)
            settingsRepository.setCustomAccent(restored.customAccentArgb)

            val linkedFolderCount = restored.schedules.count { it.source is ImageSourceConfig.LinkedFolder }
            onDone(RestoreOutcome.Success(restored.schedules.size, linkedFolderCount))
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(context.applicationContext) }
        }
    }
}
