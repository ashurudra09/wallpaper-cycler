package com.ashurudra.wallpapercycler.ui.schedules

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ImageSourceConfig
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget

/**
 * One alarm-app-style card in the schedule list. Tapping the card body opens the editor
 * ([onEdit]); the delete icon only reports the request upward — the confirmation dialog
 * (which differs for a linked folder vs. a gallery-imported set) lives in the parent screen.
 */
@Composable
fun ScheduleCard(
    state: ScheduleCardState,
    onToggle: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.schedule.label.ifBlank { "Untitled schedule" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${sourceDescription(state.schedule.source)} • ${fitModeLabel(state.schedule.fitMode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.schedule.enabled, onCheckedChange = onToggle)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ScreenTarget.HOME in state.schedule.targets) {
                    TargetChip(icon = Icons.Filled.Home, label = "Home")
                }
                if (ScreenTarget.LOCK in state.schedule.targets) {
                    TargetChip(icon = Icons.Filled.Lock, label = "Lock")
                }
            }

            Text(
                text = state.nextChangeLabel,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Thumbnail(uri = state.currentImageUri, contentDescription = "Current wallpaper")
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous image")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next image")
                }
                Thumbnail(uri = state.nextImageUri, contentDescription = "Next wallpaper")
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDeleteRequest) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete schedule")
                }
            }

            if (state.cycleErrorMessage != null) {
                Text(
                    text = state.cycleErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(uri: Uri?, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun TargetChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun sourceDescription(source: ImageSourceConfig): String = when (source) {
    is ImageSourceConfig.LinkedFolder -> "Linked folder"
    is ImageSourceConfig.ManagedSet -> "Gallery set"
}

private fun fitModeLabel(fitMode: FitMode): String = when (fitMode) {
    FitMode.FILL -> "Fill"
    FitMode.FIT_BLUR -> "Fit • blurred bars"
    FitMode.FIT_SOLID -> "Fit • solid bars"
}
