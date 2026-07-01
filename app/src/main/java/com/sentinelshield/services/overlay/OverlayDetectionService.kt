package com.sentinelshield.services.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.sentinelshield.R

/**
 * Accessibility service that detects overlay/clickjacking attacks.
 * Monitors for suspicious overlay windows that may attempt to
 * intercept user input or obscure legitimate UI elements.
 */
class OverlayDetectionService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "overlay_detection_channel"
        const val NOTIFICATION_ID = 2001
        var isRunning = false
            private set

        // Track detected overlays
        val detectedOverlays = mutableListOf<OverlayEvent>()
    }

    data class OverlayEvent(
        val packageName: String,
        val eventType: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Known legitimate overlay apps (system UI, keyboards, etc.)
    private val whitelistedPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info

        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkForOverlay(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Monitor for content changes that might indicate overlay manipulation
            }
        }
    }

    /**
     * Check if the window event indicates a suspicious overlay.
     */
    private fun checkForOverlay(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Skip whitelisted packages
        if (whitelistedPackages.contains(packageName)) return
        if (packageName == this.packageName) return

        // Check active windows for overlays
        try {
            val windows = windows
            if (windows != null && windows.size > 2) {
                // Multiple windows active - potential overlay
                windows.forEach { window ->
                    val windowPackage = window.root?.packageName?.toString()
                    if (windowPackage != null &&
                        !whitelistedPackages.contains(windowPackage) &&
                        windowPackage != packageName &&
                        window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                    ) {
                        // Potential overlay detected
                        val overlayEvent = OverlayEvent(
                            packageName = windowPackage,
                            eventType = "SUSPICIOUS_OVERLAY"
                        )
                        detectedOverlays.add(overlayEvent)
                        notifyUser(windowPackage)
                    }
                }
            }
        } catch (e: Exception) {
            // Security exception or windows not available
        }
    }

    /**
     * Send notification to user about detected overlay.
     */
    private fun notifyUser(suspiciousPackage: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("⚠️ Overlay Detected")
            .setContentText("App '$suspiciousPackage' is drawing over other apps. This could be a clickjacking attempt.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + detectedOverlays.size, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected overlay/clickjacking attempts"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
