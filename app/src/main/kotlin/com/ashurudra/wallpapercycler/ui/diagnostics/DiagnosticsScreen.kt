package com.ashurudra.wallpapercycler.ui.diagnostics

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.database.Cursor
import android.provider.DocumentsContract
import com.ashurudra.wallpapercycler.data.source.LinkedFolderScanner
import com.ashurudra.wallpapercycler.domain.model.FitMode
import com.ashurudra.wallpapercycler.domain.model.ScreenTarget
import com.ashurudra.wallpapercycler.wallpaper.WallpaperApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private data class DiagnosticCheck(val title: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val checks = listOf(
            DiagnosticCheck("1 — Battery optimization exemption") { BatteryExemptionCheck(context) },
            DiagnosticCheck("2 — Exact alarm permission") { ExactAlarmPermissionCheck(context) },
            DiagnosticCheck("3 — Lock-only wallpaper (FLAG_LOCK)") { LockWallpaperCheck(context) },
            DiagnosticCheck("4 — Home-only wallpaper, no lock bleed") { HomeWallpaperCheck(context) },
            DiagnosticCheck("5 — Exact alarm timing") { ExactAlarmTimingCheck(context) },
            DiagnosticCheck("6 — Linked-folder listing (SAF)") { FolderListingCheck(context) },
            DiagnosticCheck("7 — Real decode + crop + apply") { RealWallpaperApplyCheck(context) },
        )

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(checks) { check ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(check.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        check.content()
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryExemptionCheck(context: Context) {
    var status by remember { mutableStateOf(readBatteryExemptionStatus(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        }) { Text("Request exemption") }
        Button(onClick = { status = readBatteryExemptionStatus(context) }) { Text("Refresh status") }
    }
}

private fun readBatteryExemptionStatus(context: Context): String {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val ignoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    return if (ignoring) "Exempt — alarms can fire reliably at short intervals." else "Not exempt yet."
}

@Composable
private fun ExactAlarmPermissionCheck(context: Context) {
    var status by remember { mutableStateOf(readExactAlarmStatus(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }) { Text("Request permission") }
        }
        Button(onClick = { status = readExactAlarmStatus(context) }) { Text("Refresh status") }
    }
}

private fun readExactAlarmStatus(context: Context): String {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
    return if (canSchedule) "Granted." else "Not granted yet."
}

@Composable
private fun LockWallpaperCheck(context: Context) {
    var status by remember { mutableStateOf("Not run yet.") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = {
            status = applyTestWallpaper(context, "LOCK TEST", AndroidColor.rgb(30, 60, 220), WallpaperManager.FLAG_LOCK)
        }) { Text("Apply lock-only wallpaper") }
        Text(
            "After applying: lock your phone and check the lock screen shows this, " +
                "and that the home screen is unaffected.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HomeWallpaperCheck(context: Context) {
    var status by remember { mutableStateOf("Not run yet.") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = {
            status = applyTestWallpaper(context, "HOME TEST", AndroidColor.rgb(220, 90, 30), WallpaperManager.FLAG_SYSTEM)
        }) { Text("Apply home-only wallpaper") }
        Text(
            "Run check 3 first. After applying this, the home screen should change but " +
                "the lock screen should still show the LOCK TEST image.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun applyTestWallpaper(context: Context, label: String, color: Int, flag: Int): String {
    val wallpaperManager = WallpaperManager.getInstance(context)
    val metrics = context.resources.displayMetrics
    val width = wallpaperManager.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
    val height = wallpaperManager.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
    val bitmap = DiagnosticsBitmaps.labeled(width, height, color, label)
    return try {
        wallpaperManager.setBitmap(bitmap, null, true, flag)
        "Applied at ${currentTimeLabel()}."
    } catch (e: Exception) {
        "Failed: ${e.message}"
    } finally {
        bitmap.recycle()
    }
}

@Composable
private fun ExactAlarmTimingCheck(context: Context) {
    var status by remember { mutableStateOf(readLastAlarmStatus(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = {
            status = try {
                scheduleTestAlarm(context, delayMillis = 15_000L, label = "15-second test")
                "Scheduled a 15-second test alarm — reopen this screen after it fires."
            } catch (e: SecurityException) {
                "Failed: exact-alarm permission not granted yet (see check 2 above)."
            }
        }) { Text("Schedule 15-second test") }
        Button(onClick = {
            status = try {
                scheduleTestAlarm(context, delayMillis = 8 * 60 * 60 * 1000L, label = "8-hour overnight test")
                "Scheduled an 8-hour test alarm — leave the phone idle overnight, then check back."
            } catch (e: SecurityException) {
                "Failed: exact-alarm permission not granted yet (see check 2 above)."
            }
        }) { Text("Schedule 8-hour overnight test") }
        Button(onClick = { status = readLastAlarmStatus(context) }) { Text("Refresh status") }
    }
}

private fun scheduleTestAlarm(context: Context, delayMillis: Long, label: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = System.currentTimeMillis() + delayMillis
    val intent = Intent(context, DiagnosticsAlarmReceiver::class.java).apply {
        putExtra(DiagnosticsAlarmReceiver.EXTRA_LABEL, label)
        putExtra(DiagnosticsAlarmReceiver.EXTRA_SCHEDULED_AT, triggerAt)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        label.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
}

private fun readLastAlarmStatus(context: Context): String {
    val prefs = context.getSharedPreferences(DiagnosticsAlarmReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    val firedAt = prefs.getLong(DiagnosticsAlarmReceiver.KEY_LAST_FIRED_AT, 0L)
    if (firedAt == 0L) return "No test alarm has fired yet."
    val label = prefs.getString(DiagnosticsAlarmReceiver.KEY_LAST_LABEL, "unknown")
    val scheduledAt = prefs.getLong(DiagnosticsAlarmReceiver.KEY_LAST_SCHEDULED_AT, firedAt)
    val driftMillis = firedAt - scheduledAt
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return "Last: \"$label\" fired at ${format.format(firedAt)} " +
        "(${driftMillis} ms after its scheduled time)."
}

@Composable
private fun FolderListingCheck(context: Context) {
    var status by remember { mutableStateOf("No folder picked yet.") }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            status = "Folder pick cancelled."
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        status = listImagesInTree(context, uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = { pickFolder.launch(null) }) { Text("Pick folder & list images") }
    }
}

private fun listImagesInTree(context: Context, treeUri: Uri): String {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
    val start = System.currentTimeMillis()
    var total = 0
    var imageCount = 0
    val cursor: Cursor? = context.contentResolver.query(childrenUri, projection, null, null, null)
    cursor?.use {
        val mimeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (it.moveToNext()) {
            total++
            if (it.getString(mimeIndex)?.startsWith("image/") == true) imageCount++
        }
    }
    val elapsed = System.currentTimeMillis() - start
    return "$imageCount image(s) of $total entries — scanned in ${elapsed}ms."
}

private fun currentTimeLabel(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())

@Composable
private fun RealWallpaperApplyCheck(context: Context) {
    var status by remember { mutableStateOf("No folder picked yet.") }
    val scope = rememberCoroutineScope()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            status = "Folder pick cancelled."
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scope.launch {
            status = "Scanning folder..."
            try {
                val images = withContext(Dispatchers.IO) { LinkedFolderScanner(context, uri).listImages() }
                if (images.isEmpty()) {
                    status = "No supported images found in that folder."
                    return@launch
                }
                val chosen = images.random()
                status = "Applying \"${chosen.displayName}\" (FILL, home only)..."
                withContext(Dispatchers.IO) {
                    WallpaperApplier(context).apply(chosen.uri, FitMode.FILL, setOf(ScreenTarget.HOME))
                }
                status = "Applied \"${chosen.displayName}\" as home wallpaper — ${images.size} candidate(s) found."
            } catch (e: Exception) {
                status = "Failed: ${e.message}"
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status)
        Button(onClick = { pickFolder.launch(null) }) { Text("Pick folder & apply a random image") }
        Text(
            "Runs the real decode -> EXIF orient -> crop -> WallpaperManager pipeline, not a " +
                "synthetic test bitmap — check the home screen actually shows a real photo, " +
                "correctly oriented and cropped.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
