package com.sentinelshield.services.scanner

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinelshield.notifications.NotificationHelper

/**
 * LinkInterceptorService - Real-time URL interception and scanning.
 * 
 * Uses AccessibilityService to detect when URLs are being opened in browsers
 * and checks them against the phishing database before they load.
 */
class LinkInterceptorService : AccessibilityService() {

    companion object {
        private const val TAG = "LinkInterceptor"

        // Known browser package names
        private val BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.UCMobile.intl",
            "com.sec.android.app.sbrowser",
            "com.vivaldi.browser"
        )
    }

    private lateinit var phishingGuard: PhishingGuard
    private lateinit var notificationHelper: NotificationHelper
    private var lastCheckedUrl: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        phishingGuard = PhishingGuard(this)
        notificationHelper = NotificationHelper(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 300
            packageNames = BROWSER_PACKAGES.toTypedArray()
        }
        serviceInfo = info

        Log.i(TAG, "Link interceptor service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val source = event.source ?: return
            val url = extractUrlFromNode(source)

            if (url != null && url != lastCheckedUrl && url.startsWith("http")) {
                lastCheckedUrl = url
                checkUrl(url)
            }

            source.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Error processing accessibility event: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Link interceptor service interrupted")
    }

    /**
     * Extract URL from accessibility node (browser address bar)
     */
    private fun extractUrlFromNode(node: AccessibilityNodeInfo): String? {
        // Try to find URL bar by common view IDs
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.opera.browser:id/url_field",
            "com.brave.browser:id/url_bar"
        )

        for (id in urlBarIds) {
            val nodes = node.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (text != null && (text.contains(".") || text.startsWith("http"))) {
                    return if (text.startsWith("http")) text else "https://$text"
                }
            }
        }

        // Fallback: check if node text looks like a URL
        val text = node.text?.toString()
        if (text != null && text.matches(Regex("https?://[\\w\\-./]+"))) {
            return text
        }

        return null
    }

    /**
     * Check URL against phishing database and heuristics
     */
    private fun checkUrl(url: String) {
        try {
            val analysis = phishingGuard.analyzeUrl(url)

            if (!analysis.isSafe) {
                Log.w(TAG, "Suspicious URL detected: $url (risk: ${analysis.riskScore})")
                notificationHelper.showPhishingWarning(url, analysis.riskScore)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL: ${e.message}")
        }
    }
}
