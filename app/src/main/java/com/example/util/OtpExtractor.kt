package com.example.util

import java.util.regex.Pattern

data class OtpCandidate(
    val code: String,
    val score: Int,
    val matchedKeyword: String,
    val source: String
)

object OtpExtractor {

    // Regex for HTML removal & entity decoding
    private val SCRIPT_STYLE_PATTERN = Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")
    private val URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE)

    // Context keyword definitions with assigned confidence weights
    private val KEYWORD_WEIGHTS = listOf(
        // High priority: Verification & Confirmation (+100)
        Pair(Pattern.compile("(?:verification\\s*code|confirmation\\s*code|código\\s*de\\s*verificación|code\\s*de\\s*vérification|bestätigungscode)", Pattern.CASE_INSENSITIVE), 100),
        // Security code (+90)
        Pair(Pattern.compile("(?:security\\s*code|código\\s*de\\s*seguridad|code\\s*de\\s*sécurité|sicherheitscode)", Pattern.CASE_INSENSITIVE), 90),
        // OTP & One-time code (+90)
        Pair(Pattern.compile("(?:one[-\\s]?time\\s*(?:password|passcode|code)|\\botp\\b|código\\s*único|code\\s*à\\s*usage\\s*unique)", Pattern.CASE_INSENSITIVE), 90),
        // Login & Auth code (+80)
        Pair(Pattern.compile("(?:login\\s*code|access\\s*code|authorization\\s*code|authentication\\s*code|signin\\s*code|sign[-\\s]?in\\s*code)", Pattern.CASE_INSENSITIVE), 80),
        // General code prompts (+60)
        Pair(Pattern.compile("(?:your\\s*code(?:\\s*is)?|code\\s*is|code\\s*[:=]|passcode\\s*[:=]?|pin\\s*[:=]?|pin\\s*code|enter\\s*(?:the\\s*)?(?:following\\s*)?code|use\\s*(?:the\\s*)?code)", Pattern.CASE_INSENSITIVE), 60)
    )

    // Direct glued patterns: e.g. "Confirmation code266433If...", "verification code: 123456", "Your code:266433Please"
    private val DIRECT_EXTRACTION_PATTERNS = listOf(
        // "Confirmation code266433If...", "Your code is 266433", "Your code is G-582914", "verification code123456"
        Pair(
            Pattern.compile(
                "(?:verification\\s*code|confirmation\\s*code|security\\s*code|one[-\\s]?time\\s*(?:code|password|passcode)|login\\s*code|auth(?:entication|orization)?\\s*code|sign[-\\s]?in\\s*code|your\\s*code\\s*(?:is)?|code\\s*is|código\\s*de\\s*(?:verificación|seguridad)|code\\s*de\\s*(?:vérification|sécurité)|bestätigungscode)\\s*[:=-]?\\s*(?:[A-Za-z]-)?([0-9]{4,8})(?![0-9])",
                Pattern.CASE_INSENSITIVE
            ),
            130
        ),
        // "Code: 123456" / "OTP: 123456" / "Code:266433Please" / "Passcode: 123456" / "PIN: 1234"
        Pair(
            Pattern.compile(
                "\\b(?:OTP|Code|PIN|Passcode)\\s*[:=]\\s*(?:[A-Za-z]-)?([0-9]{4,8})(?![0-9])",
                Pattern.CASE_INSENSITIVE
            ),
            120
        ),
        // "Enter 123456 to verify" / "Use code 123456 to confirm"
        Pair(
            Pattern.compile(
                "(?:use|enter|input)\\s*(?:the\\s*)?(?:code|pin|otp)?\\s*[:=-]?\\s*([0-9]{4,8})(?![0-9])\\s*(?:to\\s*(?:verify|confirm|log\\s*in|sign\\s*in|authenticate))",
                Pattern.CASE_INSENSITIVE
            ),
            110
        ),
        // Hyphenated codes like "123-456" with code context
        Pair(
            Pattern.compile(
                "(?:code|otp|verify|confirm|pin)[^\\n]{0,30}\\b([0-9]{3}-[0-9]{3})\\b",
                Pattern.CASE_INSENSITIVE
            ),
            100
        )
    )

    // General standalone/embedded digit candidate regex: 4 to 8 digits not flanked by other digits
    private val STANDALONE_DIGIT_PATTERN = Pattern.compile("(?<![0-9])([0-9]{4,8})(?![0-9])")

    /**
     * Extracts the most probable OTP from the email message.
     * Evaluates Plain Text Body -> Cleaned HTML Body -> Subject in priority order.
     */
    fun extractOtp(bodyText: String? = null, bodyHtml: String? = null, subject: String? = null): String? {
        val candidates = extractAllCandidates(bodyText, bodyHtml, subject)
        return candidates.maxByOrNull { it.score }?.code
    }

    /**
     * Returns all scored candidates for diagnostics and telemetry.
     */
    fun extractAllCandidates(bodyText: String? = null, bodyHtml: String? = null, subject: String? = null): List<OtpCandidate> {
        val candidates = mutableListOf<OtpCandidate>()

        // 1. Plain Text Body (Priority 1: Boost +100)
        if (!bodyText.isNullOrBlank()) {
            candidates.addAll(findCandidatesInText(bodyText, source = "bodyText", baseScoreBoost = 100))
        }

        // 2. Cleaned HTML Body (Priority 2: Boost +30)
        if (!bodyHtml.isNullOrBlank()) {
            val cleanHtmlText = cleanHtml(bodyHtml)
            if (cleanHtmlText.isNotBlank()) {
                candidates.addAll(findCandidatesInText(cleanHtmlText, source = "bodyHtml", baseScoreBoost = 30))
            }
        }

        // 3. Subject (Priority 3: Boost +10)
        if (!subject.isNullOrBlank()) {
            candidates.addAll(findCandidatesInText(subject, source = "subject", baseScoreBoost = 10))
        }

        // Deduplicate and filter out false positives
        return candidates
            .filter { isValidOtp(it.code) }
            .groupBy { it.code }
            .map { (code, candidateList) ->
                val highestScore = candidateList.maxOf { it.score }
                val bestCandidate = candidateList.first { it.score == highestScore }
                bestCandidate.copy(code = code)
            }
            .sortedByDescending { it.score }
    }

    private fun findCandidatesInText(text: String, source: String, baseScoreBoost: Int): List<OtpCandidate> {
        val results = mutableListOf<OtpCandidate>()

        // Sanitize: strip URLs to prevent extracting tracking numbers / IDs from links
        val textWithoutUrls = URL_PATTERN.matcher(text).replaceAll(" ")

        // Check Direct Extraction Patterns first
        for ((pattern, baseScore) in DIRECT_EXTRACTION_PATTERNS) {
            val matcher = pattern.matcher(textWithoutUrls)
            while (matcher.find()) {
                val rawCode = matcher.group(1)?.replace("-", "")?.trim()
                if (!rawCode.isNullOrBlank() && isValidOtp(rawCode)) {
                    results.add(
                        OtpCandidate(
                            code = rawCode,
                            score = baseScore + baseScoreBoost,
                            matchedKeyword = pattern.pattern().take(30),
                            source = source
                        )
                    )
                }
            }
        }

        // Scan keyword proximities for standalone digits
        val digitMatcher = STANDALONE_DIGIT_PATTERN.matcher(textWithoutUrls)
        while (digitMatcher.find()) {
            val code = digitMatcher.group(1)?.trim() ?: continue
            if (!isValidOtp(code)) continue

            val startPos = digitMatcher.start()
            val endPos = digitMatcher.end()

            // Context window: 100 characters before and 50 characters after
            val windowStart = (startPos - 100).coerceAtLeast(0)
            val windowEnd = (endPos + 50).coerceAtMost(textWithoutUrls.length)
            val contextWindow = textWithoutUrls.substring(windowStart, windowEnd)

            var keywordScore = 0
            var matchedKeyword = "standalone_digit"

            for ((keywordPattern, weight) in KEYWORD_WEIGHTS) {
                if (keywordPattern.matcher(contextWindow).find()) {
                    if (weight > keywordScore) {
                        keywordScore = weight
                        matchedKeyword = keywordPattern.pattern().take(30)
                    }
                }
            }

            // Standalone score baseline: 30 + keywordScore + baseScoreBoost
            val finalScore = baseScoreBoost + (if (keywordScore > 0) keywordScore else 30)
            results.add(
                OtpCandidate(
                    code = code,
                    score = finalScore,
                    matchedKeyword = matchedKeyword,
                    source = source
                )
            )
        }

        return results
    }

    /**
     * Filters out non-OTP noise:
     * - Repetitive digits (000000, 111111)
     * - Year numbers (1900..2099)
     * - Codes less than 4 or greater than 8 chars
     * - Strictly numeric digits only
     */
    fun isValidOtp(code: String): Boolean {
        val clean = code.replace("-", "").trim()
        if (clean.length < 4 || clean.length > 8) return false
        if (clean.all { it == clean[0] }) return false // all identical digits like 000000
        if (clean.all { it.isDigit() }) {
            if (clean.length == 4) {
                val num = clean.toIntOrNull()
                if (num != null && num in 1900..2099) return false // likely a year
            }
            return true
        }
        return false
    }

    /**
     * Cleans HTML markup and decodes common HTML entities to pure plain text.
     */
    fun cleanHtml(html: String): String {
        // 1. Remove <script> and <style> blocks
        var cleaned = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll(" ")
        // 2. Remove all HTML tags
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ")
        // 3. Replace common HTML entities
        cleaned = cleaned
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
        // 4. Collapse whitespace
        return cleaned.replace("\\s+".toRegex(), " ").trim()
    }
}
