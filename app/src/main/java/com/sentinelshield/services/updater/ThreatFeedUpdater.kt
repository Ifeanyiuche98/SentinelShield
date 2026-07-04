package com.sentinelshield.services.updater

import android.content.Context
import android.util.Log
import com.sentinelshield.data.database.ThreatDatabase
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ThreatFeedUpdater - Automatically pulls threat intelligence from open-source feeds
 * Sources: MalwareBazaar, Abuse.ch URLhaus, PhishTank
 */
class ThreatFeedUpdater(private val context: Context) {

    companion object {
        private const val TAG = "ThreatFeedUpdater"

        // Open-source threat feed URLs
        private const val MALWARE_BAZAAR_RECENT = "https://bazaar.abuse.ch/export/csv/recent/"
        private const val URLHAUS_ONLINE = "https://urlhaus.abuse.ch/downloads/csv_online/"
        private const val FEODO_TRACKER_IPS = "https://feodotracker.abuse.ch/downloads/ipblocklist.csv"
        private const val PHISHTANK_FEED = "https://data.phishtank.com/data/online-valid.csv"

        // Update intervals
        const val UPDATE_INTERVAL_HOURS = 6L
        private const val CONNECTION_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
    }

    private val threatDatabase = ThreatDatabase(context)
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class UpdateResult(
        val malwareHashesAdded: Int = 0,
        val phishingUrlsAdded: Int = 0,
        val maliciousIpsAdded: Int = 0,
        val errors: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Start periodic threat feed updates
     */
    fun startPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                try {
                    performUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic update failed", e)
                }
                delay(UPDATE_INTERVAL_HOURS * 60 * 60 * 1000)
            }
        }
        Log.i(TAG, "Periodic threat feed updates started (every ${UPDATE_INTERVAL_HOURS}h)")
    }

    /**
     * Stop periodic updates
     */
    fun stopPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = null
        Log.i(TAG, "Periodic threat feed updates stopped")
    }

    /**
     * Perform a full threat feed update from all sources
     */
    suspend fun performUpdate(): UpdateResult {
        Log.i(TAG, "Starting threat feed update...")
        val errors = mutableListOf<String>()
        var hashesAdded = 0
        var urlsAdded = 0
        var ipsAdded = 0

        // Update malware hashes from MalwareBazaar
        try {
            hashesAdded = updateMalwareHashes()
            Log.i(TAG, "Added $hashesAdded malware hashes")
        } catch (e: Exception) {
            errors.add("MalwareBazaar: ${e.message}")
            Log.e(TAG, "Failed to update malware hashes", e)
        }

        // Update phishing URLs from URLhaus
        try {
            urlsAdded = updatePhishingUrls()
            Log.i(TAG, "Added $urlsAdded phishing URLs")
        } catch (e: Exception) {
            errors.add("URLhaus: ${e.message}")
            Log.e(TAG, "Failed to update phishing URLs", e)
        }

        // Update malicious IPs from Feodo Tracker
        try {
            ipsAdded = updateMaliciousIps()
            Log.i(TAG, "Added $ipsAdded malicious IPs")
        } catch (e: Exception) {
            errors.add("FeodoTracker: ${e.message}")
            Log.e(TAG, "Failed to update malicious IPs", e)
        }

        val result = UpdateResult(
            malwareHashesAdded = hashesAdded,
            phishingUrlsAdded = urlsAdded,
            maliciousIpsAdded = ipsAdded,
            errors = errors
        )

        // Save last update timestamp
        saveLastUpdateTime(result.timestamp)

        Log.i(TAG, "Threat feed update complete: $hashesAdded hashes, $urlsAdded URLs, $ipsAdded IPs")
        return result
    }

    /**
     * Fetch and parse malware hashes from MalwareBazaar
     */
    private suspend fun updateMalwareHashes(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val connection = createConnection(MALWARE_BAZAAR_RECENT)
        try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("#") && line.isNotBlank()) {
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            val sha256 = parts[1].trim().replace("\"", "")
                            if (sha256.length == 64 && sha256.matches(Regex("[a-fA-F0-9]+"))) {
                                val signature = if (parts.size >= 6) parts[5].trim().replace("\"", "") else "Unknown"
                                threatDatabase.addMalwareHash(sha256, signature)
                                count++
                            }
                        }
                    }
                    if (count >= 5000) return@useLines // Limit to prevent DB bloat
                }
            }
        } finally {
            connection.disconnect()
        }
        count
    }

    /**
     * Fetch and parse phishing/malware URLs from URLhaus
     */
    private suspend fun updatePhishingUrls(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val connection = createConnection(URLHAUS_ONLINE)
        try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("#") && line.isNotBlank()) {
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            val url = parts[2].trim().replace("\"", "")
                            if (url.startsWith("http")) {
                                threatDatabase.addPhishingUrl(url)
                                count++
                            }
                        }
                    }
                    if (count >= 10000) return@useLines
                }
            }
        } finally {
            connection.disconnect()
        }
        count
    }

    /**
     * Fetch and parse malicious IPs from Feodo Tracker
     */
    private suspend fun updateMaliciousIps(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val connection = createConnection(FEODO_TRACKER_IPS)
        try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("#") && line.isNotBlank()) {
                        val ip = line.trim().split(",")[0].replace("\"", "")
                        if (ip.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                            threatDatabase.addMaliciousIp(ip)
                            count++
                        }
                    }
                    if (count >= 5000) return@useLines
                }
            }
        } finally {
            connection.disconnect()
        }
        count
    }

    /**
     * Create HTTP connection with proper timeouts
     */
    private fun createConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "SentinelShield/1.0")
        return connection
    }

    /**
     * Save last update timestamp to SharedPreferences
     */
    private fun saveLastUpdateTime(timestamp: Long) {
        context.getSharedPreferences("sentinel_shield_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_threat_update", timestamp)
            .apply()
    }

    /**
     * Get last update timestamp
     */
    fun getLastUpdateTime(): Long {
        return context.getSharedPreferences("sentinel_shield_prefs", Context.MODE_PRIVATE)
            .getLong("last_threat_update", 0L)
    }

    /**
     * Get threat database statistics
     */
    fun getDatabaseStats(): Map<String, Int> {
        return threatDatabase.getStats()
    }

    fun destroy() {
        stopPeriodicUpdates()
        scope.cancel()
    }
}
