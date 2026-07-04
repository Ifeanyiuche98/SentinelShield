package com.sentinelshield.services.scanner

import android.content.Context
import android.util.Log
import com.sentinelshield.data.database.ThreatDatabase
import java.net.URI
import java.security.MessageDigest

/**
 * PhishingGuard - Enhanced real-time phishing and malicious URL protection.
 * 
 * Features:
 * - Local database lookup (fast, offline-capable)
 * - Heuristic analysis (homograph attacks, typosquatting)
 * - Domain reputation scoring
 * - URL pattern matching for known attack vectors
 * - Real-time link interception
 */
class PhishingGuard(private val context: Context) {

    companion object {
        private const val TAG = "PhishingGuard"

        // Known legitimate domains for typosquatting detection
        private val POPULAR_DOMAINS = listOf(
            "google.com", "facebook.com", "amazon.com", "apple.com",
            "microsoft.com", "paypal.com", "netflix.com", "instagram.com",
            "twitter.com", "linkedin.com", "whatsapp.com", "telegram.org",
            "binance.com", "coinbase.com", "blockchain.com", "trustwallet.com",
            "metamask.io", "opensea.io", "uniswap.org", "pancakeswap.finance",
            "gmail.com", "yahoo.com", "outlook.com", "banking.com"
        )

        // Suspicious TLDs commonly used in phishing
        private val SUSPICIOUS_TLDS = listOf(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".xyz", ".top", ".work",
            ".click", ".link", ".info", ".buzz", ".monster", ".rest"
        )

        // Homograph characters (Cyrillic/Greek lookalikes)
        private val HOMOGRAPH_MAP = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p',
            'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i',
            'ј' to 'j', 'ɡ' to 'g', 'ν' to 'v', 'ω' to 'w'
        )

        // Suspicious URL patterns
        private val SUSPICIOUS_PATTERNS = listOf(
            Regex("(?i)login.*verify"),
            Regex("(?i)account.*suspend"),
            Regex("(?i)security.*alert"),
            Regex("(?i)confirm.*identity"),
            Regex("(?i)update.*payment"),
            Regex("(?i)wallet.*connect"),
            Regex("(?i)claim.*reward"),
            Regex("(?i)airdrop.*free"),
            Regex("(?i)seed.*phrase"),
            Regex("(?i)private.*key"),
            Regex("(?i)verify.*wallet"),
            Regex("(?i)unlock.*account")
        )
    }

    data class UrlAnalysis(
        val url: String,
        val isSafe: Boolean,
        val riskScore: Int, // 0-100
        val threats: List<ThreatDetail>,
        val domain: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ThreatDetail(
        val type: ThreatType,
        val description: String,
        val confidence: Float // 0.0 to 1.0
    )

    enum class ThreatType {
        KNOWN_PHISHING,
        HOMOGRAPH_ATTACK,
        TYPOSQUATTING,
        SUSPICIOUS_TLD,
        SUSPICIOUS_PATTERN,
        EXCESSIVE_SUBDOMAINS,
        IP_BASED_URL,
        SHORTENED_URL,
        DATA_URI,
        NEWLY_REGISTERED
    }

    private val threatDatabase = ThreatDatabase(context)

    /**
     * Perform comprehensive URL analysis
     */
    fun analyzeUrl(urlString: String): UrlAnalysis {
        val threats = mutableListOf<ThreatDetail>()
        var riskScore = 0

        val cleanUrl = urlString.trim().lowercase()
        val domain = extractDomain(cleanUrl)

        // 1. Check against known phishing database
        if (threatDatabase.isKnownPhishingUrl(cleanUrl) || threatDatabase.isKnownPhishingUrl(domain)) {
            threats.add(ThreatDetail(
                ThreatType.KNOWN_PHISHING,
                "URL found in known phishing database",
                1.0f
            ))
            riskScore += 90
        }

        // 2. Check for homograph attacks
        val homographResult = detectHomograph(domain)
        if (homographResult != null) {
            threats.add(ThreatDetail(
                ThreatType.HOMOGRAPH_ATTACK,
                "Domain uses lookalike characters to impersonate '$homographResult'",
                0.95f
            ))
            riskScore += 80
        }

        // 3. Check for typosquatting
        val typosquatTarget = detectTyposquatting(domain)
        if (typosquatTarget != null) {
            threats.add(ThreatDetail(
                ThreatType.TYPOSQUATTING,
                "Domain is suspiciously similar to '$typosquatTarget'",
                0.85f
            ))
            riskScore += 60
        }

        // 4. Check for suspicious TLD
        if (SUSPICIOUS_TLDS.any { domain.endsWith(it) }) {
            threats.add(ThreatDetail(
                ThreatType.SUSPICIOUS_TLD,
                "Domain uses a TLD commonly associated with phishing",
                0.5f
            ))
            riskScore += 20
        }

        // 5. Check for suspicious URL patterns
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (pattern.containsMatchIn(cleanUrl)) {
                threats.add(ThreatDetail(
                    ThreatType.SUSPICIOUS_PATTERN,
                    "URL contains suspicious pattern: ${pattern.pattern}",
                    0.7f
                ))
                riskScore += 30
                break // Only count once
            }
        }

        // 6. Check for excessive subdomains (common in phishing)
        val subdomainCount = domain.count { it == '.' }
        if (subdomainCount >= 3) {
            threats.add(ThreatDetail(
                ThreatType.EXCESSIVE_SUBDOMAINS,
                "URL has $subdomainCount subdomain levels (unusual)",
                0.6f
            ))
            riskScore += 25
        }

        // 7. Check for IP-based URLs
        if (domain.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
            threats.add(ThreatDetail(
                ThreatType.IP_BASED_URL,
                "URL uses IP address instead of domain name",
                0.7f
            ))
            riskScore += 40
        }

        // 8. Check for URL shorteners (potential redirect to malicious site)
        val shorteners = listOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly")
        if (shorteners.any { domain.contains(it) }) {
            threats.add(ThreatDetail(
                ThreatType.SHORTENED_URL,
                "Shortened URL - destination cannot be verified",
                0.4f
            ))
            riskScore += 15
        }

        // 9. Check for data: URIs (can execute JavaScript)
        if (cleanUrl.startsWith("data:")) {
            threats.add(ThreatDetail(
                ThreatType.DATA_URI,
                "Data URI detected - may contain executable content",
                0.8f
            ))
            riskScore += 70
        }

        riskScore = riskScore.coerceIn(0, 100)
        val isSafe = riskScore < 40

        return UrlAnalysis(
            url = urlString,
            isSafe = isSafe,
            riskScore = riskScore,
            threats = threats,
            domain = domain
        )
    }

    /**
     * Quick check if URL is safe (for real-time interception)
     */
    fun isUrlSafe(url: String): Boolean {
        val analysis = analyzeUrl(url)
        return analysis.isSafe
    }

    /**
     * Detect homograph attacks using lookalike character detection
     */
    private fun detectHomograph(domain: String): String? {
        // Check if domain contains non-ASCII characters that look like ASCII
        var normalized = domain
        var hasHomograph = false

        for ((lookalike, real) in HOMOGRAPH_MAP) {
            if (domain.contains(lookalike)) {
                normalized = normalized.replace(lookalike, real)
                hasHomograph = true
            }
        }

        if (hasHomograph) {
            // Check if normalized version matches a popular domain
            for (popular in POPULAR_DOMAINS) {
                if (normalized.contains(popular.substringBefore("."))) {
                    return popular
                }
            }
        }

        return null
    }

    /**
     * Detect typosquatting using Levenshtein distance
     */
    private fun detectTyposquatting(domain: String): String? {
        val domainBase = domain.substringBefore(".").substringAfterLast(".")

        for (popular in POPULAR_DOMAINS) {
            val popularBase = popular.substringBefore(".")
            val distance = levenshteinDistance(domainBase, popularBase)

            // If edit distance is 1-2, it's likely typosquatting
            if (distance in 1..2 && domainBase != popularBase) {
                return popular
            }
        }

        return null
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: url.removePrefix("http://").removePrefix("https://").split("/")[0]
        } catch (e: Exception) {
            url.removePrefix("http://").removePrefix("https://").split("/")[0]
        }
    }
}
