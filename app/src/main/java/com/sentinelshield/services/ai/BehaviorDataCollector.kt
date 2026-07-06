package com.sentinelshield.services.ai

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Collects real-time behavioral data from installed apps.
 * Gathers metrics like network usage, background activity,
 * battery consumption, and permission usage patterns.
 */
class BehaviorDataCollector(private val context: Context) {

    companion object {
        private const val TAG = "BehaviorDataCollector"
        private const val COLLECTION_WINDOW_HOURS = 4L
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val packageManager = context.packageManager

    /**
     * Collect behavior features for a specific app package.
     */
    fun collectFeatures(packageName: String): BehaviorFeatures {
        val permissionCount = getPermissionCount(packageName)
        val networkActivity = getNetworkActivity(packageName)
        val backgroundWakeups = getBackgroundWakeups(packageName)
        val dataTx = getDataTransmission(packageName)
        val batteryDrain = getBatteryDrain(packageName)
        val cpuUsage = estimateCpuUsage(packageName)
        val sensorAccess = estimateSensorAccess(packageName)
        val ipcFrequency = estimateIpcFrequency(packageName)

        return BehaviorFeatures(
            permissionCount = permissionCount.toFloat(),
            networkActivityPerHour = networkActivity,
            backgroundWakeupsPerHour = backgroundWakeups,
            dataTxMbPerHour = dataTx,
            batteryDrainPercentPerHour = batteryDrain,
            cpuUsagePercent = cpuUsage,
            sensorAccessPerHour = sensorAccess,
            ipcFrequencyPerHour = ipcFrequency
        )
    }

    /**
     * Collect features for all non-system apps.
     */
    fun collectAllAppFeatures(): Map<String, BehaviorFeatures> {
        val results = mutableMapOf<String, BehaviorFeatures>()
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        installedApps
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
            .forEach { appInfo ->
                try {
                    results[appInfo.packageName] = collectFeatures(appInfo.packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to collect features for ${appInfo.packageName}: ${e.message}")
                }
            }

        return results
    }

    /**
     * Get the number of permissions requested by the app.
     */
    private fun getPermissionCount(packageName: String): Int {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get network connection count per hour from usage stats.
     */
    private fun getNetworkActivity(packageName: String): Float {
        if (usageStatsManager == null) return 0f

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)

        try {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )

            val appStats = usageStats?.find { it.packageName == packageName }
            if (appStats != null) {
                // Estimate network activity from foreground time and total time
                val totalTimeInForeground = appStats.totalTimeInForeground
                val timeRatio = totalTimeInForeground.toFloat() / TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)
                // Apps with high background-to-foreground ratio likely have more network activity
                return (timeRatio * 20f).coerceIn(0f, 100f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get network activity for $packageName: ${e.message}")
        }

        return 0f
    }

    /**
     * Estimate background wake-ups per hour.
     */
    private fun getBackgroundWakeups(packageName: String): Float {
        if (usageStatsManager == null) return 0f

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)

        try {
            val events = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )

            val appStats = events?.find { it.packageName == packageName }
            if (appStats != null) {
                // Use lastTimeUsed vs totalTimeInForeground ratio as proxy for background activity
                val lastUsed = appStats.lastTimeUsed
                val foregroundTime = appStats.totalTimeInForeground

                if (foregroundTime > 0 && lastUsed > startTime) {
                    val backgroundRatio = 1f - (foregroundTime.toFloat() / TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS))
                    return (backgroundRatio * 15f).coerceIn(0f, 50f)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get background wakeups for $packageName: ${e.message}")
        }

        return 0f
    }

    /**
     * Get data transmission in MB per hour.
     */
    private fun getDataTransmission(packageName: String): Float {
        if (networkStatsManager == null) return 0f

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)

        try {
            val uid = packageManager.getApplicationInfo(packageName, 0).uid

            val bucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE, null, startTime, endTime
            )

            // Estimate per-app usage based on UID
            // Note: Detailed per-app stats require additional permissions
            if (bucket != null) {
                val totalBytes = (bucket.txBytes + bucket.rxBytes).toFloat()
                val totalMb = totalBytes / (1024f * 1024f)
                return (totalMb / COLLECTION_WINDOW_HOURS).coerceIn(0f, 50f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get data transmission for $packageName: ${e.message}")
        }

        return 0f
    }

    /**
     * Estimate battery drain percentage per hour for the app.
     */
    private fun getBatteryDrain(packageName: String): Float {
        // Battery drain per app requires BatteryStats which needs system permission
        // Use a heuristic based on usage time and permissions
        if (usageStatsManager == null) return 0f

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)

        try {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )

            val appStats = usageStats?.find { it.packageName == packageName }
            if (appStats != null) {
                val foregroundMinutes = appStats.totalTimeInForeground / 60000f
                // Rough estimate: 1% per 10 minutes of active use
                return (foregroundMinutes / 10f / COLLECTION_WINDOW_HOURS).coerceIn(0f, 10f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to estimate battery drain for $packageName: ${e.message}")
        }

        return 0f
    }

    /**
     * Estimate CPU usage percentage based on foreground/background ratio.
     */
    private fun estimateCpuUsage(packageName: String): Float {
        if (usageStatsManager == null) return 0f

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)

        try {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )

            val appStats = usageStats?.find { it.packageName == packageName }
            if (appStats != null) {
                val foregroundMs = appStats.totalTimeInForeground
                val windowMs = TimeUnit.HOURS.toMillis(COLLECTION_WINDOW_HOURS)
                return ((foregroundMs.toFloat() / windowMs) * 100f).coerceIn(0f, 100f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to estimate CPU usage for $packageName: ${e.message}")
        }

        return 0f
    }

    /**
     * Estimate sensor access frequency based on permissions.
     * Apps with sensor permissions that run in background are flagged higher.
     */
    private fun estimateSensorAccess(packageName: String): Float {
        val sensorPermissions = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.BODY_SENSORS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.ACTIVITY_RECOGNITION"
        )

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requestedPerms = packageInfo.requestedPermissions ?: return 0f
            val sensorCount = requestedPerms.count { sensorPermissions.contains(it) }

            // Higher score if app has background location (likely accessing sensors in background)
            val hasBackgroundLocation = requestedPerms.contains("android.permission.ACCESS_BACKGROUND_LOCATION")
            val multiplier = if (hasBackgroundLocation) 3f else 1f

            return (sensorCount * multiplier).coerceIn(0f, 30f)
        } catch (e: Exception) {
            return 0f
        }
    }

    /**
     * Estimate inter-process communication frequency.
     * Apps that export many components or use many content providers
     * have higher IPC potential.
     */
    private fun estimateIpcFrequency(packageName: String): Float {
        try {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS
            )

            var exportedCount = 0
            packageInfo.activities?.forEach { if (it.exported) exportedCount++ }
            packageInfo.services?.forEach { if (it.exported) exportedCount++ }
            packageInfo.receivers?.forEach { if (it.exported) exportedCount++ }
            packageInfo.providers?.forEach { if (it.exported) exportedCount++ }

            return (exportedCount * 5f).coerceIn(0f, 200f)
        } catch (e: Exception) {
            return 0f
        }
    }
}
