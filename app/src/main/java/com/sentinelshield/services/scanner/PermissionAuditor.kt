package com.sentinelshield.services.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.sentinelshield.data.models.AppPermissionInfo

/**
 * Audits installed app permissions and assigns risk scores
 * based on the type and number of permissions requested.
 */
class PermissionAuditor(private val context: Context) {

    private val packageManager = context.packageManager

    // Dangerous permissions that pose privacy/security risks
    private val dangerousPermissions = setOf(
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.REQUEST_DELETE_PACKAGES"
    )

    // Weight multipliers for risk scoring
    private val permissionWeights = mapOf(
        "android.permission.SEND_SMS" to 15,
        "android.permission.READ_SMS" to 12,
        "android.permission.RECEIVE_SMS" to 12,
        "android.permission.BIND_DEVICE_ADMIN" to 20,
        "android.permission.SYSTEM_ALERT_WINDOW" to 15,
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to 18,
        "android.permission.REQUEST_INSTALL_PACKAGES" to 15,
        "android.permission.CAMERA" to 8,
        "android.permission.RECORD_AUDIO" to 10,
        "android.permission.ACCESS_FINE_LOCATION" to 8,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 12,
        "android.permission.READ_CONTACTS" to 7,
        "android.permission.READ_CALL_LOG" to 10,
        "android.permission.READ_PHONE_STATE" to 6
    )

    /**
     * Audit all installed (non-system) apps.
     */
    fun auditAllApps(): List<AppPermissionInfo> {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        return installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { appInfo -> auditApp(appInfo) }
            .sortedByDescending { it.riskScore }
    }

    /**
     * Audit a single app's permissions.
     */
    fun auditApp(appInfo: ApplicationInfo): AppPermissionInfo {
        val appName = packageManager.getApplicationLabel(appInfo).toString()
        val dangerous = mutableListOf<String>()
        val normal = mutableListOf<String>()

        try {
            val packageInfo = packageManager.getPackageInfo(
                appInfo.packageName,
                PackageManager.GET_PERMISSIONS
            )

            packageInfo.requestedPermissions?.forEach { permission ->
                if (dangerousPermissions.contains(permission)) {
                    dangerous.add(getReadablePermissionName(permission))
                } else {
                    normal.add(getReadablePermissionName(permission))
                }
            }
        } catch (e: Exception) {
            // Package not found or permissions unavailable
        }

        val riskScore = calculateRiskScore(appInfo.packageName)

        return AppPermissionInfo(
            packageName = appInfo.packageName,
            appName = appName,
            dangerousPermissions = dangerous,
            normalPermissions = normal,
            riskScore = riskScore
        )
    }

    /**
     * Calculate a risk score (0-100) based on permissions.
     */
    private fun calculateRiskScore(packageName: String): Int {
        var score = 0
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.forEach { permission ->
                score += permissionWeights.getOrDefault(permission, 2)
            }
        } catch (e: Exception) {
            return 0
        }
        return score.coerceAtMost(100)
    }

    /**
     * Convert permission string to a human-readable name.
     */
    private fun getReadablePermissionName(permission: String): String {
        return permission
            .removePrefix("android.permission.")
            .replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    /**
     * Get apps with overlay permission (potential clickjacking risk).
     */
    fun getAppsWithOverlayPermission(): List<AppPermissionInfo> {
        return auditAllApps().filter { app ->
            app.dangerousPermissions.any {
                it.contains("System alert window", ignoreCase = true)
            }
        }
    }
}
