package com.sentinelshield.services.antitheft

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Phase 5: Device Admin Receiver
 * Enables device administration capabilities for anti-theft features:
 * - Remote lock
 * - Remote wipe (factory reset)
 * - Password policy enforcement
 * - Failed unlock attempt monitoring
 */
class SentinelDeviceAdmin : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SentinelDeviceAdmin"
        private const val PREFS_NAME = "antitheft_prefs"
        private const val KEY_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
        private const val KEY_FAILED_ATTEMPT_ACTION = "failed_attempt_action"
        private const val DEFAULT_MAX_ATTEMPTS = 5
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
        Toast.makeText(context, "SentinelShield: Device Admin activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled")
        Toast.makeText(context, "SentinelShield: Device Admin deactivated", Toast.LENGTH_SHORT).show()
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Password attempt failed")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentFails = prefs.getInt("consecutive_fails", 0) + 1
        prefs.edit().putInt("consecutive_fails", currentFails).apply()

        val maxAttempts = prefs.getInt(KEY_MAX_FAILED_ATTEMPTS, DEFAULT_MAX_ATTEMPTS)

        if (currentFails >= maxAttempts) {
            Log.w(TAG, "Max failed attempts reached ($currentFails). Triggering intruder photo.")
            // Trigger intruder photo capture
            try {
                val photoIntent = Intent(context, IntruderCameraService::class.java).apply {
                    putExtra("trigger", "failed_unlock")
                    putExtra("attempts", currentFails)
                }
                context.startService(photoIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start intruder camera: ${e.message}")
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        // Reset failed attempt counter on successful unlock
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("consecutive_fails", 0).apply()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling SentinelShield Device Admin will remove anti-theft protection. Are you sure?"
    }
}
