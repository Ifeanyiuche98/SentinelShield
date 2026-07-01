package com.sentinelshield.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelshield.data.models.ScanResult
import com.sentinelshield.services.scanner.MalwareScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Scan Results screen.
 * Manages scan execution and results display.
 */
class ScanResultsViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MalwareScanner(application)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress

    /**
     * Run a full scan of all installed apps.
     */
    fun scanAllApps() {
        if (_isScanning.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = 0f
            _scanResults.value = emptyList()

            val results = scanner.scanAllApps()
            _scanResults.value = results
            _scanProgress.value = 1f
            _isScanning.value = false
        }
    }

    /**
     * Scan a specific app by package name.
     */
    fun scanSingleApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = scanner.scanAppByPackage(packageName)
            if (result != null) {
                _scanResults.value = _scanResults.value + result
            }
        }
    }

    /**
     * Clear scan results.
     */
    fun clearResults() {
        _scanResults.value = emptyList()
    }
}
