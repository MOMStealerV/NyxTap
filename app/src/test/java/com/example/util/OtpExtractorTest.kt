package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun testNormalVerificationCode() {
        val subject = "Account Verification"
        val body = "Your verification code is 266433. Please enter it on the screen."
        val otp = OtpExtractor.extractOtp(bodyText = body, subject = subject)
        assertEquals("266433", otp)
    }

    @Test
    fun testGluedTextConfirmationCode() {
        val subject = "Security notification"
        val body = "Confirmation code266433If you did not request this, ignore."
        val otp = OtpExtractor.extractOtp(bodyText = body, subject = subject)
        assertEquals("266433", otp)
    }

    @Test
    fun testGluedTextWithNoSeparator() {
        val subject = "Your Access Request"
        val body = "Your code:266433Please continue with your sign in."
        val otp = OtpExtractor.extractOtp(bodyText = body, subject = subject)
        assertEquals("266433", otp)
    }

    @Test
    fun testMultipleNumbersContextScoring() {
        val subject = "Order Confirmation #928371"
        val body = "Order ID 928371 placed on 2026-08-24. Session ID 109284. Your verification code is 518293 to confirm delivery."
        val otp = OtpExtractor.extractOtp(bodyText = body, subject = subject)
        assertEquals("518293", otp)
    }

    @Test
    fun testPlainTextDominatesHtmlFalsePositives() {
        val subject = "Sign-in code"
        val plainText = "Your security code is 382914"
        val htmlBody = """
            <html>
            <head><style>.code { color: red; }</style></head>
            <body>
              <table id="918273" width="600">
                <tr><td>Tracking ID 654321</td></tr>
                <tr><td>Order reference 827162</td></tr>
              </table>
            </body>
            </html>
        """.trimIndent()

        val otp = OtpExtractor.extractOtp(bodyText = plainText, bodyHtml = htmlBody, subject = subject)
        assertEquals("382914", otp)
    }

    @Test
    fun testHtmlFallbackWhenPlainTextEmpty() {
        val subject = "Your one-time login"
        val htmlBody = "<div>Your one-time passcode is <strong>718293</strong>.</div>"
        val otp = OtpExtractor.extractOtp(bodyText = null, bodyHtml = htmlBody, subject = subject)
        assertEquals("718293", otp)
    }

    @Test
    fun testHyphenatedAndPrefixedCodes() {
        val body1 = "Your code is G-582914 for Google Verification."
        assertEquals("582914", OtpExtractor.extractOtp(bodyText = body1))

        val body2 = "Your authentication code is 123-456."
        assertEquals("123456", OtpExtractor.extractOtp(bodyText = body2))
    }

    @Test
    fun testMultiLanguagePrompts() {
        val spanish = "Su código de verificación es 629104 para continuar."
        assertEquals("629104", OtpExtractor.extractOtp(bodyText = spanish))

        val french = "Votre code de sécurité est 849201."
        assertEquals("849201", OtpExtractor.extractOtp(bodyText = french))

        val german = "Ihr Bestätigungscode lautet 391820."
        assertEquals("391820", OtpExtractor.extractOtp(bodyText = german))
    }

    @Test
    fun testStrictNumericValidation() {
        // Rejects glued letters like "266433If"
        assertFalse("266433If must be rejected", OtpExtractor.isValidOtp("266433If"))
        assertFalse("Code with letters must be rejected", OtpExtractor.isValidOtp("ABCDEF"))
        assertFalse("Repetitive digits must be rejected", OtpExtractor.isValidOtp("000000"))
        assertFalse("Repetitive digits must be rejected", OtpExtractor.isValidOtp("111111"))
        assertFalse("Year 2026 must be rejected", OtpExtractor.isValidOtp("2026"))
        assertFalse("Year 1999 must be rejected", OtpExtractor.isValidOtp("1999"))
        assertFalse("Too short must be rejected", OtpExtractor.isValidOtp("123"))
        assertFalse("Too long must be rejected", OtpExtractor.isValidOtp("123456789"))

        assertTrue("6-digit numeric must be valid", OtpExtractor.isValidOtp("266433"))
        assertTrue("4-digit non-year must be valid", OtpExtractor.isValidOtp("8491"))
        assertTrue("8-digit numeric must be valid", OtpExtractor.isValidOtp("58291047"))
    }

    @Test
    fun testNoOtpInEmailReturnsNull() {
        val subject = "Newsletter August 2026"
        val body = "Hello, check out our new products for August 2026. No codes in this email."
        val otp = OtpExtractor.extractOtp(bodyText = body, subject = subject)
        assertNull(otp)
    }
}

