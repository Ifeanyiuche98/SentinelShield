package com.sentinelshield.services.ai

import android.content.Context
import android.util.Log
import com.sentinelshield.data.models.ThreatAlert
import com.sentinelshield.notifications.NotificationHelper
import kotlinx.coroutines.*

/**
 * Phase 4: Central AI Threat Engine that orchestrates all intelligent
 * threat detection capabilities.
 *
 * Combines:
 * - TFLite behavioral classification
 * - Statistical anomaly detection (from Phase 3 BehavioralAnalyzer)
 * - Real-time feature collection
 * - Threat scoring and alerting
 */
class AIThreatEngine(private val context: Context) {

    companion object {
        private const val TAG = "AIThreatEngine"

        // Confidence thresholds for alerting
        private const val ALERT_THRESHOLD_SUSPICIOUS = 0.6f
        private const val ALERT_THRESHOLD_MALICIOUS = 0.4f
    }

    private val classifier = AppBehaviorClassifier(context)
    private val dataCollector = BehaviorDataCollector(context)
    private val notificationHelper = NotificationHelper(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Store historical classifications for trend analysis
    private val classificationHistory = mutableMapOf<String, MutableList<BehaviorClassification>>()

    /**
     * Run a full AI-powered threat scan on all installed apps.
     * Returns a list of detected threats with confidence scores.
     */
    suspend fun runFullScan(): List<AIThreatResult> = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting full AI threat scan...")
        val results = mutableListOf<AIThreatResult>()

        val allFeatures = dataCollector.collectAllAppFeatures()

        allFeatures.forEach { (packageName, features) ->
            try {
                val classification = classifier.classify(features)

                // Store in history for trend analysis
                classificationHistory.getOrPut(packageName) { mutableListOf() }.apply {
                    add(classification)
                    // Keep last 10 classifications
                    if (size > 10) removeAt(0)
                }

                if (classification.isThreat) {
                    val trendScore = calculateTrendScore(packageName)
                    val appName = getAppName(packageName)

                    val result = AIThreatResult(
                        packageName = packageName,
                        appName = appName,
                        classification = classification,
                        features = features,
                        trendScore = trendScore,
                        recommendation = getRecommendation(classification, trendScore)
                    )
                    results.add(result)

                    // Send notification for high-confidence threats
                    if (shouldAlert(classification)) {
                        sendThreatNotification(result)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to classify $packageName: ${e.message}")
            }
        }

        Log.i(TAG, "AI scan complete: ${results.size} threats detected out of ${allFeatures.size} apps")
        results.sortedByDescending { it.classification.confidence }
    }

    /**
     * Scan a single app (used for real-time monitoring of newly installed apps).
     */
    suspend fun scanApp(packageName: String): AIThreatResult? = withContext(Dispatchers.Default) {
        try {
            val features = dataCollector.collectFeatures(packageName)
            val classification = classifier.classify(features)

            classificationHistory.getOrPut(packageName) { mutableListOf() }.apply {
                add(classification)
                if (size > 10) removeAt(0)
            }

            if (classification.isThreat) {
                val trendScore = calculateTrendScore(packageName)
                val appName = getAppName(packageName)

                val result = AIThreatResult(
                    packageName = packageName,
                    appName = appName,
                    classification = classification,
                    features = features,
                    trendScore = trendScore,
                    recommendation = getRecommendation(classification, trendScore)
                )

                if (shouldAlert(classification)) {
                    sendThreatNotification(result)
                }

                return@withContext result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan $packageName: ${e.message}")
        }
        null
    }

    /**
     * Calculate a trend score based on classification history.
     * Higher score = consistently flagged over time (more concerning).
     */
    private fun calculateTrendScore(packageName: String): Float {
        val history = classificationHistory[packageName] ?: return 0f
        if (history.size < 2) return 0f

        val threatCount = history.count { it.isThreat }
        return (threatCount.toFloat() / history.size).coerceIn(0f, 1f)
    }

    /**
     * Determine if a classification warrants an alert notification.
     */
    private fun shouldAlert(classification: BehaviorClassification): Boolean {
        return when (classification.classification) {
            AppBehaviorClassifier.CLASS_MALICIOUS ->
                classification.confidence >= ALERT_THRESHOLD_MALICIOUS
            AppBehaviorClassifier.CLASS_SUSPICIOUS ->
                classification.confidence >= ALERT_THRESHOLD_SUSPICIOUS
            else -> false
        }
    }

    /**
     * Generate a human-readable recommendation based on the threat.
     */
    private fun getRecommendation(classification: BehaviorClassification, trendScore: Float): String {
        return when {
            classification.classification == AppBehaviorClassifier.CLASS_MALICIOUS && trendScore > 0.7f ->
                "CRITICAL: This app consistently shows malicious behavior. Uninstall immediately."
            classification.classification == AppBehaviorClassifier.CLASS_MALICIOUS ->
                "HIGH RISK: This app exhibits malicious behavior patterns. Consider uninstalling."
            classification.classification == AppBehaviorClassifier.CLASS_SUSPICIOUS && trendScore > 0.5f ->
                "WARNING: This app repeatedly shows suspicious activity. Monitor closely or restrict permissions."
            classification.classification == AppBehaviorClassifier.CLASS_SUSPICIOUS ->
                "CAUTION: Unusual behavior detected. Review app permissions and recent activity."
            else -> "No immediate action required."
        }
    }

    /**
     * Send a threat notification to the user.
     */
    private fun sendThreatNotification(result: AIThreatResult) {
        val severity = when (result.classification.classification) {
            AppBehaviorClassifier.CLASS_MALICIOUS -> "Critical"
            AppBehaviorClassifier.CLASS_SUSPICIOUS -> "Medium"
            else -> "Low"
        }

        notificationHelper.showThreatDetected(
            appName = result.appName,
            packageName = result.packageName,
            threatDescription = result.recommendation,
            severity = severity
        )
    }

    /**
     * Get app display name from package name.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Get summary statistics from the AI engine.
     */
    fun getStats(): AIEngineStats {
        val totalAppsMonitored = classificationHistory.size
        val currentThreats = classificationHistory.count { (_, history) ->
            history.lastOrNull()?.isThreat == true
        }
        val suspiciousApps = classificationHistory.count { (_, history) ->
            history.lastOrNull()?.classification == AppBehaviorClassifier.CLASS_SUSPICIOUS
        }
        val maliciousApps = classificationHistory.count { (_, history) ->
            history.lastOrNull()?.classification == AppBehaviorClassifier.CLASS_MALICIOUS
        }

        return AIEngineStats(
            totalAppsMonitored = totalAppsMonitored,
            currentThreats = currentThreats,
            suspiciousApps = suspiciousApps,
            maliciousApps = maliciousApps,
            modelLoaded = classifier.toString().contains("tflite") // Check if model is active
        )
    }

    /**
     * Clean up resources.
     */
    fun shutdown() {
        scope.cancel()
        classifier.close()
    }
}

/**
 * Result from AI threat analysis.
 */
data class AIThreatResult(
    val packageName: String,
    val appName: String,
    val classification: BehaviorClassification,
    val features: BehaviorFeatures,
    val trendScore: Float,
    val recommendation: String
)

/**
 * AI Engine statistics.
 */
data class AIEngineStats(
    val totalAppsMonitored: Int,
    val currentThreats: Int,
    val suspiciousApps: Int,
    val maliciousApps: Int,
    val modelLoaded: Boolean
)
