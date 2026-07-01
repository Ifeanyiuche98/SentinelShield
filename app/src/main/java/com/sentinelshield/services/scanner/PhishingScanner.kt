package com.sentinelshield.services.scanner

import android.content.Context
import com.sentinelshield.data.database.ThreatDatabase
import com.sentinelshield.data.models.PhishingResult
import java.net.URL

/**
 * Scans URLs against a local database of known phishing domains.
 * Provides real-time URL safety checks.
 */
class PhishingScanner(private val context: Context) {

    private val threatDb = ThreatDatabase(context)

    // Additional suspicious patterns to check
    private val suspiciousPatterns = listOf(
        "login.*verify",
        "account.*update",
        "secure.*confirm",
        "wallet.*connect",
        "free.*crypto",
        "airdrop.*claim",
        "verify.*account",
        "sync.*wallet",
        "unlock.*reward",
        "claim.*token"
    )

    // Suspicious TLDs commonly used in phishing
    private val suspiciousTlds = setOf(
        ".xyz", ".top", ".club", ".work", ".click",
        ".link", ".info", ".online", ".site", ".icu"
    )

    /**
     * Check if a URL is potentially a phishing attempt.
     */
    fun checkUrl(url: String): PhishingResult {
        val normalizedUrl = normalizeUrl(url)

        // 1. Check against local database
        val (isKnownPhishing, threatType) = threatDb.isPhishingUrl(normalizedUrl)
        if (isKnownPhishing) {
            return PhishingResult(
                url = url,
                isPhishing = true,
                threatType = threatType,
                source = "Local threat database"
            )
        }

        // 2. Check against suspicious patterns
        val patternMatch = checkSuspiciousPatterns(normalizedUrl)
        if (patternMatch != null) {
            return PhishingResult(
                url = url,
                isPhishing = true,
                threatType = "suspicious_pattern",
                source = "Pattern analysis: $patternMatch"
            )
        }

        // 3. Check for suspicious TLD + keyword combination
        if (hasSuspiciousTldAndKeyword(normalizedUrl)) {
            return PhishingResult(
                url = url,
                isPhishing = true,
                threatType = "suspicious_domain",
                source = "Suspicious TLD with sensitive keywords"
            )
        }

        // 4. Check for homograph attacks (lookalike characters)
        if (hasHomographIndicators(normalizedUrl)) {
            return PhishingResult(
                url = url,
                isPhishing = true,
                threatType = "homograph_attack",
                source = "Possible homograph/lookalike domain"
            )
        }

        return PhishingResult(
            url = url,
            isPhishing = false
        )
    }

    /**
     * Batch check multiple URLs.
     */
    fun checkUrls(urls: List<String>): List<PhishingResult> {
        return urls.map { checkUrl(it) }
    }

    /**
     * Normalize URL for comparison.
     */
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    /**
     * Check URL against suspicious regex patterns.
     */
    private fun checkSuspiciousPatterns(url: String): String? {
        suspiciousPatterns.forEach { pattern ->
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                return pattern
            }
        }
        return null
    }

    /**
     * Check if URL has a suspicious TLD combined with sensitive keywords.
     */
    private fun hasSuspiciousTldAndKeyword(url: String): Boolean {
        val hasSuspiciousTld = suspiciousTlds.any { url.contains(it) }
        if (!hasSuspiciousTld) return false

        val sensitiveKeywords = listOf(
            "bank", "paypal", "apple", "google", "microsoft",
            "amazon", "netflix", "crypto", "bitcoin", "ethereum",
            "metamask", "trustwallet", "binance", "coinbase"
        )

        return sensitiveKeywords.any { url.contains(it, ignoreCase = true) }
    }

    /**
     * Check for potential homograph attacks using lookalike characters.
     */
    private fun hasHomographIndicators(url: String): Boolean {
        // Check for mixed scripts or unusual characters
        val hasNonAscii = url.any { it.code > 127 }
        if (hasNonAscii) return true

        // Check for common substitutions
        val substitutions = mapOf(
            "0" to "o", "1" to "l", "rn" to "m",
            "vv" to "w", "cl" to "d"
        )

        val knownBrands = listOf("google", "apple", "amazon", "paypal", "microsoft")
        knownBrands.forEach { brand ->
            substitutions.forEach { (fake, real) ->
                val fakeVersion = brand.replace(real, fake)
                if (url.contains(fakeVersion) && !url.contains(brand)) {
                    return true
                }
            }
        }

        return false
    }
}
