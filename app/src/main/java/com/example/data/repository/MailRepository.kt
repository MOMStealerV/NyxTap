package com.example.data.repository

import android.content.Context
import com.example.data.config.NowCareConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EmailEntity
import com.example.data.model.DetectedOtp
import com.example.data.model.EmailMessage
import com.example.data.model.ExtractionDiagnostic
import com.example.data.model.MailSession
import com.example.data.model.MailboxStatus
import com.example.data.model.PollDiagnosticInfo
import com.example.data.model.ProviderDiagnostics
import com.example.data.provider.AhemMailProvider
import com.example.data.provider.EmailFetchResult
import com.example.data.provider.MailProvider
import com.example.util.AppClipboardManager
import com.example.util.OtpCandidate
import com.example.util.OtpExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MailRepository(
    private val context: Context,
    private var provider: MailProvider = AhemMailProvider(),
    private val clipboardManager: AppClipboardManager = AppClipboardManager(context)
) {
    private val database = AppDatabase.getDatabase(context)
    private val emailDao = database.emailDao()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null

    // Single Authoritative Source of Truth: MailSession
    private val _session = MutableStateFlow(MailSession())
    val session: StateFlow<MailSession> = _session.asStateFlow()

    // Backward-compatible individual StateFlows synced directly with _session
    private val _mailboxStatus = MutableStateFlow<MailboxStatus>(MailboxStatus.Idle)
    val mailboxStatus: StateFlow<MailboxStatus> = _mailboxStatus.asStateFlow()

    private val _activeDetectedOtp = MutableStateFlow<DetectedOtp?>(null)
    val activeDetectedOtp: StateFlow<DetectedOtp?> = _activeDetectedOtp.asStateFlow()

    private val _latestExtractionDiagnostic = MutableStateFlow<ExtractionDiagnostic?>(null)
    val latestExtractionDiagnostic: StateFlow<ExtractionDiagnostic?> = _latestExtractionDiagnostic.asStateFlow()

    private val _latestOtpEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1) // (email, otp)
    val latestOtpEvent: SharedFlow<Pair<String, String>> = _latestOtpEvent.asSharedFlow()

    private val seenEmailIds = mutableSetOf<String>()

    val currentEmail: String? get() = _session.value.mailboxAddress.ifBlank { null }
    val currentMailboxName: String? get() = _session.value.mailboxLocalPart.ifBlank { null }
    val currentSessionId: String get() = _session.value.sessionId

    var pollingIntervalSeconds: Int = 5
    var autoCopyEmail: Boolean = true
    var autoCopyOtp: Boolean = true
    val defaultMonitoringTimeoutMinutes: Int = 10

    val allEmails: Flow<List<EmailMessage>> = emailDao.getAllEmails().map { entities ->
        entities.map { it.toDomain() }
    }

    val diagnostics: StateFlow<ProviderDiagnostics> = provider.diagnostics
    val pollDiagnostics: StateFlow<PollDiagnosticInfo> = provider.pollDiagnostics

    init {
        // Keep individual StateFlows in lockstep with the authoritative Session
        repositoryScope.launch {
            _session.collect { currentSession ->
                _mailboxStatus.value = currentSession.status
                _activeDetectedOtp.value = currentSession.detectedOtp
            }
        }
    }

    fun setProvider(newProvider: MailProvider) {
        provider = newProvider
    }

    suspend fun testProviderDiagnostics(): ProviderDiagnostics {
        return provider.testConnection()
    }

    suspend fun probeMailboxDirect(email: String? = currentEmail): String {
        val targetEmail = email ?: currentEmail ?: return "No active mailbox to probe."
        val mailboxOnly = targetEmail.substringBefore("@")
        return provider.probeMailboxDirect(mailboxOnly)
    }

    /**
     * Generates a new disposable mailbox using the NowCare AHEM server.
     * 1. Generates a new unique Session ID.
     * 2. Clears ALL previous mailbox state, detected OTPs, cached messages, and seen IDs.
     * 3. Sets state to GENERATING -> MAILBOX_CREATED.
     * 4. Automatically copies address to clipboard.
     * 5. Initiates a clean monitoring window.
     */
    suspend fun generateNewMailbox(): String {
        monitoringJob?.cancel()

        val newSessionId = UUID.randomUUID().toString()
        seenEmailIds.clear()

        // Reset session immediately to clean Generating state
        _session.value = MailSession(
            sessionId = newSessionId,
            status = MailboxStatus.Generating,
            startedAt = System.currentTimeMillis()
        )
        _activeDetectedOtp.value = null
        _latestExtractionDiagnostic.value = null

        return try {
            val email = provider.generateMailbox()
            val localPart = email.substringBefore("@")
            val domain = email.substringAfter("@", NowCareConfig.DEFAULT_DOMAIN)

            _session.value = _session.value.copy(
                mailboxAddress = email,
                mailboxLocalPart = localPart,
                domain = domain,
                status = MailboxStatus.MailboxCreated(email, newSessionId)
            )

            if (autoCopyEmail) {
                clipboardManager.copyEmail(email, callerContext = "MailRepository")
            }

            // Start active monitoring for 10 minutes with pristine state
            startMonitoring(
                email = email,
                timeoutMinutes = defaultMonitoringTimeoutMinutes,
                isFreshSession = true,
                sessionId = newSessionId
            )
            email
        } catch (e: Exception) {
            val errorStatus = MailboxStatus.Error("Unable to create mailbox: ${e.localizedMessage ?: "Unknown error"}")
            _session.value = _session.value.copy(status = errorStatus)
            ""
        }
    }

    /**
     * Starts an active monitoring session with a finite timeout.
     * Extracts OTP exclusively from newly received messages for the active session.
     */
    fun startMonitoring(
        email: String? = currentEmail,
        timeoutMinutes: Int = defaultMonitoringTimeoutMinutes,
        isFreshSession: Boolean = false,
        sessionId: String = _session.value.sessionId.ifBlank { UUID.randomUUID().toString() }
    ) {
        val targetEmail = email ?: currentEmail ?: return
        val localPart = targetEmail.substringBefore("@")
        val domain = targetEmail.substringAfter("@", NowCareConfig.DEFAULT_DOMAIN)

        if (isFreshSession || _session.value.sessionId != sessionId) {
            _activeDetectedOtp.value = null
            _latestExtractionDiagnostic.value = null
            seenEmailIds.clear()
            _session.value = MailSession(
                sessionId = sessionId,
                mailboxAddress = targetEmail,
                mailboxLocalPart = localPart,
                domain = domain,
                startedAt = System.currentTimeMillis(),
                status = MailboxStatus.Monitoring(
                    email = targetEmail,
                    sessionId = sessionId,
                    elapsedSeconds = 0,
                    remainingSeconds = timeoutMinutes * 60,
                    attempt = 1,
                    lastChecked = System.currentTimeMillis()
                )
            )
        }

        monitoringJob?.cancel()
        monitoringJob = repositoryScope.launch {
            val totalSeconds = timeoutMinutes * 60
            var elapsedSeconds = 0
            var attemptCount = 0
            var consecutiveNetworkErrors = 0

            _session.value = _session.value.copy(
                status = MailboxStatus.Monitoring(
                    email = targetEmail,
                    sessionId = sessionId,
                    elapsedSeconds = 0,
                    remainingSeconds = totalSeconds,
                    attempt = 1,
                    lastChecked = System.currentTimeMillis()
                )
            )

            while (isActive && elapsedSeconds < totalSeconds) {
                attemptCount++
                val mailboxOnly = _session.value.mailboxLocalPart.ifBlank { targetEmail.substringBefore("@") }

                var transientErrMsg: String? = null

                try {
                    when (val fetchResult = provider.checkEmailsDetailed(mailboxOnly)) {
                        is EmailFetchResult.Success -> {
                            consecutiveNetworkErrors = 0
                            val messages = fetchResult.emails
                            if (messages.isNotEmpty()) {
                                emailDao.insertEmails(messages.map { it.toEntity() })

                                // Process only unseen messages belonging to this session
                                val unseenMessages = messages.filter { it.id !in seenEmailIds }
                                    .sortedByDescending { it.timestamp }

                                if (unseenMessages.isNotEmpty()) {
                                    var detectedOtpCandidate: Pair<EmailMessage, OtpCandidate>? = null

                                    for (msg in unseenMessages) {
                                        val candidates = OtpExtractor.extractAllCandidates(
                                            bodyText = msg.bodyText,
                                            bodyHtml = msg.bodyHtml,
                                            subject = msg.subject
                                        )
                                        val bestCandidate = candidates.firstOrNull()

                                        _latestExtractionDiagnostic.value = ExtractionDiagnostic(
                                            messageId = msg.id,
                                            subject = msg.subject,
                                            textLength = msg.bodyText.length,
                                            htmlLength = msg.bodyHtml?.length ?: 0,
                                            extractedOtp = bestCandidate?.code,
                                            score = bestCandidate?.score ?: 0,
                                            matchedPattern = bestCandidate?.matchedKeyword.orEmpty(),
                                            timestamp = System.currentTimeMillis()
                                        )

                                        if (bestCandidate != null && bestCandidate.code.isNotBlank()) {
                                            detectedOtpCandidate = Pair(msg, bestCandidate)
                                            break
                                        }
                                    }

                                    // Mark messages as seen for this session
                                    messages.forEach { seenEmailIds.add(it.id) }
                                    _session.value = _session.value.copy(
                                        seenMessageIds = seenEmailIds.toSet(),
                                        latestMessageId = unseenMessages.firstOrNull()?.id
                                    )

                                    if (detectedOtpCandidate != null) {
                                        val (msg, candidate) = detectedOtpCandidate
                                        val otp = candidate.code

                                        val detected = DetectedOtp(
                                            sessionId = sessionId,
                                            messageId = msg.id,
                                            mailbox = targetEmail,
                                            code = otp,
                                            sender = msg.sender,
                                            subject = msg.subject,
                                            detectedAt = System.currentTimeMillis()
                                        )
                                        _activeDetectedOtp.value = detected
                                        _session.value = _session.value.copy(
                                            detectedOtp = detected,
                                            status = MailboxStatus.CodeDetected(
                                                email = targetEmail,
                                                sessionId = sessionId,
                                                code = otp,
                                                sender = msg.sender,
                                                subject = msg.subject,
                                                messageId = msg.id
                                            )
                                        )

                                        if (autoCopyOtp) {
                                            clipboardManager.copyOtp(otp, callerContext = "MailRepository")
                                        }
                                        _latestOtpEvent.tryEmit(Pair(targetEmail, otp))
                                        return@launch // Stop polling immediately on code detection
                                    } else {
                                        // Email received without OTP code
                                        val primaryMsg = unseenMessages.first()
                                        _session.value = _session.value.copy(
                                            status = MailboxStatus.EmailReceivedNoCode(
                                                email = targetEmail,
                                                sessionId = sessionId,
                                                message = primaryMsg
                                            )
                                        )
                                        return@launch // Stop polling immediately
                                    }
                                }
                            }
                        }
                        is EmailFetchResult.NetworkError -> {
                            consecutiveNetworkErrors++
                            transientErrMsg = "Connection issue: ${fetchResult.message}. Retrying..."
                        }
                        is EmailFetchResult.ServerError -> {
                            if (fetchResult.code == 401 || fetchResult.code == 403) {
                                _session.value = _session.value.copy(
                                    status = MailboxStatus.AuthError(
                                        message = "Authentication required/rejected (HTTP ${fetchResult.code})",
                                        httpCode = fetchResult.code,
                                        email = targetEmail
                                    )
                                )
                                return@launch // Stop polling immediately on auth failure
                            } else if (fetchResult.code >= 500) {
                                transientErrMsg = "Server temporary error (${fetchResult.code}). Retrying..."
                            }
                        }
                    }
                } catch (e: Exception) {
                    consecutiveNetworkErrors++
                    transientErrMsg = "Connection lost: ${e.localizedMessage}. Retrying..."
                }

                // If currently in Monitoring state, update countdown
                val currentStatus = _session.value.status
                if (currentStatus is MailboxStatus.Monitoring || currentStatus is MailboxStatus.Active || currentStatus is MailboxStatus.MailboxCreated) {
                    _session.value = _session.value.copy(
                        status = MailboxStatus.Monitoring(
                            email = targetEmail,
                            sessionId = sessionId,
                            elapsedSeconds = elapsedSeconds,
                            remainingSeconds = (totalSeconds - elapsedSeconds).coerceAtLeast(0),
                            attempt = attemptCount,
                            lastChecked = System.currentTimeMillis(),
                            transientError = transientErrMsg
                        )
                    )
                }

                val sleepDuration = if (consecutiveNetworkErrors > 2) 8000L else (pollingIntervalSeconds * 1000L)
                delay(sleepDuration)
                elapsedSeconds += (sleepDuration / 1000).toInt()
            }

            // If timeout reached without code detection:
            if (isActive) {
                _session.value = _session.value.copy(
                    status = MailboxStatus.Timeout(
                        email = targetEmail,
                        sessionId = sessionId,
                        durationMinutes = timeoutMinutes
                    )
                )
            }
        }
    }

    /**
     * Extends waiting time by another duration when user taps "Continue Waiting".
     */
    fun continueWaiting(additionalMinutes: Int = 10) {
        val email = currentEmail ?: return
        startMonitoring(email = email, timeoutMinutes = additionalMinutes, isFreshSession = false)
    }

    /**
     * Fast on-demand check for mail or verification OTP.
     */
    suspend fun checkForOtpNow(maxAttempts: Int = 6): Pair<String?, Int> = withContext(Dispatchers.IO) {
        val email = currentEmail ?: return@withContext Pair(null, 0)
        val mailboxOnly = currentMailboxName ?: email.substringBefore("@")
        val sessionId = _session.value.sessionId

        var foundOtp: String? = null
        var totalMessages = 0

        for (attempt in 1..maxAttempts) {
            _session.value = _session.value.copy(
                status = MailboxStatus.Checking(email, sessionId, attempt)
            )

            try {
                when (val fetchResult = provider.checkEmailsDetailed(mailboxOnly)) {
                    is EmailFetchResult.Success -> {
                        val messages = fetchResult.emails
                        if (messages.isNotEmpty()) {
                            totalMessages = messages.size
                            emailDao.insertEmails(messages.map { it.toEntity() })

                            val unseenMessages = messages.filter { it.id !in seenEmailIds }
                                .sortedByDescending { it.timestamp }

                            val targetList = if (unseenMessages.isNotEmpty()) unseenMessages else messages.sortedByDescending { it.timestamp }

                            for (msg in targetList) {
                                val candidates = OtpExtractor.extractAllCandidates(
                                    bodyText = msg.bodyText,
                                    bodyHtml = msg.bodyHtml,
                                    subject = msg.subject
                                )
                                val bestCandidate = candidates.firstOrNull()

                                _latestExtractionDiagnostic.value = ExtractionDiagnostic(
                                    messageId = msg.id,
                                    subject = msg.subject,
                                    textLength = msg.bodyText.length,
                                    htmlLength = msg.bodyHtml?.length ?: 0,
                                    extractedOtp = bestCandidate?.code,
                                    score = bestCandidate?.score ?: 0,
                                    matchedPattern = bestCandidate?.matchedKeyword.orEmpty(),
                                    timestamp = System.currentTimeMillis()
                                )

                                if (bestCandidate != null && bestCandidate.code.isNotBlank()) {
                                    val otp = bestCandidate.code
                                    foundOtp = otp

                                    val detected = DetectedOtp(
                                        sessionId = sessionId,
                                        messageId = msg.id,
                                        mailbox = email,
                                        code = otp,
                                        sender = msg.sender,
                                        subject = msg.subject,
                                        detectedAt = System.currentTimeMillis()
                                    )
                                    _activeDetectedOtp.value = detected
                                    _session.value = _session.value.copy(
                                        detectedOtp = detected,
                                        seenMessageIds = seenEmailIds.toSet(),
                                        status = MailboxStatus.CodeDetected(
                                            email = email,
                                            sessionId = sessionId,
                                            code = otp,
                                            sender = msg.sender,
                                            subject = msg.subject,
                                            messageId = msg.id
                                        )
                                    )

                                    messages.forEach { seenEmailIds.add(it.id) }
                                    if (autoCopyOtp) {
                                        clipboardManager.copyOtp(otp, callerContext = "MailRepository")
                                    }
                                    _latestOtpEvent.tryEmit(Pair(email, otp))
                                    monitoringJob?.cancel()
                                    return@withContext Pair(foundOtp, totalMessages)
                                }
                            }

                            // Email received, but no OTP code detected
                            val primaryMsg = targetList.first()
                            messages.forEach { seenEmailIds.add(it.id) }
                            _session.value = _session.value.copy(
                                status = MailboxStatus.EmailReceivedNoCode(
                                    email = email,
                                    sessionId = sessionId,
                                    message = primaryMsg
                                )
                            )
                            monitoringJob?.cancel()
                            return@withContext Pair(null, totalMessages)
                        }
                    }
                    is EmailFetchResult.ServerError -> {
                        if (fetchResult.code == 401 || fetchResult.code == 403) {
                            _session.value = _session.value.copy(
                                status = MailboxStatus.AuthError(
                                    message = "Authentication required/rejected (HTTP ${fetchResult.code})",
                                    httpCode = fetchResult.code,
                                    email = email
                                )
                            )
                            monitoringJob?.cancel()
                            return@withContext Pair(null, 0)
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                // Continue retry
            }

            if (attempt < maxAttempts) {
                delay(2000L)
            }
        }

        // Return to active ready
        _session.value = _session.value.copy(
            status = MailboxStatus.Active(email, sessionId)
        )
        Pair(foundOtp, totalMessages)
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _session.value = _session.value.copy(
            status = MailboxStatus.Stopped(email = currentEmail, sessionId = _session.value.sessionId)
        )
    }

    suspend fun clearHistory() {
        emailDao.clearAll()
        seenEmailIds.clear()
        _activeDetectedOtp.value = null
        _latestExtractionDiagnostic.value = null
    }

    private fun EmailMessage.toEntity(): EmailEntity = EmailEntity(
        id = id,
        mailbox = mailbox,
        sender = sender,
        subject = subject,
        snippet = snippet,
        bodyText = bodyText,
        bodyHtml = bodyHtml,
        timestamp = timestamp,
        extractedOtp = extractedOtp,
        isCopied = isCopied
    )

    private fun EmailEntity.toDomain(): EmailMessage = EmailMessage(
        id = id,
        mailbox = mailbox,
        sender = sender,
        subject = subject,
        snippet = snippet,
        bodyText = bodyText,
        bodyHtml = bodyHtml,
        timestamp = timestamp,
        extractedOtp = extractedOtp,
        isCopied = isCopied
    )
}
