package com.sentinelshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.sentinelshield.notifications.NotificationHelper
import com.sentinelshield.services.ai.AIThreatScanWorker
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
        initializeServices()
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
     * Initialize all background services:
     * Phase 3: Threat database auto-update, behavioral analysis
     * Phase 4: AI-powered threat scanning
     */
    private fun initializeServices() {
        try {
            // Initialize notification channels for all alert types
            NotificationHelper(this)

            // Phase 3: Schedule periodic threat feed updates (every 6 hours)
            ThreatUpdateWorker.schedule(this)

            // Phase 3: Schedule periodic behavioral analysis (every 4 hours)
            BehavioralMonitorWorker.schedule(this)

            // Phase 4: Schedule AI-powered threat scans (every 8 hours)
            AIThreatScanWorker.schedule(this)

            Log.i(TAG, "All services initialized: threat updates + behavioral monitor + AI engine")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize services", e)
        }
    }
}
