package com.ashurudra.wallpapercycler.ui.settings

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import kotlinx.coroutines.launch

/** A handful of appealing, fixed accent choices - not an exhaustive picker, just a starting point. */
private val ACCENT_PRESETS = listOf(
    "Default" to null,
    "Indigo" to 0xFF415AC7,
    "Teal" to 0xFF00696D,
    "Green" to 0xFF3D6A33,
    "Amber" to 0xFF8B5000,
    "Rose" to 0xFF9C4059,
    "Purple" to 0xFF7A4CA0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup(uri) { ok ->
            scope.launch {
                snackbarHostState.showSnackbar(if (ok) "Backup saved." else "Couldn't save the backup.")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) showRestoreConfirm = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsSection(title = "Theme") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = uiState.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        ) { Text(mode.label()) }
                    }
                }
            }

            SettingsSection(title = "Accent color") {
                AccentPicker(
                    customAccentArgb = uiState.customAccentArgb,
                    onAccentChange = viewModel::setCustomAccent,
                )
            }

            SettingsSection(title = "Backup") {
                Text(
                    "Saves your schedules and settings to a JSON file. Folder permissions can't " +
                        "be exported, and gallery photos aren't included - a restored schedule may " +
                        "need its folder re-linked or its photos re-imported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { exportLauncher.launch("wallpaper-cycler-backup.json") }) {
                        Text("Export")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Text("Restore")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    val pendingRestoreUri = showRestoreConfirm
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces every schedule currently in the app. Restored schedules start " +
                        "disabled - re-enable them once any linked folders are re-linked.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = null
                    viewModel.restoreBackup(pendingRestoreUri) { outcome ->
                        scope.launch {
                            val message = when (outcome) {
                                is RestoreOutcome.Success -> buildString {
                                    append("Restored ${outcome.scheduleCount} schedule(s).")
                                    if (outcome.linkedFoldersToRelink > 0) {
                                        append(" ${outcome.linkedFoldersToRelink} need their folder re-linked.")
                                    }
                                }
                                is RestoreOutcome.Failed -> "Restore failed: ${outcome.reason}"
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun AccentPicker(customAccentArgb: Int?, onAccentChange: (Int?) -> Unit) {
    var hexText by remember(customAccentArgb) {
        mutableStateOf(customAccentArgb?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "")
    }
    var hexError by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ACCENT_PRESETS) { (name, argbLong) ->
                val argb = argbLong?.toInt()
                val selected = argb == customAccentArgb
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(56.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(argb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
                            .clickable { onAccentChange(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "$name selected",
                                tint = Color.White,
                            )
                        }
                    }
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = hexText,
                onValueChange = { hexText = it; hexError = false },
                label = { Text("Custom hex, e.g. #FF8A00") },
                singleLine = true,
                isError = hexError,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Button(onClick = {
                val parsed = parseHexColor(hexText)
                if (parsed == null) {
                    hexError = true
                } else {
                    onAccentChange(parsed)
                }
            }) { Text("Apply") }
        }
        if (hexError) {
            Text(
                "Not a valid color - try a format like #FF8A00.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun parseHexColor(text: String): Int? {
    val normalized = if (text.startsWith("#")) text else "#$text"
    return runCatching { AndroidColor.parseColor(normalized) or (0xFF shl 24) }.getOrNull()
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}
