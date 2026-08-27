package com.ashurudra.wallpapercycler.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashurudra.wallpapercycler.BuildConfig
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(onOpenDiagnostics: () -> Unit, onOpenEditor: (scheduleId: String?) -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel: SchedulesViewModel = viewModel(factory = SchedulesViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // The schedule pending a delete confirmation - its source decides which dialog variant
    // to show (a managed set's imported photos need an extra yes/no of their own).
    var scheduleForDeleteConfirm by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallpaper Cycler") },
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onOpenDiagnostics) {
                            Text("Diagnostics")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add schedule")
            }
        },
    ) { padding ->
        when {
            uiState.cards.isEmpty() && uiState.isLoading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.cards.isEmpty() -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No schedules yet.\nTap + to cycle your first folder.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.cards, key = { it.schedule.id }) { cardState ->
                        ScheduleCard(
                            state = cardState,
                            onToggle = { enable -> viewModel.onToggle(cardState.schedule.id, enable) },
                            onNext = { viewModel.onNext(cardState.schedule.id) },
                            onPrevious = { viewModel.onPrevious(cardState.schedule.id) },
                            onEdit = { onOpenEditor(cardState.schedule.id) },
                            onDeleteRequest = { scheduleForDeleteConfirm = cardState.schedule },
                        )
                    }
                }
            }
        }
    }

    val scheduleToDelete = scheduleForDeleteConfirm
    if (scheduleToDelete != null) {
        when (scheduleToDelete.source) {
            is ImageSourceConfig.ManagedSet -> {
                AlertDialog(
                    onDismissRequest = { scheduleForDeleteConfirm = null },
                    title = { Text("Delete schedule?") },
                    text = {
                        Text(
                            "This schedule has its own copy of imported photos. " +
                                "Delete those too, or just delete the schedule and keep them?",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onDelete(scheduleToDelete.id, true)
                            scheduleForDeleteConfirm = null
                        }) { Text("Delete photos too") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { scheduleForDeleteConfirm = null }) { Text("Cancel") }
                            TextButton(onClick = {
                                viewModel.onDelete(scheduleToDelete.id, false)
                                scheduleForDeleteConfirm = null
                            }) { Text("Keep photos") }
                        }
                    },
                )
            }
            is ImageSourceConfig.LinkedFolder -> {
                AlertDialog(
                    onDismissRequest = { scheduleForDeleteConfirm = null },
                    title = { Text("Delete this schedule?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onDelete(scheduleToDelete.id, false)
                            scheduleForDeleteConfirm = null
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { scheduleForDeleteConfirm = null }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}
