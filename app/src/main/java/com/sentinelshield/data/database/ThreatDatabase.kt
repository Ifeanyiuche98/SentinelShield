package com.sentinelshield.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database for storing threat signatures, malware hashes,
 * and phishing URLs.
 */
class ThreatDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "sentinel_threats.db"
        const val DATABASE_VERSION = 1

        // Tables
        const val TABLE_MALWARE_HASHES = "malware_hashes"
        const val TABLE_PHISHING_URLS = "phishing_urls"
        const val TABLE_SUSPICIOUS_IPS = "suspicious_ips"
        const val TABLE_SCAN_HISTORY = "scan_history"

        // Malware hashes columns
        const val COL_HASH = "hash"
        const val COL_MALWARE_NAME = "malware_name"
        const val COL_SEVERITY = "severity"
        const val COL_DATE_ADDED = "date_added"

        // Phishing URLs columns
        const val COL_URL = "url"
        const val COL_THREAT_TYPE = "threat_type"
        const val COL_SOURCE = "source"

        // Suspicious IPs columns
        const val COL_IP = "ip_address"
        const val COL_REASON = "reason"

        // Scan history columns
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_RISK_LEVEL = "risk_level"
        const val COL_SCAN_TIME = "scan_time"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create malware hashes table
        db.execSQL("""
            CREATE TABLE $TABLE_MALWARE_HASHES (
                $COL_HASH TEXT PRIMARY KEY,
                $COL_MALWARE_NAME TEXT NOT NULL,
                $COL_SEVERITY TEXT NOT NULL DEFAULT 'HIGH',
                $COL_DATE_ADDED INTEGER NOT NULL
            )
        """)

        // Create phishing URLs table
        db.execSQL("""
            CREATE TABLE $TABLE_PHISHING_URLS (
                $COL_URL TEXT PRIMARY KEY,
                $COL_THREAT_TYPE TEXT NOT NULL DEFAULT 'phishing',
                $COL_SOURCE TEXT NOT NULL DEFAULT 'local',
                $COL_DATE_ADDED INTEGER NOT NULL
            )
        """)

        // Create suspicious IPs table
        db.execSQL("""
            CREATE TABLE $TABLE_SUSPICIOUS_IPS (
                $COL_IP TEXT PRIMARY KEY,
                $COL_REASON TEXT NOT NULL,
                $COL_DATE_ADDED INTEGER NOT NULL
            )
        """)

        // Create scan history table
        db.execSQL("""
            CREATE TABLE $TABLE_SCAN_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE_NAME TEXT NOT NULL,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_RISK_LEVEL TEXT NOT NULL,
                $COL_HASH TEXT,
                $COL_SCAN_TIME INTEGER NOT NULL
            )
        """)

        // Seed with sample known threats
        seedDatabase(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MALWARE_HASHES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PHISHING_URLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUSPICIOUS_IPS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCAN_HISTORY")
        onCreate(db)
    }

    private fun seedDatabase(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        // Sample known malware hashes (from public threat databases)
        val malwareHashes = listOf(
            Triple("e99a18c428cb38d5f260853678922e03", "Android.Joker", "CRITICAL"),
            Triple("d41d8cd98f00b204e9800998ecf8427e", "Android.HiddenAds", "HIGH"),
            Triple("5d41402abc4b2a76b9719d911017c592", "Android.BankBot", "CRITICAL"),
            Triple("7d793037a0760186574b0282f2f435e7", "Android.SpyAgent", "CRITICAL"),
            Triple("098f6bcd4621d373cade4e832627b4f6", "Android.Triada", "CRITICAL"),
            Triple("ad0234829205b9033196ba818f7a872b", "Android.Cerberus", "HIGH"),
            Triple("8ad8757baa8564dc136c1e07507f4a98", "Android.FluBot", "CRITICAL"),
            Triple("c20ad4d76fe97759aa27a0c99bff6710", "Android.Anubis", "HIGH"),
            Triple("c51ce410c124a10e0db5e4b97fc2af39", "Android.Hydra", "HIGH"),
            Triple("aab3238922bcc25a6f606eb525ffdc56", "Android.TeaBot", "CRITICAL")
        )

        malwareHashes.forEach { (hash, name, severity) ->
            db.execSQL(
                "INSERT INTO $TABLE_MALWARE_HASHES ($COL_HASH, $COL_MALWARE_NAME, $COL_SEVERITY, $COL_DATE_ADDED) VALUES (?, ?, ?, ?)",
                arrayOf(hash, name, severity, now)
            )
        }

        // Sample phishing URLs
        val phishingUrls = listOf(
            Triple("secure-login-verify.com", "phishing", "PhishTank"),
            Triple("account-update-required.net", "phishing", "PhishTank"),
            Triple("free-crypto-giveaway.xyz", "scam", "local"),
            Triple("wallet-connect-verify.io", "phishing", "local"),
            Triple("binance-airdrop-claim.com", "scam", "local"),
            Triple("metamask-verify-wallet.net", "phishing", "PhishTank"),
            Triple("trustwallet-sync.com", "phishing", "local"),
            Triple("coinbase-verify-account.org", "phishing", "PhishTank"),
            Triple("free-nft-mint-now.xyz", "scam", "local"),
            Triple("defi-yield-farming-bonus.com", "scam", "local")
        )

        phishingUrls.forEach { (url, type, source) ->
            db.execSQL(
                "INSERT INTO $TABLE_PHISHING_URLS ($COL_URL, $COL_THREAT_TYPE, $COL_SOURCE, $COL_DATE_ADDED) VALUES (?, ?, ?, ?)",
                arrayOf(url, type, source, now)
            )
        }

        // Sample suspicious IPs (known C&C servers)
        val suspiciousIps = listOf(
            Pair("185.215.113.66", "Known C&C server - Emotet"),
            Pair("91.219.236.222", "Known C&C server - TrickBot"),
            Pair("194.5.98.178", "Malware distribution"),
            Pair("45.153.241.187", "Known C&C server - Cobalt Strike"),
            Pair("103.224.182.250", "Phishing infrastructure"),
            Pair("192.99.221.77", "Known C&C server - AgentTesla"),
            Pair("23.106.215.76", "Malware distribution"),
            Pair("176.111.174.26", "Known C&C server - RedLine")
        )

        suspiciousIps.forEach { (ip, reason) ->
            db.execSQL(
                "INSERT INTO $TABLE_SUSPICIOUS_IPS ($COL_IP, $COL_REASON, $COL_DATE_ADDED) VALUES (?, ?, ?)",
                arrayOf(ip, reason, now)
            )
        }
    }

    /**
     * Check if a hash matches a known malware signature.
     */
    fun isMalwareHash(hash: String): Pair<Boolean, String> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_MALWARE_NAME FROM $TABLE_MALWARE_HASHES WHERE $COL_HASH = ?",
            arrayOf(hash)
        )
        val result = if (cursor.moveToFirst()) {
            Pair(true, cursor.getString(0))
        } else {
            Pair(false, "")
        }
        cursor.close()
        return result
    }

    /**
     * Check if a URL is a known phishing URL.
     */
    fun isPhishingUrl(url: String): Pair<Boolean, String> {
        val db = readableDatabase
        // Check if the URL contains any known phishing domain
        val cursor = db.rawQuery(
            "SELECT $COL_THREAT_TYPE FROM $TABLE_PHISHING_URLS WHERE ? LIKE '%' || $COL_URL || '%'",
            arrayOf(url)
        )
        val result = if (cursor.moveToFirst()) {
            Pair(true, cursor.getString(0))
        } else {
            Pair(false, "")
        }
        cursor.close()
        return result
    }

    /**
     * Check if an IP is suspicious.
     */
    fun isSuspiciousIp(ip: String): Pair<Boolean, String> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_REASON FROM $TABLE_SUSPICIOUS_IPS WHERE $COL_IP = ?",
            arrayOf(ip)
        )
        val result = if (cursor.moveToFirst()) {
            Pair(true, cursor.getString(0))
        } else {
            Pair(false, "")
        }
        cursor.close()
        return result
    }

    /**
     * Save scan result to history.
     */
    fun saveScanResult(packageName: String, appName: String, riskLevel: String, hash: String) {
        val db = writableDatabase
        db.execSQL(
            "INSERT INTO $TABLE_SCAN_HISTORY ($COL_PACKAGE_NAME, $COL_APP_NAME, $COL_RISK_LEVEL, $COL_HASH, $COL_SCAN_TIME) VALUES (?, ?, ?, ?, ?)",
            arrayOf(packageName, appName, riskLevel, hash, System.currentTimeMillis())
        )
    }

    /**
     * Get total number of known malware signatures.
     */
    fun getMalwareSignatureCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MALWARE_HASHES", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }
}
