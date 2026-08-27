package com.ashurudra.wallpapercycler.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashurudra.wallpapercycler.data.db.AppDatabase
import com.ashurudra.wallpapercycler.data.db.toDomain
import com.ashurudra.wallpapercycler.data.source.ImportResult
import com.ashurudra.wallpapercycler.data.source.MediaImporter
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.Schedule
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import com.ashurudra.wallpapercycler.domain.model.Trigger
import com.ashurudra.wallpapercycler.domain.usecase.SaveScheduleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Backs [ScheduleEditorScreen]. Holds one working copy of a schedule being created or edited -
 * nothing here is persisted until [save] succeeds.
 *
 * [scheduleId] null means a brand-new schedule: a stable id is generated once (via
 * [SaveScheduleUseCase.newScheduleId]) and held for the whole editing session, since gallery
 * photos need a managed-set directory to import into before the schedule itself is ever saved.
 * Non-null means loading an existing schedule by that id.
 */
class EditorViewModel(
    private val appContext: Context,
    scheduleId: String?,
) : ViewModel() {

    private val database = AppDatabase.getInstance(appContext)
    private val mediaImporter = MediaImporter(appContext)
    private val saveScheduleUseCase = SaveScheduleUseCase(appContext)

    /** Also doubles as this schedule's managed-set directory id - see [EditorUiState]. */
    private val stableId = scheduleId ?: saveScheduleUseCase.newScheduleId()

    private val _uiState = MutableStateFlow(
        EditorUiState(
            id = stableId,
            isNewSchedule = scheduleId == null,
            isLoading = scheduleId != null,
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _setPhotos = MutableStateFlow<List<File>>(emptyList())
    val setPhotos: StateFlow<List<File>> = _setPhotos.asStateFlow()

    init {
        if (scheduleId != null) {
            viewModelScope.launch {
                val entity = database.scheduleDao().getById(scheduleId)
                if (entity != null) {
                    val schedule = entity.toDomain()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            label = schedule.label,
                            targets = schedule.targets,
                            trigger = schedule.trigger,
                            source = schedule.source,
                            shuffleEnabled = schedule.shuffleEnabled,
                            sortOrder = schedule.sortOrder,
                            fitMode = schedule.fitMode,
                            existingEnabled = schedule.enabled,
                            anchoredAt = schedule.anchoredAt,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                refreshSetPhotos()
            }
        } else {
            viewModelScope.launch { refreshSetPhotos() }
        }
    }

    fun setLabel(text: String) = _uiState.update { it.copy(label = text) }

    fun setTargets(targets: Set<ScreenTarget>) =
        _uiState.update { it.copy(targets = targets, validationError = null) }

    fun setShuffleEnabled(enabled: Boolean) = _uiState.update { it.copy(shuffleEnabled = enabled) }

    fun setSortOrder(sortOrder: SortOrder) = _uiState.update { it.copy(sortOrder = sortOrder) }

    fun setFitMode(fitMode: FitMode) = _uiState.update { it.copy(fitMode = fitMode) }

    fun setTriggerInterval(everyMillis: Long) =
        _uiState.update { it.copy(trigger = Trigger.Interval(everyMillis = everyMillis)) }

    fun setTriggerTimesOfDay(times: List<LocalTime>, days: Set<DayOfWeek>) =
        _uiState.update { it.copy(trigger = Trigger.TimesOfDay(times = times, daysOfWeek = days)) }

    fun setSourceLinkedFolder(treeUri: String) = _uiState.update {
        it.copy(source = ImageSourceConfig.LinkedFolder(treeUri = treeUri), validationError = null)
    }

    /** The managed set itself is populated separately via [importPhotos]. */
    fun setSourceManagedSet() = _uiState.update {
        it.copy(source = ImageSourceConfig.ManagedSet(setId = stableId), validationError = null)
    }

    suspend fun importPhotos(uris: List<Uri>): ImportResult {
        val result = mediaImporter.importInto(setId = stableId, uris = uris)
        refreshSetPhotos()
        return result
    }

    /** Re-lists the managed-set directory from disk; safe (empty) if it doesn't exist yet. */
    fun currentSetPhotos(): List<File> =
        File(appContext.filesDir, "sets/$stableId").listFiles()?.toList().orEmpty()

    suspend fun removeSetPhotos(fileNames: List<String>) {
        withContext(Dispatchers.IO) {
            val setDir = File(appContext.filesDir, "sets/$stableId")
            fileNames.forEach { name -> File(setDir, name).delete() }
        }
        refreshSetPhotos()
    }

    /**
     * Deletes every photo currently in this schedule's managed set - used when the user
     * switches the source away from "gallery" to "linked folder", orphaning the copies.
     */
    suspend fun discardManagedSetPhotos() {
        removeSetPhotos(currentSetPhotos().map { it.name })
    }

    /**
     * Reclaims a brand-new schedule's managed-set directory when the user abandons the editor
     * (back instead of Save) - MediaImporter copies gallery photos into sets/$stableId eagerly,
     * before Save is ever pressed, and since no schedule row gets written for an abandoned
     * schedule, no DeleteScheduleUseCase call will ever clean that directory up. A no-op for an
     * existing schedule being edited, whose managed-set directory (if any) is its live data.
     */
    fun discardIfAbandoned() {
        if (!_uiState.value.isNewSchedule) return
        viewModelScope.launch(Dispatchers.IO) {
            File(appContext.filesDir, "sets/$stableId").deleteRecursively()
        }
    }

    private suspend fun refreshSetPhotos() {
        _setPhotos.value = withContext(Dispatchers.IO) { currentSetPhotos() }
    }

    /**
     * Validates only what blocks persistence outright - at least one target, and a configured
     * source (a non-blank linked folder, or a managed set with at least one imported photo).
     * Trigger validity (interval bounds, or an empty times-of-day list) is deliberately NOT
     * checked here: an unsatisfiable trigger is allowed to be saved per the plan, it just can't
     * later be enabled - that gate belongs to the schedule-list enable flow, not this screen.
     */
    suspend fun save(): Boolean {
        val state = _uiState.value

        if (state.targets.isEmpty()) {
            _uiState.update { it.copy(validationError = "Select at least one target: home or lock.") }
            return false
        }

        val source = state.source
        val sourceConfigured = when (source) {
            is ImageSourceConfig.LinkedFolder -> source.treeUri.isNotBlank()
            is ImageSourceConfig.ManagedSet -> withContext(Dispatchers.IO) {
                File(appContext.filesDir, "sets/${source.setId}").listFiles()?.isNotEmpty() == true
            }
            null -> false
        }
        if (!sourceConfigured || source == null) {
            _uiState.update {
                it.copy(validationError = "Link a folder or add at least one photo before saving.")
            }
            return false
        }

        val schedule = Schedule(
            id = state.id,
            enabled = state.existingEnabled,
            targets = state.targets,
            label = state.label,
            trigger = state.trigger,
            source = source,
            shuffleEnabled = state.shuffleEnabled,
            sortOrder = state.sortOrder,
            fitMode = state.fitMode,
            anchoredAt = state.anchoredAt,
        )
        return when (val result = saveScheduleUseCase.save(schedule)) {
            is SaveScheduleUseCase.SaveResult.Rejected -> {
                _uiState.update { it.copy(validationError = result.reason) }
                false
            }
            SaveScheduleUseCase.SaveResult.Saved -> true
        }
    }

    companion object {
        fun factory(context: Context, scheduleId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(context.applicationContext, scheduleId) }
        }
    }
}
