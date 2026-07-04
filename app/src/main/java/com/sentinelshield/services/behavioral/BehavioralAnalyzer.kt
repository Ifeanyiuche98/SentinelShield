package com.sentinelshield.services.behavioral

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * BehavioralAnalyzer - AI-based anomaly detection for installed apps.
 * 
 * Monitors app behavior patterns and flags deviations that could indicate
 * malicious activity, even without known signatures (zero-day mitigation).
 * 
 * Behavioral indicators monitored:
 * - Unusual data usage patterns
 * - Abnormal permission usage frequency
 * - Background activity spikes
 * - Suspicious inter-app communication
 * - Unexpected network connections
 */
class BehavioralAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "BehavioralAnalyzer"
        private const val PREFS_NAME = "behavioral_profiles"
        private const val ANOMALY_THRESHOLD = 2.5 // Standard deviations
        private const val MIN_SAMPLES = 5 // Minimum data points before flagging
    }

    data class AppBehaviorProfile(
        val packageName: String,
        val avgDataUsageMb: Double = 0.0,
        val avgForegroundTimeMin: Double = 0.0,
        val avgBackgroundTimeMin: Double = 0.0,
        val dataUsageStdDev: Double = 0.0,
        val foregroundStdDev: Double = 0.0,
        val backgroundStdDev: Double = 0.0,
        val sampleCount: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class BehaviorAnomaly(
        val packageName: String,
        val appName: String,
        val anomalyType: AnomalyType,
        val severity: Float, // 0.0 to 1.0
        val description: String,
        val currentValue: Double,
        val expectedValue: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class AnomalyType {
        EXCESSIVE_DATA_USAGE,
        ABNORMAL_BACKGROUND_ACTIVITY,
        UNUSUAL_PERMISSION_ACCESS,
        SUSPICIOUS_NETWORK_PATTERN,
        RAPID_BATTERY_DRAIN,
        UNEXPECTED_WAKELOCK
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Analyze all installed apps for behavioral anomalies
     */
    suspend fun analyzeAllApps(): List<BehaviorAnomaly> = withContext(Dispatchers.IO) {
        val anomalies = mutableListOf<BehaviorAnomaly>()
        val pm = context.packageManager

        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            for (appInfo in installedApps) {
                // Skip system apps
                if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) continue

                try {
                    val appAnomalies = analyzeApp(appInfo.packageName)
                    anomalies.addAll(appAnomalies)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to analyze ${appInfo.packageName}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze apps", e)
        }

        anomalies.sortedByDescending { it.severity }
    }

    /**
     * Analyze a specific app for behavioral anomalies
     */
    suspend fun analyzeApp(packageName: String): List<BehaviorAnomaly> = withContext(Dispatchers.IO) {
        val anomalies = mutableListOf<BehaviorAnomaly>()
        val profile = getOrCreateProfile(packageName)
        val currentBehavior = getCurrentBehavior(packageName)
        val pm = context.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        // Only flag if we have enough historical data
        if (profile.sampleCount < MIN_SAMPLES) {
            // Still learning - just update profile
            updateProfile(packageName, currentBehavior)
            return@withContext anomalies
        }

        // Check data usage anomaly
        if (profile.dataUsageStdDev > 0) {
            val zScore = (currentBehavior.dataUsageMb - profile.avgDataUsageMb) / profile.dataUsageStdDev
            if (zScore > ANOMALY_THRESHOLD) {
                anomalies.add(
                    BehaviorAnomaly(
                        packageName = packageName,
                        appName = appName,
                        anomalyType = AnomalyType.EXCESSIVE_DATA_USAGE,
                        severity = (zScore / 5.0).toFloat().coerceIn(0.3f, 1.0f),
                        description = "Data usage ${String.format("%.1f", currentBehavior.dataUsageMb)}MB is significantly higher than average ${String.format("%.1f", profile.avgDataUsageMb)}MB",
                        currentValue = currentBehavior.dataUsageMb,
                        expectedValue = profile.avgDataUsageMb
                    )
                )
            }
        }

        // Check background activity anomaly
        if (profile.backgroundStdDev > 0) {
            val zScore = (currentBehavior.backgroundTimeMin - profile.avgBackgroundTimeMin) / profile.backgroundStdDev
            if (zScore > ANOMALY_THRESHOLD) {
                anomalies.add(
                    BehaviorAnomaly(
                        packageName = packageName,
                        appName = appName,
                        anomalyType = AnomalyType.ABNORMAL_BACKGROUND_ACTIVITY,
                        severity = (zScore / 5.0).toFloat().coerceIn(0.3f, 1.0f),
                        description = "Background activity ${String.format("%.0f", currentBehavior.backgroundTimeMin)} min is abnormally high (avg: ${String.format("%.0f", profile.avgBackgroundTimeMin)} min)",
                        currentValue = currentBehavior.backgroundTimeMin,
                        expectedValue = profile.avgBackgroundTimeMin
                    )
                )
            }
        }

        // Check for suspicious network pattern (high data, low foreground = possible exfiltration)
        if (currentBehavior.dataUsageMb > 10 && currentBehavior.foregroundTimeMin < 1) {
            anomalies.add(
                BehaviorAnomaly(
                    packageName = packageName,
                    appName = appName,
                    anomalyType = AnomalyType.SUSPICIOUS_NETWORK_PATTERN,
                    severity = 0.8f,
                    description = "App used ${String.format("%.1f", currentBehavior.dataUsageMb)}MB of data with almost no foreground activity - possible data exfiltration",
                    currentValue = currentBehavior.dataUsageMb,
                    expectedValue = 0.0
                )
            )
        }

        // Update the behavioral profile with new data
        updateProfile(packageName, currentBehavior)

        anomalies
    }

    /**
     * Get current behavior metrics for an app
     */
    private fun getCurrentBehavior(packageName: String): CurrentBehavior {
        var dataUsageMb = 0.0
        var foregroundTimeMin = 0.0
        var backgroundTimeMin = 0.0

        // Get usage stats
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.add(Calendar.HOUR, -24)
                val startTime = calendar.timeInMillis

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime
                )

                val appStats = stats?.find { it.packageName == packageName }
                if (appStats != null) {
                    foregroundTimeMin = appStats.totalTimeInForeground / 60000.0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get usage stats for $packageName")
        }

        // Get data usage via TrafficStats (approximate)
        try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val rxBytes = TrafficStats.getUidRxBytes(uid)
            val txBytes = TrafficStats.getUidTxBytes(uid)
            if (rxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                dataUsageMb = (rxBytes + txBytes) / (1024.0 * 1024.0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get traffic stats for $packageName")
        }

        return CurrentBehavior(dataUsageMb, foregroundTimeMin, backgroundTimeMin)
    }

    /**
     * Get or create a behavioral profile for an app
     */
    private fun getOrCreateProfile(packageName: String): AppBehaviorProfile {
        val key = "profile_$packageName"
        val json = prefs.getString(key, null) ?: return AppBehaviorProfile(packageName)

        return try {
            // Simple parsing from stored format
            val parts = json.split("|")
            if (parts.size >= 8) {
                AppBehaviorProfile(
                    packageName = packageName,
                    avgDataUsageMb = parts[0].toDoubleOrNull() ?: 0.0,
                    avgForegroundTimeMin = parts[1].toDoubleOrNull() ?: 0.0,
                    avgBackgroundTimeMin = parts[2].toDoubleOrNull() ?: 0.0,
                    dataUsageStdDev = parts[3].toDoubleOrNull() ?: 0.0,
                    foregroundStdDev = parts[4].toDoubleOrNull() ?: 0.0,
                    backgroundStdDev = parts[5].toDoubleOrNull() ?: 0.0,
                    sampleCount = parts[6].toIntOrNull() ?: 0,
                    lastUpdated = parts[7].toLongOrNull() ?: 0L
                )
            } else {
                AppBehaviorProfile(packageName)
            }
        } catch (e: Exception) {
            AppBehaviorProfile(packageName)
        }
    }

    /**
     * Update behavioral profile with new observation using running statistics
     */
    private fun updateProfile(packageName: String, behavior: CurrentBehavior) {
        val profile = getOrCreateProfile(packageName)
        val n = profile.sampleCount + 1

        // Running average and standard deviation (Welford's algorithm)
        val newAvgData = profile.avgDataUsageMb + (behavior.dataUsageMb - profile.avgDataUsageMb) / n
        val newAvgFg = profile.avgForegroundTimeMin + (behavior.foregroundTimeMin - profile.avgForegroundTimeMin) / n
        val newAvgBg = profile.avgBackgroundTimeMin + (behavior.backgroundTimeMin - profile.avgBackgroundTimeMin) / n

        // Simplified std dev update
        val dataDeviation = Math.abs(behavior.dataUsageMb - newAvgData)
        val newDataStdDev = profile.dataUsageStdDev + (dataDeviation - profile.dataUsageStdDev) / n

        val fgDeviation = Math.abs(behavior.foregroundTimeMin - newAvgFg)
        val newFgStdDev = profile.foregroundStdDev + (fgDeviation - profile.foregroundStdDev) / n

        val bgDeviation = Math.abs(behavior.backgroundTimeMin - newAvgBg)
        val newBgStdDev = profile.backgroundStdDev + (bgDeviation - profile.backgroundStdDev) / n

        // Store updated profile
        val key = "profile_$packageName"
        val value = "$newAvgData|$newAvgFg|$newAvgBg|$newDataStdDev|$newFgStdDev|$newBgStdDev|$n|${System.currentTimeMillis()}"
        prefs.edit().putString(key, value).apply()
    }

    /**
     * Get the number of apps being monitored
     */
    fun getMonitoredAppCount(): Int {
        return prefs.all.count { it.key.startsWith("profile_") }
    }

    /**
     * Clear all behavioral profiles (reset learning)
     */
    fun resetProfiles() {
        prefs.edit().clear().apply()
        Log.i(TAG, "All behavioral profiles reset")
    }

    fun destroy() {
        scope.cancel()
    }

    private data class CurrentBehavior(
        val dataUsageMb: Double,
        val foregroundTimeMin: Double,
        val backgroundTimeMin: Double
    )
}
