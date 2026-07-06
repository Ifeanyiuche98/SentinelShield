package com.sentinelshield.services.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.sentinelshield.data.models.AppPermissionInfo

/**
 * Audits installed app permissions and assigns risk scores
 * based on the type and number of permissions requested,
 * with context-aware scoring that considers app category.
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

    // High-risk permissions with base weights
    private val permissionWeights = mapOf(
        "android.permission.SEND_SMS" to 15,
        "android.permission.READ_SMS" to 12,
        "android.permission.RECEIVE_SMS" to 12,
        "android.permission.BIND_DEVICE_ADMIN" to 20,
        "android.permission.SYSTEM_ALERT_WINDOW" to 12,
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to 15,
        "android.permission.REQUEST_INSTALL_PACKAGES" to 12,
        "android.permission.CAMERA" to 5,
        "android.permission.RECORD_AUDIO" to 6,
        "android.permission.ACCESS_FINE_LOCATION" to 5,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 10,
        "android.permission.READ_CONTACTS" to 4,
        "android.permission.READ_CALL_LOG" to 8,
        "android.permission.READ_PHONE_STATE" to 4,
        "android.permission.WRITE_EXTERNAL_STORAGE" to 3,
        "android.permission.READ_EXTERNAL_STORAGE" to 3
    )

    // Well-known trusted app packages that legitimately need many permissions
    private val trustedPackagePrefixes = setOf(
        "com.google.",
        "com.samsung.",
        "com.android.",
        "com.whatsapp",
        "org.telegram.",
        "com.instagram.",
        "com.facebook.",
        "com.twitter.",
        "com.spotify.",
        "com.snapchat.",
        "com.microsoft.",
        "com.amazon.",
        "com.netflix.",
        "com.uber.",
        "com.lyft.",
        "com.zhiliaoapp.musically",  // TikTok
        "com.discord",
        "com.slack",
        "com.skype.",
        "us.zoom.",
        "com.lemon.lvoverseas",  // CapCut
        "ng.indriver.",  // inDrive
        "app.phantom.",  // Phantom wallet
        "com.binance.",
        "com.coinbase.",
        "io.metamask.",
        "com.trustwallet."
    )

    // App categories that legitimately need certain permissions
    private val communicationPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.CALL_PHONE",
        "android.permission.READ_PHONE_STATE"
    )

    private val mediaPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )

    private val navigationPermissions = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
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

        val riskScore = calculateRiskScore(appInfo.packageName, dangerous.size)

        return AppPermissionInfo(
            packageName = appInfo.packageName,
            appName = appName,
            dangerousPermissions = dangerous,
            normalPermissions = normal,
            riskScore = riskScore
        )
    }

    /**
     * Calculate a context-aware risk score (0-100) based on permissions.
     * Takes into account:
     * - Whether the app is from a trusted developer
     * - Whether permissions make sense for the app's category
     * - The severity of individual permissions
     */
    private fun calculateRiskScore(packageName: String, dangerousCount: Int): Int {
        var rawScore = 0
        val requestedPermissions: Array<String>

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            requestedPermissions = packageInfo.requestedPermissions ?: return 0
        } catch (e: Exception) {
            return 0
        }

        // Calculate raw score from permission weights
        requestedPermissions.forEach { permission ->
            rawScore += permissionWeights.getOrDefault(permission, 1)
        }

        // Apply trust discount for well-known apps (reduce score by 50%)
        val isTrusted = trustedPackagePrefixes.any { packageName.startsWith(it) }
        if (isTrusted) {
            rawScore = (rawScore * 0.5).toInt()
        }

        // Apply category-based discount
        val categoryDiscount = getCategoryDiscount(packageName, requestedPermissions)
        rawScore = (rawScore * (1.0 - categoryDiscount)).toInt()

        // Bonus penalty for truly suspicious combinations
        val hasSmsAccess = requestedPermissions.any { it.contains("SMS") }
        val hasDeviceAdmin = requestedPermissions.contains("android.permission.BIND_DEVICE_ADMIN")
        val hasInstallPackages = requestedPermissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")

        if (hasSmsAccess && hasDeviceAdmin) rawScore += 25
        if (hasInstallPackages && !isTrusted) rawScore += 15
        if (hasSmsAccess && hasInstallPackages && !isTrusted) rawScore += 20

        // Scale: cap at 100, minimum 5 if any dangerous permissions exist
        val finalScore = rawScore.coerceIn(0, 100)
        return if (dangerousCount > 0 && finalScore < 5) 5 else finalScore
    }

    /**
     * Determine a discount factor based on app category inference.
     * Returns 0.0 to 0.4 (0% to 40% discount).
     */
    private fun getCategoryDiscount(packageName: String, permissions: Array<String>): Double {
        val pkg = packageName.lowercase()

        // Communication/messaging apps legitimately need camera, mic, contacts
        val isCommunicationApp = pkg.contains("messenger") || pkg.contains("chat") ||
                pkg.contains("telegram") || pkg.contains("whatsapp") || pkg.contains("signal") ||
                pkg.contains("discord") || pkg.contains("viber") || pkg.contains("skype") ||
                pkg.contains("zoom") || pkg.contains("meet")

        if (isCommunicationApp) {
            val relevantPerms = permissions.count { communicationPermissions.contains(it) }
            if (relevantPerms > 0) return 0.35
        }

        // Media/camera/video apps legitimately need camera, storage, mic
        val isMediaApp = pkg.contains("camera") || pkg.contains("video") || pkg.contains("photo") ||
                pkg.contains("capcut") || pkg.contains("editor") || pkg.contains("gallery") ||
                pkg.contains("recorder") || pkg.contains("tiktok") || pkg.contains("musically")

        if (isMediaApp) {
            val relevantPerms = permissions.count { mediaPermissions.contains(it) }
            if (relevantPerms > 0) return 0.35
        }

        // Navigation/ride-sharing apps legitimately need location
        val isNavApp = pkg.contains("maps") || pkg.contains("uber") || pkg.contains("lyft") ||
                pkg.contains("driver") || pkg.contains("indriver") || pkg.contains("bolt") ||
                pkg.contains("navigation") || pkg.contains("waze")

        if (isNavApp) {
            val relevantPerms = permissions.count { navigationPermissions.contains(it) }
            if (relevantPerms > 0) return 0.3
        }

        // Crypto/finance apps - location and biometrics are expected
        val isFinanceApp = pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("crypto") ||
                pkg.contains("binance") || pkg.contains("coinbase") || pkg.contains("phantom") ||
                pkg.contains("metamask") || pkg.contains("trust")

        if (isFinanceApp) {
            return 0.2
        }

        return 0.0
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
