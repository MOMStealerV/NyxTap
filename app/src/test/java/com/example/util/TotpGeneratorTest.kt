package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {

    @Test
    fun testBase32DecodingAndSecretExtraction() {
        val rawSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val otpAuthUri = "otpauth://totp/Example:alice@google.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Example"
        
        val extracted1 = TotpGenerator.extractSecret(rawSecret)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", extracted1)

        val extracted2 = TotpGenerator.extractSecret(otpAuthUri)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", extracted2)

        val decoded = TotpGenerator.decodeBase32(extracted1)
        assertEquals("12345678901234567890", String(decoded))
    }

    @Test
    fun testParseAndValidateSecret_AllFormats() {
        val expected = "JBSWY3DPEHPK3PXP"

        // 1. Normal Base32
        val res1 = TotpGenerator.parseAndValidateSecret("JBSWY3DPEHPK3PXP")
        assertTrue(res1 is TotpParseResult.Success && res1.secret == expected)

        // 2. Lowercase
        val res2 = TotpGenerator.parseAndValidateSecret("jbswy3dpehpk3pxp")
        assertTrue(res2 is TotpParseResult.Success && res2.secret == expected)

        // 3. Base32 with spaces
        val res3 = TotpGenerator.parseAndValidateSecret("JBSW Y3DP EHPK 3PXP")
        assertTrue(res3 is TotpParseResult.Success && res3.secret == expected)

        // 4. Base32 with line breaks (\r, \n) and tabs
        val res4 = TotpGenerator.parseAndValidateSecret("JBSW\nY3DP\r\n\tEHPK 3PXP")
        assertTrue(res4 is TotpParseResult.Success && res4.secret == expected)

        // 5. Base32 with '=' padding
        val res5 = TotpGenerator.parseAndValidateSecret("JBSWY3DPEHPK3PXP====")
        assertTrue(res5 is TotpParseResult.Success && res5.secret == expected)

        // 6. Standard otpauth URI
        val res6 = TotpGenerator.parseAndValidateSecret(
            "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        )
        assertTrue(res6 is TotpParseResult.Success && res6.secret == expected)

        // 7. URL-encoded otpauth URI
        val res7 = TotpGenerator.parseAndValidateSecret(
            "otpauth://totp/Example?secret=jbsw%20y3dp%20ehpk%203pxp"
        )
        assertTrue(res7 is TotpParseResult.Success && res7.secret == expected)
    }

    @Test
    fun testParseAndValidateSecret_ErrorStates() {
        // Empty clipboard
        val empty1 = TotpGenerator.parseAndValidateSecret("")
        assertEquals(TotpParseResult.Failure("Clipboard is empty"), empty1)

        val empty2 = TotpGenerator.parseAndValidateSecret("   \n\t ")
        assertEquals(TotpParseResult.Failure("Clipboard is empty"), empty2)

        val empty3 = TotpGenerator.parseAndValidateSecret(null)
        assertEquals(TotpParseResult.Failure("Clipboard is empty"), empty3)

        // Email in clipboard
        val email1 = TotpGenerator.parseAndValidateSecret("user@example.com")
        assertEquals(TotpParseResult.Failure("Clipboard does not contain a TOTP secret"), email1)

        val email2 = TotpGenerator.parseAndValidateSecret("test.account@nowcare.us")
        assertEquals(TotpParseResult.Failure("Clipboard does not contain a TOTP secret"), email2)

        // 6-digit OTP in clipboard
        val otp1 = TotpGenerator.parseAndValidateSecret("583214")
        assertEquals(TotpParseResult.Failure("Clipboard contains an OTP, not a secret"), otp1)

        val otp2 = TotpGenerator.parseAndValidateSecret("123 456")
        assertEquals(TotpParseResult.Failure("Clipboard contains an OTP, not a secret"), otp2)

        val otp3 = TotpGenerator.parseAndValidateSecret("123-456")
        assertEquals(TotpParseResult.Failure("Clipboard contains an OTP, not a secret"), otp3)

        // Invalid secret
        val invalid1 = TotpGenerator.parseAndValidateSecret("Hello World 123 !@#$")
        assertEquals(TotpParseResult.Failure("Invalid TOTP secret"), invalid1)

        val invalid2 = TotpGenerator.parseAndValidateSecret("otpauth://totp/Example?issuer=NoSecret")
        assertEquals(TotpParseResult.Failure("Invalid TOTP secret"), invalid2)
    }

    @Test
    fun testRfc6238VectorGeneration() {
        // RFC 6238 / RFC 4226 test vector:
        // Secret = "12345678901234567890" in ASCII -> Base32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val secretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        val code1 = TotpGenerator.generateTotpForCounter(secretBase32, 1L, 6)
        assertEquals("287082", code1)

        val code2 = TotpGenerator.generateTotpForCounter(secretBase32, 2L, 6)
        assertEquals("359152", code2)

        val code3 = TotpGenerator.generateTotpForCounter(secretBase32, 3L, 6)
        assertEquals("969429", code3)
    }

    @Test
    fun testRemainingSecondsAndProgress() {
        val remaining = TotpGenerator.getRemainingSeconds(30)
        assertTrue(remaining in 1..30)

        val progress = TotpGenerator.getProgressFraction(30)
        assertTrue(progress in 0.0f..1.0f)
    }
}
