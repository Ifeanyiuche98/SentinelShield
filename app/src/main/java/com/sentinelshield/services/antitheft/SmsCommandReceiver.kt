package com.sentinelshield.services.antitheft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Phase 5: SMS Command Receiver
 * Listens for incoming SMS messages and processes anti-theft commands.
 *
 * Supported commands (sent via SMS with a secret PIN prefix):
 * - "SENTINEL_LOCK_<PIN>" - Lock the device remotely
 * - "SENTINEL_WIPE_<PIN>" - Wipe device data remotely
 * - "SENTINEL_LOCATE_<PIN>" - Get device GPS location
 * - "SENTINEL_ALARM_<PIN>" - Trigger loud alarm
 * - "SENTINEL_PHOTO_<PIN>" - Take front camera photo
 */
class SmsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsCommandReceiver"
        private const val COMMAND_PREFIX = "SENTINEL_"
        private const val PREFS_NAME = "antitheft_prefs"
        private const val KEY_PIN = "security_pin"
        private const val KEY_ENABLED = "antitheft_enabled"
        private const val KEY_TRUSTED_NUMBER = "trusted_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_ENABLED, false)
        if (!isEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val storedPin = prefs.getString(KEY_PIN, null) ?: return
        val trustedNumber = prefs.getString(KEY_TRUSTED_NUMBER, null)

        for (sms in messages) {
            val body = sms.messageBody?.trim() ?: continue
            val sender = sms.originatingAddress ?: continue

            // Only process commands from trusted number if set
            if (trustedNumber != null && !sender.endsWith(trustedNumber.takeLast(10))) {
                continue
            }

            if (body.startsWith(COMMAND_PREFIX)) {
                processCommand(context, body, storedPin, sender)
            }
        }
    }

    private fun processCommand(context: Context, message: String, storedPin: String, sender: String) {
        val parts = message.removePrefix(COMMAND_PREFIX).split("_")
        if (parts.size < 2) return

        val command = parts[0]
        val pin = parts.last()

        // Verify PIN
        if (pin != storedPin) {
            Log.w(TAG, "Invalid PIN attempt from $sender")
            return
        }

        Log.i(TAG, "Processing anti-theft command: $command from $sender")

        when (command) {
            "LOCK" -> {
                val lockIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_LOCK
                    putExtra("sender", sender)
                }
                context.startForegroundService(lockIntent)
            }
            "WIPE" -> {
                val wipeIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_WIPE
                    putExtra("sender", sender)
                }
                context.startForegroundService(wipeIntent)
            }
            "LOCATE" -> {
                val locateIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_LOCATE
                    putExtra("sender", sender)
                }
                context.startForegroundService(locateIntent)
            }
            "ALARM" -> {
                val alarmIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_ALARM
                    putExtra("sender", sender)
                }
                context.startForegroundService(alarmIntent)
            }
            "PHOTO" -> {
                val photoIntent = Intent(context, AntiTheftActionService::class.java).apply {
                    action = AntiTheftActionService.ACTION_PHOTO
                    putExtra("sender", sender)
                }
                context.startForegroundService(photoIntent)
            }
            else -> Log.w(TAG, "Unknown command: $command")
        }
    }
}
