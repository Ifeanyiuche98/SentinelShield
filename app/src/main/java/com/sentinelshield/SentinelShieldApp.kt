package com.sentinelshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SentinelShieldApp : Application() {

    companion object {
        const val CHANNEL_ID = "sentinel_shield_protection"
        const val CHANNEL_NAME = "Real-time Protection"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
}
