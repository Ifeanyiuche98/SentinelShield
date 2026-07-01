package com.sentinelshield.data.models

/**
 * Represents the result of scanning an installed app.
 */
data class ScanResult(
    val packageName: String,
    val appName: String,
    val riskLevel: RiskLevel,
    val threats: List<String> = emptyList(),
    val hash: String = "",
    val scanTimestamp: Long = System.currentTimeMillis()
)

enum class RiskLevel {
    SAFE, LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Represents an app's permission profile.
 */
data class AppPermissionInfo(
    val packageName: String,
    val appName: String,
    val dangerousPermissions: List<String> = emptyList(),
    val normalPermissions: List<String> = emptyList(),
    val riskScore: Int = 0 // 0-100
)

/**
 * Represents a network connection made by an app.
 */
data class NetworkConnection(
    val sourceApp: String,
    val destinationIp: String,
    val destinationDomain: String = "",
    val port: Int = 0,
    val protocol: String = "TCP",
    val isSuspicious: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a detected overlay/clickjacking threat.
 */
data class OverlayThreat(
    val packageName: String,
    val appName: String,
    val detectionType: String, // "SYSTEM_ALERT_WINDOW", "TYPE_APPLICATION_OVERLAY"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a phishing URL check result.
 */
data class PhishingResult(
    val url: String,
    val isPhishing: Boolean,
    val threatType: String = "", // "phishing", "malware", "scam"
    val source: String = "" // which database flagged it
)

/**
 * Overall device security status.
 */
data class SecurityStatus(
    val isProtected: Boolean = true,
    val threatsFound: Int = 0,
    val lastScanTime: Long = 0L,
    val realTimeProtectionEnabled: Boolean = true,
    val appsScanned: Int = 0,
    val suspiciousConnections: Int = 0
)
