package com.sentinelshield.services.antitheft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Phase 5: SIM Change Detector
 * Detects when the SIM card is changed and alerts the trusted number.
 * This helps identify if a thief has swapped the SIM card.
 */
class SimChangeDetector : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimChangeDetector"
        private const val PREFS_NAME = "antitheft_prefs"
        private const val KEY_STORED_SIM_SERIAL = "stored_sim_serial"
        private const val KEY_STORED_SIM_OPERATOR = "stored_sim_operator"
        private const val KEY_TRUSTED_NUMBER = "trusted_number"
        private const val KEY_SIM_DETECTION_ENABLED = "sim_detection_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.SIM_STATE_CHANGED") return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_SIM_DETECTION_ENABLED, false)
        if (!isEnabled) return

        val simState = intent.getStringExtra("ss") ?: return
        if (simState != "READY") return

        checkSimChange(context)
    }

    /**
     * Check if the current SIM is different from the stored one.
     */
    private fun checkSimChange(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trustedNumber = prefs.getString(KEY_TRUSTED_NUMBER, null) ?: return

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val currentSimSerial = telephonyManager.simSerialNumber ?: "unknown"
            val currentOperator = telephonyManager.simOperatorName ?: "unknown"

            val storedSimSerial = prefs.getString(KEY_STORED_SIM_SERIAL, null)
            val storedOperator = prefs.getString(KEY_STORED_SIM_OPERATOR, null)

            if (storedSimSerial == null) {
                // First time - store current SIM info
                prefs.edit().apply {
                    putString(KEY_STORED_SIM_SERIAL, currentSimSerial)
                    putString(KEY_STORED_SIM_OPERATOR, currentOperator)
                    apply()
                }
                Log.i(TAG, "SIM info stored: $currentOperator")
                return
            }

            // Check if SIM has changed
            if (currentSimSerial != storedSimSerial) {
                Log.w(TAG, "SIM CHANGE DETECTED! Old: $storedSimSerial, New: $currentSimSerial")
                alertSimChange(context, trustedNumber, currentOperator, currentSimSerial)

                // Also try to get location and send it
                val locateIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_LOCATE
                    putExtra("sender", trustedNumber)
                }
                context.startForegroundService(locateIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot read SIM info: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "SIM check failed: ${e.message}")
        }
    }

    /**
     * Send alert SMS to trusted number about SIM change.
     */
    private fun alertSimChange(context: Context, trustedNumber: String, newOperator: String, newSerial: String) {
        try {
            val message = "⚠️ SentinelShield ALERT!\n\n" +
                    "SIM card has been CHANGED on your device!\n\n" +
                    "New operator: $newOperator\n" +
                    "New SIM serial: ${newSerial.takeLast(8)}\n\n" +
                    "If this wasn't you, your device may have been stolen.\n" +
                    "Send SENTINEL_LOCATE_<PIN> to the new number to track it."

            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(trustedNumber, null, parts, null, null)
            Log.i(TAG, "SIM change alert sent to $trustedNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SIM change alert: ${e.message}")
        }
    }

    /**
     * Store current SIM info for future comparison.
     * Call this during initial setup.
     */
    fun storeCurrentSimInfo(context: Context) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simSerial = telephonyManager.simSerialNumber ?: "unknown"
            val operator = telephonyManager.simOperatorName ?: "unknown"

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_STORED_SIM_SERIAL, simSerial)
                putString(KEY_STORED_SIM_OPERATOR, operator)
                apply()
            }
            Log.i(TAG, "SIM info stored: $operator ($simSerial)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot store SIM info - permission denied")
        }
    }
}
