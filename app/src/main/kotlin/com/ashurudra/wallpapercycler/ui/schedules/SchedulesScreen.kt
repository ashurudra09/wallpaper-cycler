package com.ashurudra.wallpapercycler.ui.schedules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ashurudra.wallpapercycler.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(onOpenDiagnostics: () -> Unit) {
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
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Phase 6: open the schedule editor */ }) {
                Icon(Icons.Filled.Add, contentDescription = "Add schedule")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No schedules yet.\nTap + to cycle your first folder.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
