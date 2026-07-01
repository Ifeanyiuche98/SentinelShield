package com.sentinelshield.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinelshield.data.models.AppPermissionInfo
import com.sentinelshield.services.scanner.PermissionAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Permission Auditor screen.
 * Manages permission analysis for all installed apps.
 */
class PermissionAuditorViewModel(application: Application) : AndroidViewModel(application) {

    private val auditor = PermissionAuditor(application)

    private val _appPermissions = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    val appPermissions: StateFlow<List<AppPermissionInfo>> = _appPermissions

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing

    private val _overlayApps = MutableStateFlow<List<AppPermissionInfo>>(emptyList())
    val overlayApps: StateFlow<List<AppPermissionInfo>> = _overlayApps

    /**
     * Audit all installed apps' permissions.
     */
    fun auditAllApps() {
        if (_isAuditing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isAuditing.value = true
            _appPermissions.value = auditor.auditAllApps()
            _overlayApps.value = auditor.getAppsWithOverlayPermission()
            _isAuditing.value = false
        }
    }

    /**
     * Get apps filtered by minimum risk score.
     */
    fun getHighRiskApps(minScore: Int = 50): List<AppPermissionInfo> {
        return _appPermissions.value.filter { it.riskScore >= minScore }
    }
}
