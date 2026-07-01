package com.sentinelshield.services.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Receives BOOT_COMPLETED broadcast and restarts
 * the real-time protection service automatically.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE)
            val protectionEnabled = prefs.getBoolean("realtime_protection", true)

            if (protectionEnabled) {
                val serviceIntent = Intent(context, RealTimeProtectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
