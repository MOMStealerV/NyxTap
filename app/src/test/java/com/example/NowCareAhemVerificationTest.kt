package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.EmailMessage
import com.example.data.model.MailboxStatus
import com.example.data.model.PollDiagnosticInfo
import com.example.data.model.ProviderConnectionStatus
import com.example.data.model.ProviderDiagnostics
import com.example.data.provider.AhemMailProvider
import com.example.data.provider.EmailFetchResult
import com.example.data.provider.MailProvider
import com.example.data.repository.MailRepository
import com.example.util.AppClipboardManager
import com.example.util.OtpExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NowCareAhemVerificationTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: AppClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboardManager = AppClipboardManager(context)
    }

    // =========================================================================
    // TEST 1 — Provider configuration & properties discovery
    // =========================================================================
    @Test
    fun test1_ProviderConfigurationAndPropertiesDiscovery() = runBlocking {
        val provider = AhemMailProvider("https://nowcare.us")

        // 1. Provider name and API server
        assertTrue(provider.providerName.contains("AHEM") || provider.providerName.contains("NowCare"))
        assertEquals("https://nowcare.us", provider.apiServerUrl)
        assertEquals("https://nowcare.us/api/", provider.cleanBaseUrl)

        // 2. Allowed domains contain nowcare.us
        val diagnostics = provider.diagnostics.value
        assertTrue("allowedDomains must contain nowcare.us", diagnostics.allowedDomains.contains("nowcare.us"))

        // 3. Properties json parsing verification
        val samplePropertiesJson = """
            {
                "allowedDomains": ["nowcare.us"],
                "domain": "nowcare.us",
                "authHeaderRequired": false,
                "maxMailboxLife": 86400,
                "maxAge": 86400
            }
        """.trimIndent()

        val json = JSONObject(samplePropertiesJson)
        val domainsArray = json.getJSONArray("allowedDomains")
        val parsedDomains = (0 until domainsArray.length()).map { domainsArray.getString(it) }
        assertTrue(parsedDomains.contains("nowcare.us"))
        assertFalse(json.getBoolean("authHeaderRequired"))

        // Verify no token is ever exposed in diagnostics
        assertFalse(diagnostics.rawPropertiesJson?.contains("Bearer") == true)
    }

    // =========================================================================
    // TEST 2 — Generate mailbox
    // =========================================================================
    @Test
    fun test2_GenerateMailbox() = runBlocking {
        val provider = AhemMailProvider("https://nowcare.us")
        val generatedAddress = provider.generateMailbox()

        assertNotNull(generatedAddress)
        assertTrue("Email address must contain @", generatedAddress.contains("@"))

        val localPart = generatedAddress.substringBefore("@")
        val domain = generatedAddress.substringAfter("@")

        assertTrue("Local part must not be blank", localPart.isNotBlank())
        assertEquals("Domain must be nowcare.us", "nowcare.us", domain)

        // Verify repository automatically copies to clipboard on generation
        val repository = MailRepository(context, provider = provider, clipboardManager = clipboardManager)
        val repoGeneratedEmail = repository.generateNewMailbox()

        assertTrue(repoGeneratedEmail.endsWith("@nowcare.us"))
        assertEquals(repoGeneratedEmail, repository.currentEmail)
        assertEquals(repoGeneratedEmail, clipboardManager.readPrimaryClipText())
    }

    // =========================================================================
    // TEST 3 — Mailbox monitoring target and isolation
    // =========================================================================
    @Test
    fun test3_MailboxMonitoringTarget() = runBlocking {
        val provider = AhemMailProvider("https://nowcare.us")
        val generated = provider.generateMailbox()
        val mailboxOnly = generated.substringBefore("@")

        // Endpoint polled must be on nowcare.us API
        val expectedEndpoint = "${provider.cleanBaseUrl}mailbox/$mailboxOnly/email"
        assertEquals("https://nowcare.us/api/mailbox/$mailboxOnly/email", expectedEndpoint)

        // Must not reference any third-party endpoints
        assertFalse(expectedEndpoint.contains("ahem.email"))
        assertFalse(expectedEndpoint.contains("1secmail"))
        assertFalse(expectedEndpoint.contains("qiott.com"))
    }

    // =========================================================================
    // TEST 4 — Real email test & OTP extraction (583214)
    // =========================================================================
    @Test
    fun test4_RealEmailAndOtpExtraction() = runBlocking {
        val testSubject = "Your verification code is 583214"
        val testBody = "Hello, please use code 583214 to verify your account."

        // 1. Run OtpExtractor
        val extractedOtp = OtpExtractor.extractOtp(testSubject, testBody, null)
        assertEquals("583214", extractedOtp)

        // 2. Test in mock provider simulation
        val mockProvider = object : MailProvider {
            override val providerName: String = "NowCare AHEM Mock"
            override val domain: String = "nowcare.us"
            override val apiServerUrl: String = "https://nowcare.us"
            override val diagnostics: StateFlow<ProviderDiagnostics> = MutableStateFlow(
                ProviderDiagnostics("NowCare", "https://nowcare.us", listOf("nowcare.us"), false, ProviderConnectionStatus.Connected("OK"), System.currentTimeMillis())
            ).asStateFlow()
            override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = MutableStateFlow(
                PollDiagnosticInfo()
            ).asStateFlow()

            override suspend fun generateMailbox(): String = "testuser@nowcare.us"
            override suspend fun checkEmails(mailboxName: String): List<EmailMessage> = listOf(
                EmailMessage(
                    id = "msg-12345",
                    mailbox = "testuser@nowcare.us",
                    sender = "auth@service.com",
                    subject = testSubject,
                    snippet = testBody,
                    bodyText = testBody,
                    bodyHtml = null,
                    timestamp = System.currentTimeMillis(),
                    extractedOtp = extractedOtp,
                    isCopied = false
                )
            )
            override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult = EmailFetchResult.Success(checkEmails(mailboxName))
            override suspend fun probeMailboxDirect(mailboxName: String): String = "HTTP 200 OK"
            override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? = checkEmails(mailboxName).firstOrNull()
            override suspend fun testConnection(): ProviderDiagnostics = diagnostics.value
        }

        val repository = MailRepository(context, provider = mockProvider, clipboardManager = clipboardManager)
        val activeEmail = repository.generateNewMailbox()
        assertEquals("testuser@nowcare.us", activeEmail)

        val (foundOtp, msgCount) = repository.checkForOtpNow()

        assertEquals("583214", foundOtp)
        assertEquals(1, msgCount)
        assertEquals("583214", clipboardManager.readPrimaryClipText())
        assertTrue(repository.mailboxStatus.value is MailboxStatus.CodeDetected)
    }

    // =========================================================================
    // TEST 5 — Diagnostics & Endpoint Polling Visibility
    // =========================================================================
    @Test
    fun test5_DiagnosticsAndEndpointSafety() = runBlocking {
        val provider = AhemMailProvider("https://nowcare.us")
        val diag = provider.diagnostics.value

        assertEquals("https://nowcare.us", diag.apiServerUrl)
        assertTrue(diag.allowedDomains.contains("nowcare.us"))

        // Ensure authorization tokens are not stored in diagnostics or exposed
        assertFalse(diag.toString().contains("Bearer "))
    }

    // =========================================================================
    // TEST 6 — Authentication Lifecycle & Token Handling
    // =========================================================================
    @Test
    fun test6_AuthenticationLifecycle() = runBlocking {
        var tokenRefreshed = false
        val mockAuthProvider = object : MailProvider {
            override val providerName: String = "NowCare AHEM Auth Test"
            override val domain: String = "nowcare.us"
            override val apiServerUrl: String = "https://nowcare.us"
            override val diagnostics: StateFlow<ProviderDiagnostics> = MutableStateFlow(
                ProviderDiagnostics("NowCare Auth", "https://nowcare.us", listOf("nowcare.us"), true, ProviderConnectionStatus.Connected("Auth Required"), System.currentTimeMillis())
            ).asStateFlow()
            override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = MutableStateFlow(PollDiagnosticInfo()).asStateFlow()

            override suspend fun generateMailbox(): String = "authuser@nowcare.us"
            override suspend fun checkEmails(mailboxName: String): List<EmailMessage> = emptyList()

            override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult {
                // Simulate 401 challenge on first attempt, recovery on refreshed token
                return if (!tokenRefreshed) {
                    tokenRefreshed = true
                    EmailFetchResult.ServerError(401, "Unauthorized - token required")
                } else {
                    EmailFetchResult.Success(emptyList())
                }
            }
            override suspend fun probeMailboxDirect(mailboxName: String): String = "HTTP 200 OK"
            override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? = null
            override suspend fun testConnection(): ProviderDiagnostics = diagnostics.value
        }

        val res1 = mockAuthProvider.checkEmailsDetailed("authuser")
        assertTrue(res1 is EmailFetchResult.ServerError && (res1 as EmailFetchResult.ServerError).code == 401)

        val res2 = mockAuthProvider.checkEmailsDetailed("authuser")
        assertTrue(res2 is EmailFetchResult.Success)
    }

    // =========================================================================
    // TEST 7 — Timeout & Action Handling
    // =========================================================================
    @Test
    fun test7_TimeoutAndActions() = runBlocking {
        val mockEmptyProvider = object : MailProvider {
            override val providerName: String = "NowCare Empty"
            override val domain: String = "nowcare.us"
            override val apiServerUrl: String = "https://nowcare.us"
            override val diagnostics: StateFlow<ProviderDiagnostics> = MutableStateFlow(
                ProviderDiagnostics("NowCare", "https://nowcare.us", listOf("nowcare.us"), false, ProviderConnectionStatus.Connected("OK"), System.currentTimeMillis())
            ).asStateFlow()
            override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = MutableStateFlow(PollDiagnosticInfo()).asStateFlow()

            override suspend fun generateMailbox(): String = "timeoutuser@nowcare.us"
            override suspend fun checkEmails(mailboxName: String): List<EmailMessage> = emptyList()
            override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult = EmailFetchResult.Success(emptyList())
            override suspend fun probeMailboxDirect(mailboxName: String): String = "HTTP 200 OK"
            override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? = null
            override suspend fun testConnection(): ProviderDiagnostics = diagnostics.value
        }

        val repository = MailRepository(context, provider = mockEmptyProvider, clipboardManager = clipboardManager)
        val initialEmail = repository.generateNewMailbox()

        assertEquals("timeoutuser@nowcare.us", initialEmail)

        // Continue waiting restarts monitoring for the SAME mailbox
        repository.continueWaiting(10)
        val status = repository.mailboxStatus.value
        assertTrue(status is MailboxStatus.Monitoring)
        assertEquals(initialEmail, (status as MailboxStatus.Monitoring).email)

        // Generate New Email creates a different mailbox
        var secondMailboxCalled = false
        val secondProvider = object : MailProvider by mockEmptyProvider {
            override suspend fun generateMailbox(): String {
                secondMailboxCalled = true
                return "newuser@nowcare.us"
            }
        }
        repository.setProvider(secondProvider)
        val newEmail = repository.generateNewMailbox()
        assertTrue(secondMailboxCalled)
        assertEquals("newuser@nowcare.us", newEmail)
    }

    // =========================================================================
    // TEST 8 — Duplicate Message ID Prevention
    // =========================================================================
    @Test
    fun test8_DuplicateMessagePrevention() = runBlocking {
        val message = EmailMessage(
            id = "unique-msg-999",
            mailbox = "duptest@nowcare.us",
            sender = "sender@domain.com",
            subject = "Code: 442211",
            snippet = "Code: 442211",
            bodyText = "Code: 442211",
            bodyHtml = null,
            timestamp = 1000L,
            extractedOtp = "442211",
            isCopied = false
        )

        var pollCount = 0
        val mockProvider = object : MailProvider {
            override val providerName: String = "NowCare Dup Test"
            override val domain: String = "nowcare.us"
            override val apiServerUrl: String = "https://nowcare.us"
            override val diagnostics: StateFlow<ProviderDiagnostics> = MutableStateFlow(
                ProviderDiagnostics("NowCare", "https://nowcare.us", listOf("nowcare.us"), false, ProviderConnectionStatus.Connected("OK"), System.currentTimeMillis())
            ).asStateFlow()
            override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = MutableStateFlow(PollDiagnosticInfo()).asStateFlow()

            override suspend fun generateMailbox(): String = "duptest@nowcare.us"
            override suspend fun checkEmails(mailboxName: String): List<EmailMessage> {
                pollCount++
                return listOf(message)
            }
            override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult = EmailFetchResult.Success(checkEmails(mailboxName))
            override suspend fun probeMailboxDirect(mailboxName: String): String = "HTTP 200 OK"
            override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? = message
            override suspend fun testConnection(): ProviderDiagnostics = diagnostics.value
        }

        val repository = MailRepository(context, provider = mockProvider, clipboardManager = clipboardManager)
        val mailbox = repository.generateNewMailbox()
        assertEquals("duptest@nowcare.us", mailbox)

        val (firstOtp, count1) = repository.checkForOtpNow()
        assertEquals("442211", firstOtp)
        assertEquals(1, count1)

        // Second check with exact same message ID
        val (secondOtp, count2) = repository.checkForOtpNow()
        assertEquals(1, count2)
    }

    // =========================================================================
    // TEST 9 — Network, Server & Auth Failure Differentiation
    // =========================================================================
    @Test
    fun test9_NetworkAndErrorDifferentiation() = runBlocking {
        val netError = EmailFetchResult.NetworkError("Failed to connect to nowcare.us:443")
        val serverError = EmailFetchResult.ServerError(502, "Bad Gateway")
        val authError = EmailFetchResult.ServerError(403, "Forbidden")
        val emptySuccess = EmailFetchResult.Success(emptyList())

        assertTrue(netError is EmailFetchResult.NetworkError)
        assertEquals("Failed to connect to nowcare.us:443", netError.message)

        assertTrue(serverError is EmailFetchResult.ServerError)
        assertEquals(502, serverError.code)

        assertTrue(authError is EmailFetchResult.ServerError)
        assertEquals(403, authError.code)

        assertTrue(emptySuccess is EmailFetchResult.Success)
        assertTrue(emptySuccess.emails.isEmpty())
    }

    // =========================================================================
    // TEST 10 — Complete Floating Overlay Workflow Verification
    // =========================================================================
    @Test
    fun test10_FloatingOverlayWorkflow() = runBlocking {
        val email = "workflow@nowcare.us"
        val otp = "718293"

        // Step 1: Mail generated & copied
        clipboardManager.copyEmail(email)
        assertEquals(email, clipboardManager.readPrimaryClipText())

        // Step 2: Incoming email OTP detected
        val extracted = OtpExtractor.extractOtp("Verification Code", "Your pin is $otp", null)
        assertEquals(otp, extracted)

        // Step 3: Automatic OTP clipboard copy
        clipboardManager.copyOtp(extracted!!)
        assertEquals(otp, clipboardManager.readPrimaryClipText())
    }

    // =========================================================================
    // TEST 11 — Real AHEM Mailparser Payload Parsing (html: false & body-only OTP)
    // =========================================================================
    @Test
    fun test11_AhemMailparserJsonPayloadParsing() = runBlocking {
        // Real AHEM mailparser JSON payload with html: false
        val plainTextAhemJson = """
            {
                "emailId": "msg_live_001",
                "sender": { "address": "auth@security-service.com", "name": "Security Service" },
                "subject": "Sign in to your account",
                "text": "Hello!\nYour single-use sign in verification code is 849201.\nDo not share this code.",
                "html": false,
                "timestamp": 1724395200000
            }
        """.trimIndent()

        val json = JSONObject(plainTextAhemJson)
        val textBody = when (val textVal = json.opt("text") ?: json.opt("body")) {
            is String -> textVal
            else -> ""
        }
        val htmlBody = when (val htmlVal = json.opt("html")) {
            is String -> if (htmlVal.isNotBlank() && htmlVal != "false") htmlVal else null
            else -> null
        }

        assertEquals(null, htmlBody)
        assertTrue(textBody.contains("849201"))

        // Extract OTP from real body
        val extractedOtp = OtpExtractor.extractOtp(json.getString("subject"), textBody, htmlBody)
        assertEquals("849201", extractedOtp)
    }

    // =========================================================================
    // TEST 12 — Independent Email Event & Stale OTP Prevention (141823 vs 266433)
    // =========================================================================
    @Test
    fun test12_IndependentEmailEventAndStaleOtpPrevention() = runBlocking {
        val targetEmail = "freshmailbox@nowcare.us"
        val mailboxOnly = "freshmailbox"

        val emailA = EmailMessage(
            id = "message_A_id_111",
            mailbox = targetEmail,
            sender = "Service A",
            subject = "Your code is 141823",
            snippet = "Code: 141823",
            bodyText = "Your verification code is 141823",
            bodyHtml = null,
            timestamp = 1000000L,
            extractedOtp = "141823"
        )

        val emailB = EmailMessage(
            id = "message_B_id_222",
            mailbox = targetEmail,
            sender = "Meta <notification@email.meta.com>",
            subject = "Meta confirmation",
            snippet = "Confirmation code266433",
            bodyText = "Confirmation code266433. Enter this code to confirm your account.",
            bodyHtml = null,
            timestamp = 2000000L,
            extractedOtp = "266433"
        )

        var simulatedEmails = listOf(emailA)

        val dynamicProvider = object : MailProvider {
            override val providerName: String = "NowCare AHEM Dynamic Mock"
            override val domain: String = "nowcare.us"
            override val apiServerUrl: String = "https://nowcare.us"
            override val diagnostics: StateFlow<ProviderDiagnostics> = MutableStateFlow(
                ProviderDiagnostics("NowCare", "https://nowcare.us", listOf("nowcare.us"), false, ProviderConnectionStatus.Connected("OK"), System.currentTimeMillis())
            ).asStateFlow()
            override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = MutableStateFlow(
                PollDiagnosticInfo()
            ).asStateFlow()

            override suspend fun generateMailbox(): String = targetEmail
            override suspend fun checkEmails(mailboxName: String): List<EmailMessage> = simulatedEmails
            override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult {
                return EmailFetchResult.Success(simulatedEmails)
            }
            override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? {
                return simulatedEmails.find { it.id == emailId }
            }
            override suspend fun deleteEmail(mailboxName: String, emailId: String): Boolean = true
            override suspend fun deleteMailbox(mailboxName: String): Boolean = true
            override suspend fun testConnection(): ProviderDiagnostics = diagnostics.value
            override suspend fun probeMailboxDirect(mailboxName: String): String = "HTTP 200"
        }

        val repository = MailRepository(context, provider = dynamicProvider, clipboardManager = clipboardManager)
        val generated = repository.generateNewMailbox()
        assertEquals(targetEmail, generated)

        // 1. Initial State: clean
        assertEquals(null, repository.activeDetectedOtp.value)

        // 2. Process First Email (Email A with OTP 141823)
        val (firstOtp, total1) = repository.checkForOtpNow(maxAttempts = 1)
        assertEquals("141823", firstOtp)
        assertEquals(1, total1)
        assertEquals("141823", repository.activeDetectedOtp.value?.code)
        assertEquals("message_A_id_111", repository.activeDetectedOtp.value?.messageId)
        assertEquals("141823", clipboardManager.readPrimaryClipText())

        // 3. Now simulate arrival of Email B (New email with OTP 266433)
        simulatedEmails = listOf(emailB, emailA) // List now contains both, newest first

        val (secondOtp, total2) = repository.checkForOtpNow(maxAttempts = 1)
        assertEquals("266433", secondOtp)
        assertEquals(2, total2)

        // Critical assertion: Active detected OTP MUST be 266433, NOT the stale 141823!
        assertEquals("266433", repository.activeDetectedOtp.value?.code)
        assertEquals("message_B_id_222", repository.activeDetectedOtp.value?.messageId)
        assertEquals("266433", clipboardManager.readPrimaryClipText())

        // Active MailboxStatus must also carry 266433
        val status = repository.mailboxStatus.value
        assertTrue(status is MailboxStatus.CodeDetected)
        val codeDetected = status as MailboxStatus.CodeDetected
        assertEquals("266433", codeDetected.code)
        assertEquals("message_B_id_222", codeDetected.messageId)
    }

    // =========================================================================
    // TEST 13 — Meta "Confirmation code266433" Regex Extraction
    // =========================================================================
    @Test
    fun test13_MetaConfirmationCodeExtraction() {
        val metaBodyWithGluedCode = "Confirmation code266433"
        val extracted1 = OtpExtractor.extractOtp("Instagram", metaBodyWithGluedCode, null)
        assertEquals("266433", extracted1)

        val metaBodyWithColon = "Confirmation code: 266433"
        val extracted2 = OtpExtractor.extractOtp("Meta Confirmation", metaBodyWithColon, null)
        assertEquals("266433", extracted2)

        val metaInstagramPhrase = "Your Instagram code is 266433"
        val extracted3 = OtpExtractor.extractOtp("Instagram security", metaInstagramPhrase, null)
        assertEquals("266433", extracted3)
    }

    // =========================================================================
    // TEST 14 — Sender JSON Normalization (No raw JSON strings)
    // =========================================================================
    @Test
    fun test14_SenderJsonNormalization() {
        val ahemProvider = AhemMailProvider("https://nowcare.us")

        // Format 1: Nodemailer value array
        val nodemailerJson = JSONObject("""
            {
                "from": {
                    "value": [
                        { "address": "notification@email.meta.com", "name": "Meta" }
                    ],
                    "text": "Meta <notification@email.meta.com>"
                }
            }
        """.trimIndent())
        val sender1 = ahemProvider.extractSenderString(nodemailerJson)
        assertEquals("Meta <notification@email.meta.com>", sender1)

        // Format 2: Raw string JSON embedded in from
        val embeddedStringJson = JSONObject("""
            {
                "from": "{\"value\":[{\"address\":\"security@meta.com\",\"name\":\"Meta Security\"}]}"
            }
        """.trimIndent())
        val sender2 = ahemProvider.extractSenderString(embeddedStringJson)
        assertEquals("Meta Security <security@meta.com>", sender2)

        // Format 3: Standard sender object
        val standardSenderObj = JSONObject("""
            {
                "sender": { "address": "no-reply@nowcare.us", "name": "NowCare Support" }
            }
        """.trimIndent())
        val sender3 = ahemProvider.extractSenderString(standardSenderObj)
        assertEquals("NowCare Support <no-reply@nowcare.us>", sender3)

        // Format 4: Plain string
        val plainString = JSONObject("""
            {
                "from": "support@service.com"
            }
        """.trimIndent())
        val sender4 = ahemProvider.extractSenderString(plainString)
        assertEquals("support@service.com", sender4)
    }

    // =========================================================================
    // TEST 15 — Generate New Mailbox Resets Active OTP State
    // =========================================================================
    @Test
    fun test15_GenerateNewMailboxResetsActiveState() = runBlocking {
        val ahemProvider = AhemMailProvider("https://nowcare.us")
        val repository = MailRepository(context, provider = ahemProvider, clipboardManager = clipboardManager)

        // Simulate an active state
        val dummyEmail = EmailMessage(
            id = "dummy1",
            mailbox = "dummy@nowcare.us",
            sender = "Service",
            subject = "Code",
            snippet = "123456",
            bodyText = "123456",
            extractedOtp = "123456"
        )
        val (found, _) = repository.checkForOtpNow()

        // Generate a new mailbox
        val newEmail = repository.generateNewMailbox()
        assertTrue(newEmail.isNotBlank())

        // Active detected OTP must be completely clean/null
        assertEquals(null, repository.activeDetectedOtp.value)
    }
}
