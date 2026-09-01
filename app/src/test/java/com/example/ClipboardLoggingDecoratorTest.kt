package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.repository.TwoFaRepository
import com.example.util.AppClipboardManager
import com.example.util.AppLifecycleTracker
import com.example.util.ClipboardAuditLog
import com.example.util.ClipboardLifecycleState
import com.example.util.ClipboardManagerClient
import com.example.util.ClipboardOperation
import com.example.util.LoggingClipboardDecorator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClipboardLoggingDecoratorTest {

    private lateinit var context: Context
    private lateinit var appClipboardManager: AppClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appClipboardManager = AppClipboardManager(context, AppLifecycleTracker)
        appClipboardManager.clearAuditLogs()
    }

    // =========================================================================
    // TEST 1 — Copy Operations Logging & Sanitization
    // =========================================================================
    @Test
    fun test1_CopyEmailAndPlainTextAuditLogging() {
        val testEmail = "developer@nowcare.us"
        val success = appClipboardManager.copyEmail(testEmail, callerContext = "MailScreen")
        assertTrue(success)

        val logs = appClipboardManager.getAuditLogs()
        assertEquals(1, logs.size)

        val emailLog = logs.first()
        assertEquals(ClipboardOperation.COPY_EMAIL, emailLog.operation)
        assertEquals("MailScreen", emailLog.callerContext)
        assertTrue(emailLog.success)
        assertFalse(emailLog.isSensitiveFlagApplied)
        assertTrue(emailLog.sanitizedSummary.contains("de***@nowcare.us"))

        // Copy plain text
        appClipboardManager.copyPlainText("TestLabel", "Arthur Pendelton", callerContext = "NameRepository")
        val updatedLogs = appClipboardManager.getAuditLogs()
        assertEquals(2, updatedLogs.size)
        val plainTextLog = updatedLogs.last()
        assertEquals(ClipboardOperation.COPY_PLAIN_TEXT, plainTextLog.operation)
        assertEquals("NameRepository", plainTextLog.callerContext)
        assertTrue(plainTextLog.sanitizedSummary.contains("TestLabel"))
    }

    // =========================================================================
    // TEST 2 — Sensitive Secret Copy (OTP and TOTP) & Flag Marking
    // =========================================================================
    @Test
    fun test2_SensitiveSecretCopyOtpAndTotp() {
        val otpCode = "584920"
        appClipboardManager.copyOtp(otpCode, callerContext = "MailRepository")

        val totpCode = "192837"
        appClipboardManager.copyTotp(totpCode, callerContext = "TwoFaRepository")

        val logs = appClipboardManager.getAuditLogs()
        assertEquals(2, logs.size)

        val otpLog = logs[0]
        assertEquals(ClipboardOperation.COPY_SECRET_OTP, otpLog.operation)
        assertEquals("MailRepository", otpLog.callerContext)
        assertTrue(otpLog.isSensitiveFlagApplied)
        assertTrue(otpLog.sanitizedSummary.contains("len=6"))

        val totpLog = logs[1]
        assertEquals(ClipboardOperation.COPY_SECRET_TOTP, totpLog.operation)
        assertEquals("TwoFaRepository", totpLog.callerContext)
        assertTrue(totpLog.isSensitiveFlagApplied)
        assertTrue(totpLog.sanitizedSummary.contains("len=6"))
    }

    // =========================================================================
    // TEST 3 — Secret Read Logging & Redaction (Base32 & otpauth://)
    // =========================================================================
    @Test
    fun test3_SecretReadLoggingAndRedaction() {
        val secretKey = "JBSWY3DPEHPK3PXP"
        appClipboardManager.copyPlainText("2FA Secret", secretKey, callerContext = "ExternalApp")

        val readSecret = appClipboardManager.readTotpSecret(callerContext = "TwoFaScreen")
        assertEquals(secretKey, readSecret)

        val logs = appClipboardManager.getAuditLogs()
        val readLog = logs.last()

        assertEquals(ClipboardOperation.READ_SECRET_TOTP, readLog.operation)
        assertEquals("TwoFaScreen", readLog.callerContext)
        assertTrue(readLog.success)
        assertTrue("Summary must note Base32 without leaking secret", readLog.sanitizedSummary.contains("Base32"))
        assertFalse("Log must not leak plain text secret key", readLog.sanitizedSummary.contains(secretKey))
    }

    // =========================================================================
    // TEST 4 — otpauth URI Sanitization in Read Audit Logs
    // =========================================================================
    @Test
    fun test4_OtpauthUriSanitization() {
        val otpauthUri = "otpauth://totp/Acme:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Acme"
        appClipboardManager.copyPlainText("RawUri", otpauthUri, callerContext = "ExternalScanner")

        val readText = appClipboardManager.readPrimaryClipText(callerContext = "FloatingOverlay")
        assertEquals(otpauthUri, readText)

        val logs = appClipboardManager.getAuditLogs()
        val readLog = logs.last()

        assertEquals(ClipboardOperation.READ_PRIMARY_CLIP, readLog.operation)
        assertEquals("FloatingOverlay", readLog.callerContext)
        assertTrue(readLog.sanitizedSummary.contains("otpauth://TOTP"))
        assertFalse("Log must not contain secret param value", readLog.sanitizedSummary.contains("JBSWY3DPEHPK3PXP"))
    }

    // =========================================================================
    // TEST 5 — Mock Delegate for Permission & Lifecycle Consistency Verification
    // =========================================================================
    @Test
    fun test5_PermissionAndLifecycleStateDecoratorVerification() {
        // Create mock delegate to test simulated environment conditions
        val mockDelegate = object : ClipboardManagerClient {
            var storedText: String? = null
            override fun copyEmail(email: String, callerContext: String): Boolean { storedText = email; return true }
            override fun copyOtp(otp: String, callerContext: String): Boolean { storedText = otp; return true }
            override fun copyTotp(code: String, callerContext: String): Boolean { storedText = code; return true }
            override fun copyPlainText(label: String, text: String, callerContext: String): Boolean { storedText = text; return true }
            override fun readTotpSecret(callerContext: String): String? = if (storedText == "JBSWY3DPEHPK3PXP") storedText else null
            override fun readPrimaryClipText(callerContext: String): String? = storedText
            override fun hasPrimaryClip(): Boolean = storedText != null
            override fun getAuditLogs(): List<ClipboardAuditLog> = emptyList()
            override fun clearAuditLogs() {}
        }

        val decorator = LoggingClipboardDecorator(
            delegate = mockDelegate,
            context = context,
            lifecycleTracker = AppLifecycleTracker
        )

        // Overlay is marked active
        AppLifecycleTracker.setOverlayServiceRunning(true)
        assertEquals(ClipboardLifecycleState.FOREGROUND_SERVICE, AppLifecycleTracker.determineLifecycleState())

        mockDelegate.copyPlainText("Secret", "JBSWY3DPEHPK3PXP", "App")
        val secret = decorator.readTotpSecret("FloatingOverlay")
        assertEquals("JBSWY3DPEHPK3PXP", secret)

        val logs = decorator.getAuditLogs()
        val readLog = logs.last()
        assertEquals("FloatingOverlay", readLog.callerContext)
        assertEquals(ClipboardLifecycleState.FOREGROUND_SERVICE, readLog.environment.lifecycleState)
        assertTrue(readLog.environment.isOverlayServiceRunning)

        // Cleanup
        AppLifecycleTracker.setOverlayServiceRunning(false)
    }

    // =========================================================================
    // TEST 6 — TwoFaRepository & Floating Overlay Flow with Decorator
    // =========================================================================
    @Test
    fun test6_TwoFaRepositoryWithClipboardDecorator() = runBlocking {
        val twoFaRepo = TwoFaRepository(context, appClipboardManager)
        val validSecret = "JBSWY3DPEHPK3PXP"

        // Put secret on clipboard
        appClipboardManager.copyPlainText("2FA Secret", validSecret, callerContext = "UserSetup")

        // Trigger generation from clipboard as FloatingOverlay caller
        val result = twoFaRepo.generateFromClipboard(callerContext = "FloatingOverlay")
        assertTrue(result.isSuccess)
        val totpResult = result.getOrNull()
        assertNotNull(totpResult)
        assertEquals(6, totpResult!!.code.length)

        val logs = appClipboardManager.getAuditLogs()
        // Should have: 1. COPY_PLAIN_TEXT, 2. READ_PRIMARY_CLIP (TwoFaRepo), 3. COPY_SECRET_TOTP (TwoFaRepo auto-copy)
        assertTrue(logs.size >= 3)
        val totpCopyLog = logs.last()
        assertEquals(ClipboardOperation.COPY_SECRET_TOTP, totpCopyLog.operation)
        assertEquals("FloatingOverlay", totpCopyLog.callerContext)
        assertTrue(totpCopyLog.isSensitiveFlagApplied)
    }

    // =========================================================================
    // TEST 7 — Audit Log Buffer Clearance & Bounds
    // =========================================================================
    @Test
    fun test7_AuditLogBufferClearance() {
        appClipboardManager.copyEmail("test1@nowcare.us")
        appClipboardManager.copyOtp("123456")
        assertTrue(appClipboardManager.getAuditLogs().isNotEmpty())

        appClipboardManager.clearAuditLogs()
        assertEquals(0, appClipboardManager.getAuditLogs().size)
    }
}
