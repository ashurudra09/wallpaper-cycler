package com.ashurudra.wallpapercycler.ui.editor

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ashurudra.wallpapercycler.data.source.ImportResult
import com.ashurudra.wallpapercycler.data.source.UriPermissionManager
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import kotlinx.coroutines.launch
import java.io.File

private enum class SourceKind { LINKED_FOLDER, GALLERY }

/** A picker result that would discard the other kind's data, held until the user confirms. */
private sealed interface PendingSwitch {
    data class ToLinkedFolder(val treeUri: Uri) : PendingSwitch
    data class ToGallery(val uris: List<Uri>) : PendingSwitch
}

/**
 * The source step: choose "Link a folder" or "Select from gallery", then (for gallery) manage
 * the imported photos. Picker results are staged, not applied immediately - if they'd discard
 * the other kind's already-configured data, a confirm dialog gates actually applying them, so
 * backing out of that dialog never destroys anything.
 */
@Composable
fun SourceSection(
    source: ImageSourceConfig?,
    setPhotos: List<File>,
    onLinkFolder: (String) -> Unit,
    onSelectGallery: () -> Unit,
    onImportPhotos: suspend (List<Uri>) -> ImportResult,
    onRemovePhotos: suspend (List<String>) -> Unit,
    onDiscardManagedSet: suspend () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val uriPermissionManager = remember { UriPermissionManager(context) }
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var pendingSwitch by remember { mutableStateOf<PendingSwitch?>(null) }

    val currentKind = when (source) {
        is ImageSourceConfig.LinkedFolder -> SourceKind.LINKED_FOLDER
        is ImageSourceConfig.ManagedSet -> SourceKind.GALLERY
        null -> null
    }

    /** Releases a previously-persisted linked-folder grant that a new source is about to replace. */
    fun releasePreviousLinkedFolder() {
        val oldUri = (source as? ImageSourceConfig.LinkedFolder)?.treeUri?.let(Uri::parse) ?: return
        if (uriPermissionManager.isPersisted(oldUri)) {
            uriPermissionManager.release(oldUri)
        }
    }

    fun applyImport(uris: List<Uri>) {
        onSelectGallery()
        importing = true
        scope.launch {
            val result = onImportPhotos(uris)
            importing = false
            onMessage(
                "Imported ${result.imported.size}, skipped ${result.duplicates.size} duplicate(s), " +
                    "${result.failed.size} failed.",
            )
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (currentKind == SourceKind.GALLERY) {
            pendingSwitch = PendingSwitch.ToLinkedFolder(uri)
        } else {
            releasePreviousLinkedFolder()
            uriPermissionManager.persist(uri)
            onLinkFolder(uri.toString())
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (currentKind == SourceKind.LINKED_FOLDER) {
            pendingSwitch = PendingSwitch.ToGallery(uris)
        } else {
            applyImport(uris)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceOptionRow(
            title = "Link a folder",
            subtitle = (source as? ImageSourceConfig.LinkedFolder)?.let { folderDisplayName(it.treeUri) }
                ?: "Images stay where they are - edits to the folder show up automatically.",
            selected = currentKind == SourceKind.LINKED_FOLDER,
            onClick = { pickFolder.launch(null) },
        )
        SourceOptionRow(
            title = "Select from gallery",
            subtitle = "Copies the photos you pick into this schedule's own set.",
            selected = currentKind == SourceKind.GALLERY,
            onClick = {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )

        if (currentKind == SourceKind.GALLERY) {
            if (importing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("Importing photos...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            SetPhotoGrid(
                photos = setPhotos,
                importing = importing,
                onAddPhotos = {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDeleteSelected = { names -> scope.launch { onRemovePhotos(names) } },
            )
        }
    }

    pendingSwitch?.let { switch ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("Switch source?") },
            text = {
                Text(
                    when (switch) {
                        is PendingSwitch.ToLinkedFolder ->
                            "Switching to a linked folder will discard the photos imported for this schedule. Continue?"
                        is PendingSwitch.ToGallery ->
                            "Switching to gallery photos will discard the linked folder for this schedule. Continue?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSwitch = null
                    when (switch) {
                        is PendingSwitch.ToLinkedFolder -> {
                            uriPermissionManager.persist(switch.treeUri)
                            onLinkFolder(switch.treeUri.toString())
                            scope.launch { onDiscardManagedSet() }
                        }
                        is PendingSwitch.ToGallery -> {
                            releasePreviousLinkedFolder()
                            applyImport(switch.uris)
                        }
                    }
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pendingSwitch = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SourceOptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Best-effort human-readable label for a SAF tree URI - falls back to a generic label. */
private fun folderDisplayName(treeUri: String): String = runCatching {
    val uri = Uri.parse(treeUri)
    val docId = DocumentsContract.getTreeDocumentId(uri)
    "Linked: " + docId.substringAfter(':', docId).ifBlank { "root" }
}.getOrDefault("Folder linked")
