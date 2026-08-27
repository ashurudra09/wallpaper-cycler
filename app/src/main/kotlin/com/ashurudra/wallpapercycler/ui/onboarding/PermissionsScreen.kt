package com.ashurudra.wallpapercycler.ui.onboarding

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Shown once, before the first launch of the schedules list. Explains and offers the two
 * permissions that make scheduled wallpaper changes reliable, then lets the user move on
 * regardless of whether either was granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Set up Wallpaper Cycler") })
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A couple of permissions",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Wallpaper Cycler changes your wallpaper on a schedule, even when the app " +
                    "isn't open. Android can be aggressive about pausing background work to " +
                    "save battery, which gets in the way of that. These settings help the " +
                    "schedule keep running on time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BatteryExemptionSection(context)
            ExactAlarmSection(context)

            Text(
                "You can change either of these later from your device Settings. The app will " +
                    "keep working without them — just less reliably at short cycling intervals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BatteryExemptionSection(context: Context) {
    var isExempt by remember { mutableStateOf(readBatteryExemptionStatus(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isExempt = readBatteryExemptionStatus(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Battery optimization", style = MaterialTheme.typography.titleMedium)
            Text(
                if (isExempt) {
                    "Exempt — wallpaper changes can fire reliably, even at short intervals."
                } else {
                    "Not exempt yet. Without this, Android may delay or skip scheduled changes " +
                        "to save battery."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    launcher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isExempt) "Exemption granted" else "Allow running in the background") }
            OutlinedButton(
                onClick = { isExempt = readBatteryExemptionStatus(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh status") }
        }
    }
}

private fun readBatteryExemptionStatus(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun ExactAlarmSection(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    var canSchedule by remember { mutableStateOf(readExactAlarmStatus(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        canSchedule = readExactAlarmStatus(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Exact alarms", style = MaterialTheme.typography.titleMedium)
            Text(
                if (canSchedule) {
                    "Granted — a good fallback if you'd rather decline the battery exemption above."
                } else {
                    "Not granted yet. This is a documented fallback for precise timing if you " +
                        "decline the battery exemption above."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    launcher.launch(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (canSchedule) "Permission granted" else "Allow exact alarms") }
            OutlinedButton(
                onClick = { canSchedule = readExactAlarmStatus(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh status") }
        }
    }
}

private fun readExactAlarmStatus(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}
