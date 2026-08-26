package com.example.data.provider

import com.example.data.config.NowCareConfig
import com.example.data.model.EmailMessage
import com.example.data.model.MailDeliveryStatus
import com.example.data.model.PollDiagnosticInfo
import com.example.data.model.ProviderConnectionStatus
import com.example.data.model.ProviderDiagnostics
import com.example.util.OtpExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class EmailFetchResult {
    data class Success(
        val emails: List<EmailMessage>,
        val httpCode: Int = 200,
        val messageIds: List<String> = emails.map { it.id },
        val responseSummary: String = if (emails.isEmpty()) {
            "NowCare API is reachable, but this mailbox currently contains no messages."
        } else {
            "HTTP 200 OK (${emails.size} email(s) returned)"
        }
    ) : EmailFetchResult()

    data class NetworkError(
        val message: String,
        val responseSummary: String = "Network error: $message"
    ) : EmailFetchResult()

    data class ServerError(
        val code: Int,
        val message: String,
        val responseSummary: String = "HTTP $code: $message"
    ) : EmailFetchResult()
}

interface MailProvider {
    val providerName: String
    val domain: String
    val apiServerUrl: String
    val diagnostics: StateFlow<ProviderDiagnostics>
    val pollDiagnostics: StateFlow<PollDiagnosticInfo>
    suspend fun generateMailbox(): String // Returns the full email address
    suspend fun checkEmails(mailboxName: String): List<EmailMessage>
    suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult
    suspend fun probeMailboxDirect(mailboxName: String): String
    suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage?
    suspend fun testConnection(): ProviderDiagnostics
    suspend fun deleteEmail(mailboxName: String, emailId: String): Boolean = false
    suspend fun deleteMailbox(mailboxName: String): Boolean = false
}

/**
 * AHEM / NowCare Mail Provider implementation.
 *
 * Configured to use the NowCare AHEM deployment:
 * 1. API Server: https://nowcare.us (API base: https://nowcare.us/api/)
 * 2. Queries /api/properties endpoint to discover allowedDomains and auth requirements dynamically.
 * 3. Enforces domain strictly to nowcare.us.
 * 4. Manages mailbox lifecycle (generation, monitoring, token handling).
 */
class AhemMailProvider(
    serverUrl: String = NowCareConfig.BASE_URL
) : MailProvider {

    override val providerName: String = "AHEM / NowCare"

    private val normalizedServerUrl: String = serverUrl.trim().trimEnd('/')
    override val apiServerUrl: String get() = normalizedServerUrl

    val cleanBaseUrl: String
        get() = if (normalizedServerUrl.endsWith("/api")) {
            "$normalizedServerUrl/"
        } else {
            "$normalizedServerUrl/api/"
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val random = SecureRandom()
    private val charPool: List<Char> = ('a'..'z') + ('0'..'9')

    // Dynamic properties fetched from /api/properties
    private val propertiesMutex = Mutex()
    private var allowedDomains: List<String> = NowCareConfig.ALLOWED_DOMAINS
    private var defaultDomain: String = NowCareConfig.DEFAULT_DOMAIN
    private var authHeaderRequired: Boolean = false
    private var propertiesLoaded: Boolean = false

    // Active domain property
    private var currentActiveDomain: String = NowCareConfig.DEFAULT_DOMAIN
    override val domain: String get() = currentActiveDomain

    // Diagnostics StateFlow for UI Inspection
    private val _diagnostics = MutableStateFlow(
        ProviderDiagnostics(
            providerName = providerName,
            apiServerUrl = normalizedServerUrl,
            allowedDomains = NowCareConfig.ALLOWED_DOMAINS,
            authHeaderRequired = false,
            status = ProviderConnectionStatus.Connected("Configured for NowCare AHEM"),
            lastCheckedTimestamp = System.currentTimeMillis()
        )
    )
    override val diagnostics: StateFlow<ProviderDiagnostics> = _diagnostics.asStateFlow()

    // Live Polling Diagnostics for real-time Developer Inspection Panel
    private val _pollDiagnostics = MutableStateFlow(
        PollDiagnosticInfo(
            serverUrl = cleanBaseUrl,
            pollingEndpoint = "${cleanBaseUrl}mailbox/<mailbox>/email"
        )
    )
    override val pollDiagnostics: StateFlow<PollDiagnosticInfo> = _pollDiagnostics.asStateFlow()

    // Cache of Bearer tokens per mailbox (mailboxName -> token)
    private val tokenCache = ConcurrentHashMap<String, String>()

    /**
     * Loads server properties from /api/properties endpoint dynamically.
     */
    suspend fun loadProperties(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        propertiesMutex.withLock {
            if (propertiesLoaded && !force) return@withContext true

            _diagnostics.value = _diagnostics.value.copy(
                status = ProviderConnectionStatus.Checking
            )

            val url = "${cleanBaseUrl}properties"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                        val json = JSONObject(bodyString)

                        // Read allowed domains array dynamically from server properties
                        val domainsList = mutableListOf<String>()
                        if (json.has("allowedDomains")) {
                            val arr = json.optJSONArray("allowedDomains")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val d = arr.optString(i).trim()
                                    if (d.isNotBlank() && !domainsList.contains(d)) {
                                        domainsList.add(d)
                                    }
                                }
                            }
                        }

                        val serverDomain = json.optString("domain", "").trim()
                        if (serverDomain.isNotBlank() && !domainsList.contains(serverDomain)) {
                            domainsList.add(serverDomain)
                        }

                        if (domainsList.isNotEmpty()) {
                            // Filter domains to sanitize
                            val sanitizedDomains = domainsList.filter { NowCareConfig.isDomainAllowed(it) }
                            allowedDomains = if (sanitizedDomains.isNotEmpty()) sanitizedDomains else NowCareConfig.ALLOWED_DOMAINS
                            defaultDomain = if (allowedDomains.contains("nowcare.us")) "nowcare.us" else allowedDomains.first()
                            currentActiveDomain = defaultDomain
                        }

                        authHeaderRequired = json.optBoolean("authHeaderRequired", false)
                        val maxLife = json.optLong("maxMailboxLife", 86400)
                        val maxAge = json.optLong("maxAge", 86400)

                        propertiesLoaded = true

                        _diagnostics.value = ProviderDiagnostics(
                            providerName = providerName,
                            apiServerUrl = normalizedServerUrl,
                            allowedDomains = allowedDomains,
                            authHeaderRequired = authHeaderRequired,
                            status = ProviderConnectionStatus.Connected("Connected (${allowedDomains.joinToString()})"),
                            lastCheckedTimestamp = System.currentTimeMillis(),
                            maxMailboxLife = maxLife,
                            maxAge = maxAge,
                            rawPropertiesJson = bodyString
                        )
                        return@withContext true
                    } else {
                        val errorMsg = "HTTP ${response.code}: ${response.message.ifBlank { "Properties unavailable" }}"
                        _diagnostics.value = _diagnostics.value.copy(
                            status = ProviderConnectionStatus.Error(errorMsg, response.code),
                            lastCheckedTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: IOException) {
                _diagnostics.value = _diagnostics.value.copy(
                    status = ProviderConnectionStatus.Error("Network error: ${e.localizedMessage ?: "Cannot reach server"}"),
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _diagnostics.value = _diagnostics.value.copy(
                    status = ProviderConnectionStatus.Error("Error: ${e.localizedMessage ?: "Unknown error"}"),
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            }
            propertiesLoaded = true
            false
        }
    }

    override suspend fun testConnection(): ProviderDiagnostics = withContext(Dispatchers.IO) {
        loadProperties(force = true)
        _diagnostics.value
    }

    /**
     * Obtains a Bearer authentication token for a mailbox if required or challenged.
     */
    private suspend fun getAuthToken(mailboxOnly: String, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            tokenCache[mailboxOnly]?.let { return@withContext it }
        }

        val url = "${cleanBaseUrl}auth/token"
        val jsonPayload = JSONObject().apply {
            put("mailbox", mailboxOnly)
        }.toString()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@withContext null
                    val json = JSONObject(bodyString)
                    val token = json.optString("token", "")
                    if (token.isNotBlank()) {
                        tokenCache[mailboxOnly] = token
                        return@withContext token
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore auth token fetch error
        }
        null
    }

    /**
     * Generates a new random mailbox on an allowed domain discovered from the NowCare server.
     */
    override suspend fun generateMailbox(): String = withContext(Dispatchers.IO) {
        loadProperties()

        val selectedDomain = NowCareConfig.DEFAULT_DOMAIN
        currentActiveDomain = selectedDomain

        val randomPrefix = (1..9)
            .map { random.nextInt(charPool.size) }
            .map(charPool::get)
            .joinToString("")

        // Pre-fetch auth token if server requires authentication headers
        if (authHeaderRequired) {
            getAuthToken(randomPrefix)
        }

        "$randomPrefix@$selectedDomain"
    }

    /**
     * Checks emails for a specified mailbox.
     */
    override suspend fun checkEmails(mailboxName: String): List<EmailMessage> {
        return when (val result = checkEmailsDetailed(mailboxName)) {
            is EmailFetchResult.Success -> result.emails
            else -> emptyList()
        }
    }

    /**
     * Checks emails with detailed result distinguishing between network errors, server errors, and empty mailbox.
     */
    override suspend fun checkEmailsDetailed(mailboxName: String): EmailFetchResult = withContext(Dispatchers.IO) {
        loadProperties()

        val mailboxOnly = mailboxName.substringBefore("@")
        val domainPart = mailboxName.substringAfter("@", currentActiveDomain)
        val url = "${cleanBaseUrl}mailbox/$mailboxOnly/email"

        var token = tokenCache[mailboxOnly]
        if (token == null && authHeaderRequired) {
            token = getAuthToken(mailboxOnly)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val responseCode = resp.code

                // Handle token expiration or auth challenge (401 / 403)
                if (responseCode == 401 || responseCode == 403) {
                    val refreshedToken = getAuthToken(mailboxOnly, forceRefresh = true)
                    if (!refreshedToken.isNullOrBlank()) {
                        val retryReq = Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer $refreshedToken")
                            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            if (!retryResp.isSuccessful) {
                                val errSummary = "Authentication required/rejected (HTTP ${retryResp.code})"
                                _pollDiagnostics.value = _pollDiagnostics.value.copy(
                                    serverUrl = cleanBaseUrl,
                                    mailbox = mailboxOnly,
                                    email = "$mailboxOnly@$domainPart",
                                    pollingEndpoint = url,
                                    lastPollTimestamp = System.currentTimeMillis(),
                                    httpStatus = retryResp.code,
                                    responseSummary = errSummary,
                                    emailsReturnedCount = 0,
                                    returnedMessageIds = emptyList(),
                                    lastError = errSummary,
                                    deliveryStatus = MailDeliveryStatus.DeliveryFailed
                                )
                                return@withContext EmailFetchResult.ServerError(retryResp.code, "Authentication rejected", errSummary)
                            }
                            val emails = parseEmailList(retryResp.body?.string(), mailboxOnly, domainPart)
                            val summary = if (emails.isEmpty()) {
                                "NowCare API is reachable, but this mailbox currently contains no messages."
                            } else {
                                "HTTP 200 OK (${emails.size} email(s) returned)"
                            }
                            _pollDiagnostics.value = _pollDiagnostics.value.copy(
                                serverUrl = cleanBaseUrl,
                                mailbox = mailboxOnly,
                                email = "$mailboxOnly@$domainPart",
                                pollingEndpoint = url,
                                lastPollTimestamp = System.currentTimeMillis(),
                                httpStatus = 200,
                                responseSummary = summary,
                                emailsReturnedCount = emails.size,
                                returnedMessageIds = emails.map { it.id },
                                latestMessageSubject = emails.firstOrNull()?.subject,
                                latestMessageSender = emails.firstOrNull()?.sender,
                                lastError = null,
                                deliveryStatus = if (emails.isNotEmpty()) MailDeliveryStatus.VerifiedReceived else MailDeliveryStatus.VerifiedEmpty
                            )
                            return@withContext EmailFetchResult.Success(emails, httpCode = 200, responseSummary = summary)
                        }
                    } else {
                        val errSummary = "Authentication required/rejected (HTTP $responseCode)"
                        _pollDiagnostics.value = _pollDiagnostics.value.copy(
                            serverUrl = cleanBaseUrl,
                            mailbox = mailboxOnly,
                            email = "$mailboxOnly@$domainPart",
                            pollingEndpoint = url,
                            lastPollTimestamp = System.currentTimeMillis(),
                            httpStatus = responseCode,
                            responseSummary = errSummary,
                            emailsReturnedCount = 0,
                            returnedMessageIds = emptyList(),
                            lastError = errSummary,
                            deliveryStatus = MailDeliveryStatus.DeliveryFailed
                        )
                        return@withContext EmailFetchResult.ServerError(responseCode, "Authentication required/rejected", errSummary)
                    }
                }

                // If 404, mailbox is simply empty/not created yet on AHEM servers
                if (responseCode == 404) {
                    val summary = "NowCare API is reachable, but this mailbox currently contains no messages."
                    _pollDiagnostics.value = _pollDiagnostics.value.copy(
                        serverUrl = cleanBaseUrl,
                        mailbox = mailboxOnly,
                        email = "$mailboxOnly@$domainPart",
                        pollingEndpoint = url,
                        lastPollTimestamp = System.currentTimeMillis(),
                        httpStatus = 200, // Normalized for client empty mailbox state
                        responseSummary = summary,
                        emailsReturnedCount = 0,
                        returnedMessageIds = emptyList(),
                        lastError = null,
                        deliveryStatus = MailDeliveryStatus.VerifiedEmpty
                    )
                    return@withContext EmailFetchResult.Success(emptyList(), httpCode = 200, responseSummary = summary)
                }

                if (!resp.isSuccessful) {
                    val errSummary = "Server response: HTTP $responseCode (${resp.message})"
                    _pollDiagnostics.value = _pollDiagnostics.value.copy(
                        serverUrl = cleanBaseUrl,
                        mailbox = mailboxOnly,
                        email = "$mailboxOnly@$domainPart",
                        pollingEndpoint = url,
                        lastPollTimestamp = System.currentTimeMillis(),
                        httpStatus = responseCode,
                        responseSummary = errSummary,
                        emailsReturnedCount = 0,
                        returnedMessageIds = emptyList(),
                        lastError = errSummary,
                        deliveryStatus = MailDeliveryStatus.DeliveryFailed
                    )
                    return@withContext EmailFetchResult.ServerError(responseCode, errSummary, errSummary)
                }

                val emails = parseEmailList(resp.body?.string(), mailboxOnly, domainPart)
                val summary = if (emails.isEmpty()) {
                    "NowCare API is reachable, but this mailbox currently contains no messages."
                } else {
                    "HTTP 200 OK (${emails.size} email(s) returned)"
                }
                _pollDiagnostics.value = _pollDiagnostics.value.copy(
                    serverUrl = cleanBaseUrl,
                    mailbox = mailboxOnly,
                    email = "$mailboxOnly@$domainPart",
                    pollingEndpoint = url,
                    lastPollTimestamp = System.currentTimeMillis(),
                    httpStatus = 200,
                    responseSummary = summary,
                    emailsReturnedCount = emails.size,
                    returnedMessageIds = emails.map { it.id },
                    latestMessageSubject = emails.firstOrNull()?.subject,
                    latestMessageSender = emails.firstOrNull()?.sender,
                    lastError = null,
                    deliveryStatus = if (emails.isNotEmpty()) MailDeliveryStatus.VerifiedReceived else MailDeliveryStatus.VerifiedEmpty
                )
                return@withContext EmailFetchResult.Success(emails, httpCode = 200, responseSummary = summary)
            }
        } catch (e: IOException) {
            val errSummary = "Network error: ${e.localizedMessage ?: "Timeout / Connection failed"}"
            _pollDiagnostics.value = _pollDiagnostics.value.copy(
                serverUrl = cleanBaseUrl,
                mailbox = mailboxOnly,
                email = "$mailboxOnly@$domainPart",
                pollingEndpoint = url,
                lastPollTimestamp = System.currentTimeMillis(),
                httpStatus = null,
                responseSummary = errSummary,
                lastError = errSummary,
                deliveryStatus = MailDeliveryStatus.DeliveryFailed
            )
            return@withContext EmailFetchResult.NetworkError(errSummary, errSummary)
        } catch (e: Exception) {
            val errSummary = "Unexpected error: ${e.localizedMessage ?: "Unknown"}"
            _pollDiagnostics.value = _pollDiagnostics.value.copy(
                serverUrl = cleanBaseUrl,
                mailbox = mailboxOnly,
                email = "$mailboxOnly@$domainPart",
                pollingEndpoint = url,
                lastPollTimestamp = System.currentTimeMillis(),
                httpStatus = null,
                responseSummary = errSummary,
                lastError = errSummary,
                deliveryStatus = MailDeliveryStatus.DeliveryFailed
            )
            return@withContext EmailFetchResult.NetworkError(errSummary, errSummary)
        }
    }

    /**
     * Direct mailbox probe with accurate status representation and authentication handling.
     */
    override suspend fun probeMailboxDirect(mailboxName: String): String = withContext(Dispatchers.IO) {
        val mailboxOnly = mailboxName.substringBefore("@")
        val domainPart = mailboxName.substringAfter("@", currentActiveDomain)
        val url = "${cleanBaseUrl}mailbox/$mailboxOnly/email"

        var token = tokenCache[mailboxOnly]
        if (token == null && authHeaderRequired) {
            token = getAuthToken(mailboxOnly)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
            .get()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code

                // Handle token challenge or expiration (401 / 403)
                if (code == 401 || code == 403) {
                    val refreshedToken = getAuthToken(mailboxOnly, forceRefresh = true)
                    if (!refreshedToken.isNullOrBlank()) {
                        val retryReq = Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer $refreshedToken")
                            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
                            .get()
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            return@withContext formatProbeResponse(retryResp, mailboxOnly, domainPart)
                        }
                    } else {
                        return@withContext "STATUS: HTTP $code\nAuthentication required/rejected on NowCare server."
                    }
                }

                return@withContext formatProbeResponse(resp, mailboxOnly, domainPart)
            }
        } catch (e: Exception) {
            "STATUS: Network failure\nError: ${e.localizedMessage ?: "Could not connect to NowCare server"}"
        }
    }

    private suspend fun formatProbeResponse(response: Response, mailboxOnly: String, domainPart: String): String {
        val code = response.code
        val body = response.body?.string().orEmpty()

        return if (code == 404 || (code == 200 && (body.trim() == "[]" || body.trim().isBlank()))) {
            "STATUS: HTTP $code\nNowCare API is reachable, but this mailbox currently contains no messages."
        } else if (response.isSuccessful) {
            val emails = parseEmailList(body, mailboxOnly, domainPart)
            if (emails.isEmpty()) {
                "STATUS: HTTP $code\nNowCare API is reachable, but this mailbox currently contains no messages."
            } else {
                val first = emails.first()
                "STATUS: HTTP $code OK\nEmail count: ${emails.size}\nMessage ID: ${first.id}\nSubject: ${first.subject}\nFrom: ${first.sender}"
            }
        } else if (code == 401 || code == 403) {
            "STATUS: HTTP $code\nAuthentication required/rejected on NowCare server."
        } else {
            "STATUS: HTTP $code\nServer error: ${response.message}"
        }
    }

    private suspend fun parseEmailList(bodyString: String?, mailboxOnly: String, domainPart: String): List<EmailMessage> {
        if (bodyString.isNullOrBlank()) return emptyList()
        val jsonArray = try {
            JSONArray(bodyString)
        } catch (e: Exception) {
            return emptyList()
        }

        val list = mutableListOf<EmailMessage>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val emailId = obj.optString("emailId", obj.optString("id", obj.optString("_id", "")))
            if (emailId.isBlank()) continue

            val summarySender = extractSenderString(obj)
            val summarySubject = obj.optString("subject", "(No Subject)")
            val summarySnippet = obj.optString("snippet", obj.optString("text", summarySubject))
            val summaryTimestamp = parseTimestamp(obj)
            val summaryOtp = OtpExtractor.extractOtp(bodyText = summarySnippet, bodyHtml = null, subject = summarySubject)

            val fullMessage = getEmailDetails("$mailboxOnly@$domainPart", emailId)

            val message = fullMessage ?: EmailMessage(
                id = emailId,
                mailbox = "$mailboxOnly@$domainPart",
                sender = summarySender,
                subject = summarySubject,
                snippet = summarySnippet.take(140).ifBlank { summarySubject },
                bodyText = summarySnippet.ifBlank { summarySubject },
                bodyHtml = null,
                timestamp = summaryTimestamp,
                extractedOtp = summaryOtp,
                isCopied = false
            )
            list.add(message)
        }
        return list.sortedByDescending { it.timestamp }
    }

    /**
     * Gets full email details including content and OTP detection.
     */
    override suspend fun getEmailDetails(mailboxName: String, emailId: String): EmailMessage? = withContext(Dispatchers.IO) {
        val mailboxOnly = mailboxName.substringBefore("@")
        val domainPart = mailboxName.substringAfter("@", currentActiveDomain)
        val url = "${cleanBaseUrl}mailbox/$mailboxOnly/email/$emailId"

        val token = tokenCache[mailboxOnly]
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    val refreshedToken = getAuthToken(mailboxOnly, forceRefresh = true)
                    if (!refreshedToken.isNullOrBlank()) {
                        val retryReq = Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer $refreshedToken")
                            .header("User-Agent", "TempMail2FA-NowCare/1.0 (Android)")
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            if (!retryResp.isSuccessful) return@withContext null
                            return@withContext parseEmailDetails(retryResp.body?.string(), mailboxOnly, domainPart, emailId)
                        }
                    }
                }

                if (!response.isSuccessful) return@withContext null
                return@withContext parseEmailDetails(response.body?.string(), mailboxOnly, domainPart, emailId)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEmailDetails(bodyString: String?, mailboxOnly: String, domainPart: String, emailId: String): EmailMessage? {
        if (bodyString.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(bodyString)
            val sender = extractSenderString(obj)
            val subject = obj.optString("subject", "(No Subject)")

            val textBody = when (val textVal = obj.opt("text") ?: obj.opt("body")) {
                is String -> textVal
                else -> ""
            }
            val htmlBody = when (val htmlVal = obj.opt("html") ?: obj.opt("textAsHtml")) {
                is String -> if (htmlVal.isNotBlank() && htmlVal != "false") htmlVal else null
                else -> null
            }
            val timestamp = parseTimestamp(obj)

            // Ensure proper named parameter extraction
            val otp = OtpExtractor.extractOtp(bodyText = textBody, bodyHtml = htmlBody, subject = subject)

            val displaySnippet = textBody.trim().take(140).ifBlank {
                if (!htmlBody.isNullOrBlank()) {
                    OtpExtractor.cleanHtml(htmlBody).take(140)
                } else {
                    subject
                }
            }

            val fullBodyText = textBody.ifBlank {
                if (!htmlBody.isNullOrBlank()) {
                    OtpExtractor.cleanHtml(htmlBody)
                } else {
                    subject
                }
            }

            EmailMessage(
                id = emailId,
                mailbox = "$mailboxOnly@$domainPart",
                sender = sender,
                subject = subject,
                snippet = displaySnippet,
                bodyText = fullBodyText,
                bodyHtml = htmlBody,
                timestamp = timestamp,
                extractedOtp = otp,
                isCopied = false
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes a specific email from the mailbox.
     */
    override suspend fun deleteEmail(mailboxName: String, emailId: String): Boolean = withContext(Dispatchers.IO) {
        val mailboxOnly = mailboxName.substringBefore("@")
        val url = "${cleanBaseUrl}mailbox/$mailboxOnly/email/$emailId"
        val token = tokenCache[mailboxOnly]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .delete()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes the entire mailbox and clears local cached tokens for lifecycle management.
     */
    override suspend fun deleteMailbox(mailboxName: String): Boolean = withContext(Dispatchers.IO) {
        val mailboxOnly = mailboxName.substringBefore("@")
        val url = "${cleanBaseUrl}mailbox/$mailboxOnly"
        val token = tokenCache[mailboxOnly]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .delete()

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        tokenCache.remove(mailboxOnly)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extraction of email sender name and address from various AHEM / nodemailer JSON formats.
     */
    fun extractSenderString(obj: JSONObject): String {
        val fromVal = obj.opt("from")
        val fromParsed = parseSenderValue(fromVal)
        if (fromParsed.isNotBlank()) return fromParsed

        val senderVal = obj.opt("sender")
        val senderParsed = parseSenderValue(senderVal)
        if (senderParsed.isNotBlank()) return senderParsed

        return "Unknown Sender"
    }

    private fun parseSenderValue(value: Any?): String {
        if (value == null) return ""
        if (value is String) {
            val str = value.trim()
            if (str.startsWith("{") && str.endsWith("}")) {
                try {
                    val parsedObj = JSONObject(str)
                    val result = parseSenderValue(parsedObj)
                    if (result.isNotBlank()) return result
                } catch (e: Exception) {
                    // fall through
                }
            } else if (str.startsWith("[") && str.endsWith("]")) {
                try {
                    val parsedArr = JSONArray(str)
                    val result = parseSenderValue(parsedArr)
                    if (result.isNotBlank()) return result
                } catch (e: Exception) {
                    // fall through
                }
            }
            return str
        }
        if (value is JSONObject) {
            val text = value.optString("text", "")
            if (text.isNotBlank()) return text.trim()
            val valArray = value.optJSONArray("value")
            if (valArray != null && valArray.length() > 0) {
                val first = valArray.opt(0)
                val parsed = parseSenderValue(first)
                if (parsed.isNotBlank()) return parsed
            }
            val name = value.optString("name", "")
            val address = value.optString("address", "")
            return if (name.isNotBlank() && address.isNotBlank()) {
                "$name <$address>"
            } else if (name.isNotBlank()) {
                name
            } else {
                address
            }
        }
        if (value is JSONArray && value.length() > 0) {
            val first = value.opt(0)
            return parseSenderValue(first)
        }
        return value.toString()
    }

    private fun parseTimestamp(obj: JSONObject): Long {
        val timestampVal = obj.opt("timestamp") ?: obj.opt("date") ?: obj.opt("createdAt")
        if (timestampVal is Number) return timestampVal.toLong()
        if (timestampVal is String) {
            val num = timestampVal.toLongOrNull()
            if (num != null) return num

            val isoFormats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "EEE, dd MMM yyyy HH:mm:ss Z"
            )
            for (format in isoFormats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val date = sdf.parse(timestampVal)
                    if (date != null) return date.time
                } catch (e: Exception) {
                    // Try next
                }
            }
        }
        return System.currentTimeMillis()
    }
}
