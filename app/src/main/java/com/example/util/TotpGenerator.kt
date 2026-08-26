package com.example.util

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

sealed class TotpParseResult {
    data class Success(val secret: String) : TotpParseResult()
    data class Failure(val errorMessage: String) : TotpParseResult()
}

object TotpGenerator {

    private const val DEFAULT_TIME_STEP_SECONDS = 30
    private const val DEFAULT_DIGITS = 6
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Parses and validates a raw input string (from clipboard or manual input).
     * Normalizes spaces, hyphens, line breaks, lowercase, padding '=' and extracts from otpauth:// URIs.
     * Accurately distinguishes error states: Empty, Email, 6-digit OTP, or Invalid Base32 Secret.
     */
    fun parseAndValidateSecret(input: String?): TotpParseResult {
        if (input == null || input.trim().isEmpty()) {
            return TotpParseResult.Failure("Clipboard is empty")
        }

        val trimmed = input.trim()

        // Check if clipboard contains an email address (not an otpauth URI)
        if (!trimmed.startsWith("otpauth://", ignoreCase = true) &&
            trimmed.contains("@") && trimmed.contains(".") && !trimmed.contains("?")
        ) {
            return TotpParseResult.Failure("Clipboard does not contain a TOTP secret")
        }

        // Check if clipboard contains a 6-digit (or 6-8 digit) OTP code instead of a secret key
        val cleanDigits = trimmed.replace(" ", "").replace("-", "")
        if (cleanDigits.length in 6..8 && cleanDigits.all { it.isDigit() }) {
            return TotpParseResult.Failure("Clipboard contains an OTP, not a secret")
        }

        // Handle otpauth:// URI
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val secretRegex = Regex("[?&]secret=([^&]+)", RegexOption.IGNORE_CASE)
            val match = secretRegex.find(trimmed) ?: return TotpParseResult.Failure("Invalid TOTP secret")
            val rawSecretParam = match.groupValues[1]
            val urlDecoded = try {
                URLDecoder.decode(rawSecretParam, "UTF-8")
            } catch (e: Exception) {
                rawSecretParam
            }
            val normalized = normalizeSecretString(urlDecoded)
            if (normalized.isEmpty() || normalized.length < 4 || !isValidBase32(normalized)) {
                return TotpParseResult.Failure("Invalid TOTP secret")
            }
            return try {
                decodeBase32(normalized)
                TotpParseResult.Success(normalized)
            } catch (e: Exception) {
                TotpParseResult.Failure("Invalid TOTP secret")
            }
        }

        // Handle plain Base32 string
        val normalized = normalizeSecretString(trimmed)
        if (normalized.isEmpty() || normalized.length < 4 || !isValidBase32(normalized)) {
            return TotpParseResult.Failure("Invalid TOTP secret")
        }

        return try {
            decodeBase32(normalized)
            TotpParseResult.Success(normalized)
        } catch (e: Exception) {
            TotpParseResult.Failure("Invalid TOTP secret")
        }
    }

    /**
     * Removes whitespace, tabs, newlines, carriage returns, hyphens, and padding '='.
     * Converts to uppercase ASCII.
     */
    fun normalizeSecretString(raw: String): String {
        return raw
            .replace(" ", "")
            .replace("\t", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace("-", "")
            .replace("=", "")
            .uppercase(Locale.US)
    }

    /**
     * Checks if all characters belong to the standard Base32 alphabet (A-Z, 2-7).
     */
    fun isValidBase32(secret: String): Boolean {
        if (secret.isEmpty()) return false
        for (c in secret) {
            if (c !in BASE32_ALPHABET) return false
        }
        return true
    }

    /**
     * Decodes Base32 string into raw byte array.
     * Handles spaces, hyphens, lowercase characters, and ignores standard padding '='.
     */
    fun decodeBase32(encoded: String): ByteArray {
        val clean = normalizeSecretString(encoded)

        if (clean.isEmpty()) {
            throw IllegalArgumentException("Empty Base32 secret")
        }

        var buffer = 0
        var bitsLeft = 0
        val result = mutableListOf<Byte>()

        for (c in clean) {
            val valIndex = BASE32_ALPHABET.indexOf(c)
            if (valIndex < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return result.toByteArray()
    }

    /**
     * Parses secret from plain Base32 string or standard otpauth:// URL.
     */
    fun extractSecret(input: String): String {
        return when (val parseResult = parseAndValidateSecret(input)) {
            is TotpParseResult.Success -> parseResult.secret
            is TotpParseResult.Failure -> normalizeSecretString(input)
        }
    }

    /**
     * Generates a TOTP code for a given timestamp and secret.
     * Defaults to 30-second time step and 6 digits.
     */
    fun generateTotp(
        secretBase32: String,
        timestampMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = "HmacSHA1"
    ): String {
        val cleanSecret = extractSecret(secretBase32)
        val keyBytes = decodeBase32(cleanSecret)
        val timeStep = (timestampMillis / 1000L) / timeStepSeconds
        return generateHotp(keyBytes, timeStep, digits, algorithm)
    }

    /**
     * Generates HOTP code directly from a Base32 secret and counter step.
     */
    fun generateTotpForCounter(
        secretBase32: String,
        counter: Long,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = "HmacSHA1"
    ): String {
        val cleanSecret = extractSecret(secretBase32)
        val keyBytes = decodeBase32(cleanSecret)
        return generateHotp(keyBytes, counter, digits, algorithm)
    }

    /**
     * Generates HOTP code according to RFC 4226.
     */
    fun generateHotp(
        key: ByteArray,
        counter: Long,
        digits: Int = DEFAULT_DIGITS,
        algorithm: String = "HmacSHA1"
    ): String {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = try {
            Mac.getInstance(algorithm)
        } catch (e: NoSuchAlgorithmException) {
            Mac.getInstance("HmacSHA1")
        }

        val signKey = SecretKeySpec(key, algorithm)
        try {
            mac.init(signKey)
        } catch (e: InvalidKeyException) {
            throw IllegalArgumentException("Invalid secret key for HMAC", e)
        }

        val hash = mac.doFinal(data)

        // Dynamic truncation as per RFC 4226
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Calculates remaining seconds in the current TOTP period.
     */
    fun getRemainingSeconds(
        timestampMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS
    ): Int {
        val currentSeconds = (timestampMillis / 1000L)
        val elapsed = (currentSeconds % timeStepSeconds).toInt()
        val remaining = timeStepSeconds - elapsed
        return if (remaining == 0) timeStepSeconds else remaining
    }

    /**
     * Calculates progress fraction (1.0f at start of period down to 0.0f at expiration).
     */
    fun getProgressFraction(
        timestampMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Int = DEFAULT_TIME_STEP_SECONDS
    ): Float {
        val remaining = getRemainingSeconds(timestampMillis, timeStepSeconds)
        return remaining.toFloat() / timeStepSeconds.toFloat()
    }
}
