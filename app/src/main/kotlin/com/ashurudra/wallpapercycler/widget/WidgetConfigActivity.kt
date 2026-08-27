package com.ashurudra.wallpapercycler.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ashurudra.wallpapercycler.data.prefs.WidgetPreferences
import com.ashurudra.wallpapercycler.ui.theme.WallpaperCyclerTheme

/**
 * Launched by the system widget host when a widget is placed. Picks the scope (Home/Lock/Both)
 * this widget instance follows - see [WidgetScope] for what that means once placed. Back button
 * or process death leaves the widget un-placed, per the standard Android widget configuration
 * contract: RESULT_CANCELED is set as soon as the widget id is known, before the user can pick
 * anything, and only overwritten once a scope is actually chosen.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            WallpaperCyclerTheme {
                WidgetConfigScreen(onScopeChosen = ::confirm)
            }
        }
    }

    private fun confirm(scope: WidgetScope) {
        WidgetPreferences(this).setScope(appWidgetId, scope)

        val request = OneTimeWorkRequestBuilder<WidgetActionWorker>()
            .setInputData(workDataOf(WidgetActionWorker.KEY_ACTION to WidgetActionWorker.ACTION_UPDATE))
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "widget_action_all",
            ExistingWorkPolicy.REPLACE,
            request,
        )

        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(onScopeChosen: (WidgetScope) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Widget scope") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Choose which screen this widget follows. It always shows whichever enabled " +
                    "schedule currently owns that screen, even if that changes later.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { onScopeChosen(WidgetScope.HOME) }, modifier = Modifier.fillMaxWidth()) {
                Text("Home screen")
            }
            Button(onClick = { onScopeChosen(WidgetScope.LOCK) }, modifier = Modifier.fillMaxWidth()) {
                Text("Lock screen")
            }
            Button(onClick = { onScopeChosen(WidgetScope.BOTH) }, modifier = Modifier.fillMaxWidth()) {
                Text("Both screens")
            }
        }
    }
}
