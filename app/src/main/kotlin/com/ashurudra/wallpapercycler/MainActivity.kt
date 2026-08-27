package com.ashurudra.wallpapercycler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashurudra.wallpapercycler.domain.model.ThemeMode
import com.ashurudra.wallpapercycler.ui.diagnostics.DiagnosticsScreen
import com.ashurudra.wallpapercycler.ui.editor.ScheduleEditorScreen
import com.ashurudra.wallpapercycler.ui.onboarding.PermissionsScreen
import com.ashurudra.wallpapercycler.ui.schedules.SchedulesScreen
import com.ashurudra.wallpapercycler.ui.settings.SettingsScreen
import com.ashurudra.wallpapercycler.ui.theme.WallpaperCyclerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_SCHEDULES = "schedules"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_EDITOR_NEW = "editor_new"
private const val ROUTE_EDITOR_EDIT = "editor_edit/{scheduleId}"
private const val ARG_SCHEDULE_ID = "scheduleId"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Gates the splash screen until we know whether onboarding has already been completed,
        // so the very first frame the user sees is the correct start destination - never a
        // flash of the schedules list followed by a jump to onboarding (or vice versa).
        var startReady by mutableStateOf(false)
        var showOnboarding by mutableStateOf<Boolean?>(null)
        splashScreen.setKeepOnScreenCondition { !startReady }

        val container = (application as WallpaperCyclerApp).container
        lifecycleScope.launch {
            // Falls back to showing onboarding if the settings DataStore can't be read (e.g. a
            // corrupted preferences file), so a read failure can't crash the app or hang the
            // splash screen forever - mirrors the runCatching fallback SettingsRepository.themeMode
            // already uses.
            showOnboarding = runCatching { !container.settingsRepository.onboardingComplete.first() }
                .getOrDefault(true)
            startReady = true
        }

        setContent {
            val themeMode by container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val customAccentArgb by container.settingsRepository.customAccent.collectAsState(initial = null)
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

            WallpaperCyclerTheme(
                darkTheme = darkTheme,
                accentOverride = customAccentArgb?.let { Color(it) },
            ) {
                val onboardingNeeded = showOnboarding
                if (onboardingNeeded == null) {
                    // Splash screen is still covering the activity while startReady is false.
                    Surface(color = MaterialTheme.colorScheme.background) {}
                } else {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()
                    NavHost(
                        navController = navController,
                        startDestination = if (onboardingNeeded) ROUTE_ONBOARDING else ROUTE_SCHEDULES,
                    ) {
                        composable(ROUTE_ONBOARDING) {
                            PermissionsScreen(
                                onContinue = {
                                    scope.launch {
                                        container.settingsRepository.setOnboardingComplete()
                                        navController.navigate(ROUTE_SCHEDULES) {
                                            popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                        composable(ROUTE_SCHEDULES) {
                            SchedulesScreen(
                                onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                                onOpenEditor = { scheduleId ->
                                    if (scheduleId == null) {
                                        navController.navigate(ROUTE_EDITOR_NEW)
                                    } else {
                                        navController.navigate("editor_edit/$scheduleId")
                                    }
                                },
                            )
                        }
                        composable(ROUTE_DIAGNOSTICS) {
                            DiagnosticsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(ROUTE_EDITOR_NEW) {
                            ScheduleEditorScreen(
                                scheduleId = null,
                                onDone = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = ROUTE_EDITOR_EDIT,
                            arguments = listOf(navArgument(ARG_SCHEDULE_ID) { type = NavType.StringType }),
                        ) { backStackEntry ->
                            ScheduleEditorScreen(
                                scheduleId = backStackEntry.arguments?.getString(ARG_SCHEDULE_ID),
                                onDone = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
