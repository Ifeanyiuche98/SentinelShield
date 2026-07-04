package com.sentinelshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.sentinelshield.notifications.NotificationHelper
import com.sentinelshield.services.behavioral.BehavioralMonitorWorker
import com.sentinelshield.services.updater.ThreatUpdateWorker

class SentinelShieldApp : Application() {

    companion object {
        const val CHANNEL_ID = "sentinel_shield_protection"
        const val CHANNEL_NAME = "Real-time Protection"
        private const val TAG = "SentinelShieldApp"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePhase3Services()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SentinelShield real-time protection notifications"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Initialize Phase 3 services:
     * - Threat database auto-update worker
     * - Behavioral analysis monitor
     * - Notification channels for all alert types
     */
    private fun initializePhase3Services() {
        try {
            // Initialize notification channels for all alert types
            NotificationHelper(this)

            // Schedule periodic threat feed updates (every 6 hours)
            ThreatUpdateWorker.schedule(this)

            // Schedule periodic behavioral analysis (every 4 hours)
            BehavioralMonitorWorker.schedule(this)

            Log.i(TAG, "Phase 3 services initialized: auto-update + behavioral monitor")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Phase 3 services", e)
        }
    }
}
