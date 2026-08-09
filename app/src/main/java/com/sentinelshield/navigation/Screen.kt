package com.sentinelshield.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object ScanResults : Screen("scan_results")
    object PermissionAuditor : Screen("permission_auditor")
    object NetworkMonitor : Screen("network_monitor")
    object Settings : Screen("settings")
    object AntiTheft : Screen("anti_theft")
}
