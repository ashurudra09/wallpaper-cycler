package com.ashurudra.wallpapercycler.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.domain.model.SortOrder
import kotlinx.coroutines.launch

/**
 * Create (scheduleId == null) or edit (scheduleId != null) one schedule: label, targets,
 * image source, trigger, shuffle/sort, and fit mode. Nothing is persisted until Save succeeds -
 * see [EditorViewModel.save] for the validation rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(scheduleId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(context, scheduleId))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val setPhotos by viewModel.setPhotos.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNewSchedule) "New schedule" else "Edit schedule") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.discardIfAbandoned()
                        onDone()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = viewModel.save()
                            saving = false
                            if (ok) onDone()
                        }
                    },
                    enabled = !saving && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "Saving..." else "Save") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.height(4.dp))

            uiState.validationError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = uiState.label,
                onValueChange = viewModel::setLabel,
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Targets")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ScreenTarget.HOME in uiState.targets,
                        onClick = { viewModel.setTargets(uiState.targets.toggle(ScreenTarget.HOME)) },
                        label = { Text("Home") },
                    )
                    FilterChip(
                        selected = ScreenTarget.LOCK in uiState.targets,
                        onClick = { viewModel.setTargets(uiState.targets.toggle(ScreenTarget.LOCK)) },
                        label = { Text("Lock") },
                    )
                }
            }

            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Image source")
                SourceSection(
                    source = uiState.source,
                    setPhotos = setPhotos,
                    onLinkFolder = viewModel::setSourceLinkedFolder,
                    onSelectGallery = viewModel::setSourceManagedSet,
                    onImportPhotos = viewModel::importPhotos,
                    onRemovePhotos = viewModel::removeSetPhotos,
                    onDiscardManagedSet = viewModel::discardManagedSetPhotos,
                    onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                )
            }

            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("When to change")
                TriggerSection(
                    initialTrigger = uiState.trigger,
                    onIntervalChange = viewModel::setTriggerInterval,
                    onTimesOfDayChange = viewModel::setTriggerTimesOfDay,
                )
            }

            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Shuffle")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Switch(checked = uiState.shuffleEnabled, onCheckedChange = viewModel::setShuffleEnabled)
                    Text(
                        if (uiState.shuffleEnabled) {
                            "Plays every image once before reshuffling."
                        } else {
                            "Cycles through the sorted order below."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!uiState.shuffleEnabled) {
                    SortOrderPicker(sortOrder = uiState.sortOrder, onSortOrderChange = viewModel::setSortOrder)
                }
            }

            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Fit mode")
                FitModePicker(fitMode = uiState.fitMode, onFitModeChange = viewModel::setFitMode)
            }

            Box(modifier = Modifier.height(8.dp))
        }
    }
}

private fun Set<ScreenTarget>.toggle(target: ScreenTarget): Set<ScreenTarget> =
    if (target in this) this - target else this + target

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderPicker(sortOrder: SortOrder, onSortOrderChange: (SortOrder) -> Unit) {
    val isName = sortOrder == SortOrder.NAME_ASC || sortOrder == SortOrder.NAME_DESC
    val isAscending = sortOrder == SortOrder.NAME_ASC || sortOrder == SortOrder.DATE_ASC

    fun combine(name: Boolean, ascending: Boolean): SortOrder = when {
        name && ascending -> SortOrder.NAME_ASC
        name && !ascending -> SortOrder.NAME_DESC
        !name && ascending -> SortOrder.DATE_ASC
        else -> SortOrder.DATE_DESC
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isName,
                onClick = { onSortOrderChange(combine(name = true, ascending = isAscending)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Name") }
            SegmentedButton(
                selected = !isName,
                onClick = { onSortOrderChange(combine(name = false, ascending = isAscending)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Date") }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isAscending,
                onClick = { onSortOrderChange(combine(name = isName, ascending = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Ascending") }
            SegmentedButton(
                selected = !isAscending,
                onClick = { onSortOrderChange(combine(name = isName, ascending = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Descending") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitModePicker(fitMode: FitMode, onFitModeChange: (FitMode) -> Unit) {
    val options = listOf(
        FitMode.FILL to "Fill",
        FitMode.FIT_BLUR to "Fit (blur)",
        FitMode.FIT_SOLID to "Fit (solid)",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = fitMode == mode,
                onClick = { onFitModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}
