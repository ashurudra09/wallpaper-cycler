package com.ashurudra.wallpapercycler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ashurudra.wallpapercycler.ui.diagnostics.DiagnosticsScreen
import com.ashurudra.wallpapercycler.ui.schedules.SchedulesScreen
import com.ashurudra.wallpapercycler.ui.theme.WallpaperCyclerTheme

private const val ROUTE_SCHEDULES = "schedules"
private const val ROUTE_DIAGNOSTICS = "diagnostics"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WallpaperCyclerTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_SCHEDULES) {
                    composable(ROUTE_SCHEDULES) {
                        SchedulesScreen(
                            onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                        )
                    }
                    composable(ROUTE_DIAGNOSTICS) {
                        DiagnosticsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
