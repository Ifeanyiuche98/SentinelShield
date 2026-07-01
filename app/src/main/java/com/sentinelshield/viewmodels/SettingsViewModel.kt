package com.sentinelshield.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.sentinelshield.services.protection.RealTimeProtectionService
import com.sentinelshield.services.network.NetworkMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Settings screen.
 * Manages app preferences and service toggles.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE)

    private val _realTimeProtection = MutableStateFlow(prefs.getBoolean("realtime_protection", true))
    val realTimeProtection: StateFlow<Boolean> = _realTimeProtection

    private val _networkMonitoring = MutableStateFlow(prefs.getBoolean("network_monitoring", false))
    val networkMonitoring: StateFlow<Boolean> = _networkMonitoring

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _autoScanOnInstall = MutableStateFlow(prefs.getBoolean("auto_scan_install", true))
    val autoScanOnInstall: StateFlow<Boolean> = _autoScanOnInstall

    private val _scanFrequency = MutableStateFlow(prefs.getString("scan_frequency", "daily") ?: "daily")
    val scanFrequency: StateFlow<String> = _scanFrequency

    private val _darkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true))
    val darkMode: StateFlow<Boolean> = _darkMode

    /**
     * Toggle real-time protection service.
     */
    fun toggleRealTimeProtection(enabled: Boolean) {
        _realTimeProtection.value = enabled
        prefs.edit().putBoolean("realtime_protection", enabled).apply()

        val context = getApplication<Application>()
        val intent = Intent(context, RealTimeProtectionService::class.java)
        if (enabled) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    /**
     * Toggle network monitoring (VPN).
     */
    fun toggleNetworkMonitoring(enabled: Boolean) {
        _networkMonitoring.value = enabled
        prefs.edit().putBoolean("network_monitoring", enabled).apply()

        val context = getApplication<Application>()
        val intent = Intent(context, NetworkMonitorService::class.java)
        if (enabled) {
            context.startForegroundService(intent)
        } else {
            intent.action = "STOP"
            context.startService(intent)
        }
    }

    /**
     * Toggle notifications.
     */
    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications", enabled).apply()
    }

    /**
     * Toggle auto-scan on new app install.
     */
    fun toggleAutoScanOnInstall(enabled: Boolean) {
        _autoScanOnInstall.value = enabled
        prefs.edit().putBoolean("auto_scan_install", enabled).apply()
    }

    /**
     * Set scan frequency.
     */
    fun setScanFrequency(frequency: String) {
        _scanFrequency.value = frequency
        prefs.edit().putString("scan_frequency", frequency).apply()
    }

    /**
     * Toggle dark mode.
     */
    fun toggleDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }
}
