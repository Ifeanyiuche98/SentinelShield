package com.sentinelshield.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sentinelshield.screens.*

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = modifier) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToScan = { navController.navigate(Screen.ScanResults.route) },
                onNavigateToPermissions = { navController.navigate(Screen.PermissionAuditor.route) },
                onNavigateToNetwork = { navController.navigate(Screen.NetworkMonitor.route) }
            )
        }
        composable(Screen.ScanResults.route) {
            ScanResultsScreen()
        }
        composable(Screen.PermissionAuditor.route) {
            PermissionAuditorScreen()
        }
        composable(Screen.NetworkMonitor.route) {
            NetworkMonitorScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.AntiTheft.route) {
            AntiTheftScreen()
        }
    }
}
