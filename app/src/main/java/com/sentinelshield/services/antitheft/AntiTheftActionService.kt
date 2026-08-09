package com.sentinelshield.services.antitheft

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinelshield.R
import com.sentinelshield.SentinelShieldApp

/**
 * Phase 5: Anti-Theft Action Service
 * Executes anti-theft commands as a foreground service.
 *
 * Actions:
 * - LOCK: Lock device using Device Admin
 * - WIPE: Factory reset (requires Device Admin)
 * - LOCATE: Get GPS location and send via SMS
 * - ALARM: Play loud alarm sound at max volume
 * - PHOTO: Capture front camera photo (intruder detection)
 */
class AntiTheftActionService : Service() {

    companion object {
        private const val TAG = "AntiTheftActionService"
        const val ACTION_LOCK = "com.sentinelshield.ACTION_LOCK"
        const val ACTION_WIPE = "com.sentinelshield.ACTION_WIPE"
        const val ACTION_LOCATE = "com.sentinelshield.ACTION_LOCATE"
        const val ACTION_ALARM = "com.sentinelshield.ACTION_ALARM"
        const val ACTION_PHOTO = "com.sentinelshield.ACTION_PHOTO"
        const val ACTION_STOP_ALARM = "com.sentinelshield.ACTION_STOP_ALARM"

        private const val NOTIFICATION_ID = 5001
        private const val ALARM_DURATION_MS = 60000L  // 1 minute
    }

    private var mediaPlayer: MediaPlayer? = null
    private var alarmHandler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: ""

        when (intent?.action) {
            ACTION_LOCK -> executeLock(sender)
            ACTION_WIPE -> executeWipe(sender)
            ACTION_LOCATE -> executeLocate(sender)
            ACTION_ALARM -> executeAlarm(sender)
            ACTION_PHOTO -> executePhoto(sender)
            ACTION_STOP_ALARM -> stopAlarm()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, SentinelShieldApp.CHANNEL_ID)
            .setContentTitle("SentinelShield Anti-Theft")
            .setContentText("Processing security command...")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Lock the device immediately using Device Policy Manager.
     */
    private fun executeLock(sender: String) {
        Log.i(TAG, "Executing LOCK command")
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, SentinelDeviceAdmin::class.java)

            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                sendSmsResponse(sender, "SentinelShield: Device LOCKED successfully.")
            } else {
                sendSmsResponse(sender, "SentinelShield: Device Admin not active. Cannot lock.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lock failed: ${e.message}")
            sendSmsResponse(sender, "SentinelShield: Lock failed - ${e.message}")
        }
        stopSelf()
    }

    /**
     * Wipe device data (factory reset). DANGEROUS - requires confirmation.
     */
    private fun executeWipe(sender: String) {
        Log.i(TAG, "Executing WIPE command")
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, SentinelDeviceAdmin::class.java)

            if (dpm.isAdminActive(adminComponent)) {
                sendSmsResponse(sender, "SentinelShield: WIPING device in 10 seconds...")
                // Delay wipe to allow SMS to send
                Handler(Looper.getMainLooper()).postDelayed({
                    dpm.wipeData(0)
                }, 10000)
            } else {
                sendSmsResponse(sender, "SentinelShield: Device Admin not active. Cannot wipe.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wipe failed: ${e.message}")
            sendSmsResponse(sender, "SentinelShield: Wipe failed - ${e.message}")
        }
    }

    /**
     * Get device location and send via SMS.
     */
    private fun executeLocate(sender: String) {
        Log.i(TAG, "Executing LOCATE command")
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try to get last known location first
            val lastKnown = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }

            if (lastKnown != null) {
                sendLocationSms(sender, lastKnown)
            } else {
                // Request fresh location
                requestFreshLocation(sender, locationManager)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Locate failed: ${e.message}")
            sendSmsResponse(sender, "SentinelShield: Location failed - ${e.message}")
            stopSelf()
        }
    }

    private fun requestFreshLocation(sender: String, locationManager: LocationManager) {
        try {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    sendLocationSms(sender, location)
                    try {
                        locationManager.removeUpdates(this)
                    } catch (e: Exception) { /* ignore */ }
                    stopSelf()
                }

                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L, 0f, locationListener,
                Looper.getMainLooper()
            )

            // Timeout after 30 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    locationManager.removeUpdates(locationListener)
                } catch (e: Exception) { /* ignore */ }
                sendSmsResponse(sender, "SentinelShield: Location timeout. GPS may be disabled.")
                stopSelf()
            }, 30000)

        } catch (e: SecurityException) {
            sendSmsResponse(sender, "SentinelShield: Location permission not granted.")
            stopSelf()
        }
    }

    private fun sendLocationSms(sender: String, location: Location) {
        val googleMapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val accuracy = "±${location.accuracy.toInt()}m"
        val message = "SentinelShield LOCATION:\n" +
                "Lat: ${location.latitude}\n" +
                "Lng: ${location.longitude}\n" +
                "Accuracy: $accuracy\n" +
                "Map: $googleMapsLink"
        sendSmsResponse(sender, message)
    }

    /**
     * Trigger a loud alarm at maximum volume.
     */
    private fun executeAlarm(sender: String) {
        Log.i(TAG, "Executing ALARM command")
        try {
            // Set volume to maximum
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Also maximize ring and media volume
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0
            )
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
            )

            // Play alarm sound
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AntiTheftActionService, alarmUri)
                isLooping = true
                prepare()
                start()
            }

            // Auto-stop after duration
            alarmHandler = Handler(Looper.getMainLooper())
            alarmHandler?.postDelayed({
                stopAlarm()
                stopSelf()
            }, ALARM_DURATION_MS)

            sendSmsResponse(sender, "SentinelShield: ALARM activated for 60 seconds!")

            // Update notification with stop action
            val stopIntent = Intent(this, AntiTheftActionService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, SentinelShieldApp.CHANNEL_ID)
                .setContentTitle("⚠️ ANTI-THEFT ALARM ACTIVE")
                .setContentText("Tap to stop alarm")
                .setSmallIcon(R.drawable.ic_shield)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.ic_shield, "STOP ALARM", stopPendingIntent)
                .setOngoing(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

        } catch (e: Exception) {
            Log.e(TAG, "Alarm failed: ${e.message}")
            sendSmsResponse(sender, "SentinelShield: Alarm failed - ${e.message}")
            stopSelf()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        alarmHandler?.removeCallbacksAndMessages(null)
        alarmHandler = null
    }

    /**
     * Capture photo using front camera (intruder detection).
     * Note: This requires the camera permission and works best when device is unlocked.
     */
    private fun executePhoto(sender: String) {
        Log.i(TAG, "Executing PHOTO command")
        try {
            val photoIntent = Intent(this, IntruderCameraService::class.java).apply {
                putExtra("sender", sender)
            }
            startService(photoIntent)
            sendSmsResponse(sender, "SentinelShield: Attempting to capture intruder photo...")
        } catch (e: Exception) {
            Log.e(TAG, "Photo capture failed: ${e.message}")
            sendSmsResponse(sender, "SentinelShield: Photo capture failed - ${e.message}")
        }
        stopSelf()
    }

    /**
     * Send SMS response back to the command sender.
     */
    private fun sendSmsResponse(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank()) return
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.i(TAG, "SMS response sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS response: ${e.message}")
        }
    }
}
