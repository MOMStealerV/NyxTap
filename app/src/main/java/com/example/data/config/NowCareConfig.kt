package com.example.data.config

/**
 * Authoritative configuration for NowCare AHEM Mail Server.
 * Strictly restricts disposable mailbox generation to nowcare.us domain.
 */
object NowCareConfig {
    const val BASE_URL: String = "https://nowcare.us"
    const val API_BASE_URL: String = "https://nowcare.us/api/"
    const val DEFAULT_DOMAIN: String = "nowcare.us"
    val ALLOWED_DOMAINS: List<String> = listOf("nowcare.us")

    /**
     * Verifies if a domain is allowed.
     */
    fun isDomainAllowed(domain: String): Boolean {
        val cleanDomain = domain.trim().lowercase()
        return cleanDomain == DEFAULT_DOMAIN || ALLOWED_DOMAINS.contains(cleanDomain)
    }

    /**
     * Validates and returns the authoritative domain.
     */
    fun sanitizeDomain(domain: String?): String {
        if (domain.isNullOrBlank()) return DEFAULT_DOMAIN
        val clean = domain.trim().lowercase()
        return if (isDomainAllowed(clean)) clean else DEFAULT_DOMAIN
    }
}
