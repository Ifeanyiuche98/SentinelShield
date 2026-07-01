package com.sentinelshield.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelshield.data.models.SecurityStatus
import com.sentinelshield.services.network.NetworkMonitorService
import com.sentinelshield.services.overlay.OverlayDetectionService
import com.sentinelshield.services.protection.RealTimeProtectionService
import com.sentinelshield.services.scanner.MalwareScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Dashboard screen.
 * Provides overall security status and quick scan functionality.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MalwareScanner(application)

    private val _securityStatus = MutableStateFlow(SecurityStatus())
    val securityStatus: StateFlow<SecurityStatus> = _securityStatus

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress

    init {
        refreshStatus()
    }

    /**
     * Refresh the overall security status.
     */
    fun refreshStatus() {
        _securityStatus.value = SecurityStatus(
            isProtected = RealTimeProtectionService.isRunning,
            threatsFound = RealTimeProtectionService.threatsBlocked,
            lastScanTime = System.currentTimeMillis(),
            realTimeProtectionEnabled = RealTimeProtectionService.isRunning,
            appsScanned = 0,
            suspiciousConnections = NetworkMonitorService.getSuspiciousCount()
        )
    }

    /**
     * Run a full device scan.
     */
    fun runFullScan() {
        if (_isScanning.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = 0f

            val results = scanner.scanAllApps()
            val totalApps = results.size
            var scannedCount = 0

            results.forEach { _ ->
                scannedCount++
                _scanProgress.value = scannedCount.toFloat() / totalApps.toFloat()
            }

            val threats = results.count { it.riskLevel.ordinal >= 3 }

            _securityStatus.value = SecurityStatus(
                isProtected = threats == 0 && RealTimeProtectionService.isRunning,
                threatsFound = threats,
                lastScanTime = System.currentTimeMillis(),
                realTimeProtectionEnabled = RealTimeProtectionService.isRunning,
                appsScanned = totalApps,
                suspiciousConnections = NetworkMonitorService.getSuspiciousCount()
            )

            _isScanning.value = false
        }
    }

    /**
     * Get signature database count.
     */
    fun getSignatureCount(): Int = scanner.getSignatureCount()
}
