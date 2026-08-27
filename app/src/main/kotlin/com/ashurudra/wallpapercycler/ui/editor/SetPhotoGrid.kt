package com.ashurudra.wallpapercycler.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/**
 * The managed set's thumbnail grid: long-press (or tap once something is already selected) to
 * multi-select, then delete; "Add photos" re-launches the same gallery-import flow the caller
 * already wired up for the initial pick.
 */
@Composable
fun SetPhotoGrid(
    photos: List<File>,
    importing: Boolean,
    onAddPhotos: () -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resets whenever the underlying photo list actually changes (an import or a delete) -
    // toggling selection itself never changes `photos`, so an in-progress selection survives
    // recomposition just fine.
    var selected by remember(photos) { mutableStateOf(emptySet<String>()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selected = emptySet() }) { Text("Cancel") }
                    Button(onClick = {
                        onDeleteSelected(selected.toList())
                        selected = emptySet()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }

        // A plain chunked grid, not LazyVerticalGrid - this Column already lives inside the
        // editor screen's own Modifier.verticalScroll(), and a lazy layout measured with an
        // unbounded (infinite) height parent throws at measure time. Fine at this app's scale
        // (personal photo sets, not tens of thousands of images).
        photos.chunked(PHOTO_GRID_COLUMNS).forEach { rowPhotos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowPhotos.forEach { file ->
                    PhotoCell(
                        file = file,
                        selected = file.name in selected,
                        hasSelection = selected.isNotEmpty(),
                        onToggle = { selected = if (file.name in selected) selected - file.name else selected + file.name },
                        onLongClick = { selected = selected + file.name },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(PHOTO_GRID_COLUMNS - rowPhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        OutlinedButton(
            onClick = onAddPhotos,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (importing) "Importing..." else "Add photos") }

        val totalBytes = photos.sumOf { it.length() }
        Text(
            "${photos.size} photo(s) - ${formatSize(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PHOTO_GRID_COLUMNS = 3

@Composable
private fun PhotoCell(
    file: File,
    selected: Boolean,
    hasSelection: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (hasSelection) onToggle() },
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(4.dp),
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}
