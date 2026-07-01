package com.sentinelshield.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelshield.data.models.NetworkConnection
import com.sentinelshield.services.network.NetworkMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Network Monitor screen.
 * Provides real-time network connection data.
 */
class NetworkMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val _connections = MutableStateFlow<List<NetworkConnection>>(emptyList())
    val connections: StateFlow<List<NetworkConnection>> = _connections

    private val _suspiciousConnections = MutableStateFlow<List<NetworkConnection>>(emptyList())
    val suspiciousConnections: StateFlow<List<NetworkConnection>> = _suspiciousConnections

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring

    private val _totalConnections = MutableStateFlow(0)
    val totalConnections: StateFlow<Int> = _totalConnections

    init {
        startPolling()
    }

    /**
     * Poll the network monitor service for updated connection data.
     */
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                _isMonitoring.value = NetworkMonitorService.isRunning
                _totalConnections.value = NetworkMonitorService.getConnectionCount()

                synchronized(NetworkMonitorService.activeConnections) {
                    _connections.value = NetworkMonitorService.activeConnections.takeLast(50).reversed()
                }

                synchronized(NetworkMonitorService.suspiciousConnections) {
                    _suspiciousConnections.value = NetworkMonitorService.suspiciousConnections.toList()
                }

                delay(2000) // Update every 2 seconds
            }
        }
    }

    /**
     * Get connection statistics.
     */
    fun getStats(): ConnectionStats {
        return ConnectionStats(
            totalConnections = _totalConnections.value,
            suspiciousCount = _suspiciousConnections.value.size,
            isVpnActive = _isMonitoring.value
        )
    }

    data class ConnectionStats(
        val totalConnections: Int,
        val suspiciousCount: Int,
        val isVpnActive: Boolean
    )
}
