package com.sentinelshield.services.protection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sentinelshield.R
import com.sentinelshield.data.models.RiskLevel
import com.sentinelshield.services.scanner.MalwareScanner

/**
 * Background service providing real-time protection.
 * Monitors for new app installations and automatically scans them.
 * Runs as a foreground service with a persistent notification.
 */
class RealTimeProtectionService : Service() {

    companion object {
        const val CHANNEL_ID = "realtime_protection_channel"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
            private set
        var lastScannedApp: String = ""
            private set
        var threatsBlocked: Int = 0
            private set
    }

    private lateinit var scanner: MalwareScanner
    private var packageReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        scanner = MalwareScanner(this)
        createNotificationChannel()
        registerPackageReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        showProtectionNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Register BroadcastReceiver to listen for package changes.
     */
    private fun registerPackageReceiver() {
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val packageName = intent.data?.schemeSpecificPart ?: return

                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        // New app installed - scan it immediately
                        onNewAppInstalled(packageName)
                    }
                    Intent.ACTION_PACKAGE_REPLACED -> {
                        // App updated - re-scan
                        onAppUpdated(packageName)
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        // App removed - log it
                        lastScannedApp = "Removed: $packageName"
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        registerReceiver(packageReceiver, filter)
    }

    /**
     * Handle new app installation - auto scan.
     */
    private fun onNewAppInstalled(packageName: String) {
        lastScannedApp = packageName

        // Scan the newly installed app
        val result = scanner.scanAppByPackage(packageName)

        if (result != null && result.riskLevel >= RiskLevel.MEDIUM) {
            threatsBlocked++
            notifyThreatDetected(result.appName, result.riskLevel, result.threats)
        }

        // Update the persistent notification
        updateProtectionNotification()
    }

    /**
     * Handle app update - re-scan for trojaned updates.
     */
    private fun onAppUpdated(packageName: String) {
        lastScannedApp = "$packageName (updated)"
        val result = scanner.scanAppByPackage(packageName)

        if (result != null && result.riskLevel >= RiskLevel.MEDIUM) {
            threatsBlocked++
            notifyThreatDetected(result.appName, result.riskLevel, result.threats)
        }
    }

    /**
     * Notify user about a detected threat in a new/updated app.
     */
    private fun notifyThreatDetected(appName: String, riskLevel: RiskLevel, threats: List<String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val threatSummary = threats.take(2).joinToString(", ")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("🚨 Threat Detected: $appName")
            .setContentText("Risk: ${riskLevel.name} - $threatSummary")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Risk Level: ${riskLevel.name}\n\nThreats found:\n${threats.joinToString("\n• ", "• ")}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + threatsBlocked, notification)
    }

    /**
     * Show the persistent foreground notification.
     */
    private fun showProtectionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("SentinelShield Active")
            .setContentText("Real-time protection is enabled. Your device is protected.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Update the persistent notification with latest stats.
     */
    private fun updateProtectionNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("SentinelShield Active")
            .setContentText("Protected | Threats blocked: $threatsBlocked")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Real-time Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for real-time protection status"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        packageReceiver?.let { unregisterReceiver(it) }
    }
}
