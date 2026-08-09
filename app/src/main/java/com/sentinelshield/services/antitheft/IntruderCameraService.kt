package com.sentinelshield.services.antitheft

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinelshield.R
import com.sentinelshield.SentinelShieldApp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 5: Intruder Camera Service
 * Captures a photo using the front camera when:
 * - Failed unlock attempts exceed threshold
 * - Remote PHOTO command is received
 *
 * Photos are stored locally and can be retrieved later.
 */
class IntruderCameraService : Service() {

    companion object {
        private const val TAG = "IntruderCameraService"
        private const val NOTIFICATION_ID = 5002
        private const val PHOTO_DIR = "intruder_photos"
    }

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startBackgroundThread()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: ""
        val trigger = intent?.getStringExtra("trigger") ?: "remote_command"
        val attempts = intent?.getIntExtra("attempts", 0) ?: 0

        Log.i(TAG, "Intruder photo requested. Trigger: $trigger, Attempts: $attempts")
        capturePhoto(sender, trigger, attempts)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopBackgroundThread()
        cameraDevice?.close()
        imageReader?.close()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, SentinelShieldApp.CHANNEL_ID)
            .setContentTitle("SentinelShield Security")
            .setContentText("Processing security action...")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Background thread interrupted", e)
        }
    }

    /**
     * Capture a photo using the front camera.
     */
    private fun capturePhoto(sender: String, trigger: String, attempts: Int) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // Find front-facing camera
            val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }

            if (frontCameraId == null) {
                Log.e(TAG, "No front camera found")
                if (sender.isNotBlank()) {
                    sendSmsResponse(sender, "SentinelShield: No front camera available.")
                }
                stopSelf()
                return
            }

            // Set up ImageReader
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    // Save photo
                    val photoFile = savePhoto(bytes, trigger, attempts)
                    Log.i(TAG, "Intruder photo saved: ${photoFile?.absolutePath}")

                    if (sender.isNotBlank()) {
                        sendSmsResponse(sender, "SentinelShield: Intruder photo captured and saved.")
                    }

                    // Show notification about captured photo
                    showPhotoNotification(trigger, attempts)
                }
                stopSelf()
            }, backgroundHandler)

            // Open camera
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    stopSelf()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    if (sender.isNotBlank()) {
                        sendSmsResponse(sender, "SentinelShield: Camera error ($error)")
                    }
                    stopSelf()
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted: ${e.message}")
            if (sender.isNotBlank()) {
                sendSmsResponse(sender, "SentinelShield: Camera permission not granted.")
            }
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Camera capture failed: ${e.message}")
            stopSelf()
        }
    }

    private fun createCaptureSession(camera: CameraDevice) {
        try {
            val surface = imageReader!!.surface

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            }
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "Capture request failed: ${e.message}")
                            stopSelf()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera session configuration failed")
                        stopSelf()
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create capture session failed: ${e.message}")
            stopSelf()
        }
    }

    /**
     * Save captured photo to internal storage.
     */
    private fun savePhoto(bytes: ByteArray, trigger: String, attempts: Int): File? {
        return try {
            val photoDir = File(filesDir, PHOTO_DIR)
            if (!photoDir.exists()) photoDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "intruder_${trigger}_${timestamp}.jpg"
            val photoFile = File(photoDir, fileName)

            FileOutputStream(photoFile).use { fos ->
                fos.write(bytes)
            }

            // Store metadata
            val prefs = getSharedPreferences("intruder_photos", Context.MODE_PRIVATE)
            val photoList = prefs.getStringSet("photo_list", mutableSetOf()) ?: mutableSetOf()
            photoList.add("$fileName|$trigger|$attempts|${System.currentTimeMillis()}")
            prefs.edit().putStringSet("photo_list", photoList).apply()

            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo: ${e.message}")
            null
        }
    }

    private fun showPhotoNotification(trigger: String, attempts: Int) {
        val title = when (trigger) {
            "failed_unlock" -> "⚠️ Intruder Detected!"
            else -> "📸 Security Photo Captured"
        }
        val text = when (trigger) {
            "failed_unlock" -> "Photo taken after $attempts failed unlock attempts"
            else -> "Remote capture completed"
        }

        val notification = NotificationCompat.Builder(this, SentinelShieldApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun sendSmsResponse(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
        }
    }
}
