package com.sentinelshield.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sentinelshield.MainActivity
import com.sentinelshield.R
import com.sentinelshield.services.behavioral.BehavioralAnalyzer

/**
 * NotificationHelper - Smart alert system for SentinelShield.
 * 
 * Provides actionable notifications for:
 * - Threat detection (malware found)
 * - Behavioral anomalies
 * - Phishing link warnings
 * - Scan completion reports
 * - Threat database update status
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_THREATS = "sentinel_threats"
        const val CHANNEL_SCANS = "sentinel_scans"
        const val CHANNEL_UPDATES = "sentinel_updates"
        const val CHANNEL_BEHAVIORAL = "sentinel_behavioral"

        private const val NOTIFICATION_THREAT_BASE = 1000
        private const val NOTIFICATION_SCAN = 2000
        private const val NOTIFICATION_UPDATE = 3000
        private const val NOTIFICATION_BEHAVIORAL = 4000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val threatChannel = NotificationChannel(
                CHANNEL_THREATS,
                "Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when threats are detected"
                enableVibration(true)
                setShowBadge(true)
            }

            val scanChannel = NotificationChannel(
                CHANNEL_SCANS,
                "Scan Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about scan completion and results"
            }

            val updateChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "Database Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Threat database update notifications"
            }

            val behavioralChannel = NotificationChannel(
                CHANNEL_BEHAVIORAL,
                "Behavioral Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts about suspicious app behavior"
            }

            manager.createNotificationChannels(
                listOf(threatChannel, scanChannel, updateChannel, behavioralChannel)
            )
        }
    }

    /**
     * Show threat detected notification with actions
     */
    fun showThreatDetected(
        appName: String,
        packageName: String,
        threatDescription: String,
        severity: String = "High"
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "scan_results")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Threat Detected: $appName")
            .setContentText(threatDescription)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$threatDescription\n\nSeverity: $severity\nPackage: $packageName\n\nTap to view details and take action."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_THREAT_BASE + packageName.hashCode() % 999, notification)
    }

    /**
     * Show phishing URL warning
     */
    fun showPhishingWarning(url: String, riskScore: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val riskLevel = when {
            riskScore >= 80 -> "Critical"
            riskScore >= 60 -> "High"
            riskScore >= 40 -> "Medium"
            else -> "Low"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚫 Phishing Link Blocked")
            .setContentText("A suspicious link was detected (Risk: $riskLevel)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("A suspicious URL was detected and blocked.\n\nURL: ${url.take(80)}...\nRisk Level: $riskLevel ($riskScore/100)\n\nDo not enter any personal information on this site."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_THREAT_BASE + url.hashCode() % 999, notification)
    }

    /**
     * Show behavioral anomaly alert
     */
    fun showBehavioralAnomaly(anomaly: BehavioralAnalyzer.BehaviorAnomaly) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "permission_auditor")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val severityText = when {
            anomaly.severity >= 0.8f -> "High"
            anomaly.severity >= 0.5f -> "Medium"
            else -> "Low"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BEHAVIORAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔍 Unusual Behavior: ${anomaly.appName}")
            .setContentText(anomaly.description)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${anomaly.description}\n\nType: ${anomaly.anomalyType.name}\nSeverity: $severityText\n\nTap to investigate."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_BEHAVIORAL + anomaly.packageName.hashCode() % 999, notification)
    }

    /**
     * Show scan completion notification
     */
    fun showScanComplete(appsScanned: Int, threatsFound: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "scan_results")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (threatsFound > 0) "⚠️ Scan Complete - $threatsFound Threats Found"
        else "✅ Scan Complete - Device Secure"

        val text = if (threatsFound > 0) "Scanned $appsScanned apps. Found $threatsFound potential threats. Tap to review."
        else "Scanned $appsScanned apps. No threats detected."

        val notification = NotificationCompat.Builder(context, CHANNEL_SCANS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (threatsFound > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_SCAN, notification)
    }

    /**
     * Show threat database update notification
     */
    fun showDatabaseUpdated(hashesAdded: Int, urlsAdded: Int, ipsAdded: Int) {
        val total = hashesAdded + urlsAdded + ipsAdded
        if (total == 0) return // Don't notify if nothing was added

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("🛡️ Threat Database Updated")
            .setContentText("Added $total new threat signatures")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Threat database updated successfully.\n\n• $hashesAdded malware hashes\n• $urlsAdded phishing URLs\n• $ipsAdded malicious IPs\n\nYour protection is up to date."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_UPDATE, notification)
    }

    /**
     * Show overlay/clickjacking detection alert
     */
    fun showOverlayDetected(overlayAppName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Clickjacking Attempt Detected")
            .setContentText("$overlayAppName is drawing over other apps")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$overlayAppName is displaying an overlay on your screen.\n\nThis could be a clickjacking attempt to trick you into tapping something you didn't intend to.\n\nBe cautious and avoid entering sensitive information."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_THREAT_BASE + overlayAppName.hashCode() % 999, notification)
    }
}
