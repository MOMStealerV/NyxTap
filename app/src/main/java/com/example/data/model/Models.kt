package com.example.data.model

data class EmailMessage(
    val id: String,
    val mailbox: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val extractedOtp: String? = null,
    val isCopied: Boolean = false
)

data class DetectedOtp(
    val sessionId: String = "",
    val messageId: String,
    val mailbox: String,
    val code: String,
    val sender: String,
    val subject: String,
    val detectedAt: Long = System.currentTimeMillis()
)

data class ExtractionDiagnostic(
    val messageId: String = "",
    val subject: String = "",
    val textLength: Int = 0,
    val htmlLength: Int = 0,
    val extractedOtp: String? = null,
    val score: Int = 0,
    val matchedPattern: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class MailDeliveryStatus {
    NotVerified,
    VerifiedEmpty,
    VerifiedReceived,
    DeliveryFailed
}

data class PollDiagnosticInfo(
    val serverUrl: String = "https://nowcare.us/api/",
    val mailbox: String = "",
    val email: String = "",
    val pollingEndpoint: String = "",
    val lastPollTimestamp: Long = 0L,
    val httpStatus: Int? = null,
    val responseSummary: String = "No poll performed yet",
    val emailsReturnedCount: Int = 0,
    val returnedMessageIds: List<String> = emptyList(),
    val latestMessageSubject: String? = null,
    val latestMessageSender: String? = null,
    val lastError: String? = null,
    val deliveryStatus: MailDeliveryStatus = MailDeliveryStatus.NotVerified,
    val isDirectProbeRunning: Boolean = false,
    val directProbeResult: String? = null
)

/**
 * Single Authoritative Mail Session.
 * Every component (UI, ViewModel, Repository, Overlay, Service, Diagnostics) observes this state.
 */
data class MailSession(
    val sessionId: String = "",
    val mailboxAddress: String = "",
    val mailboxLocalPart: String = "",
    val domain: String = "nowcare.us",
    val startedAt: Long = System.currentTimeMillis(),
    val status: MailboxStatus = MailboxStatus.Idle,
    val seenMessageIds: Set<String> = emptySet(),
    val latestMessageId: String? = null,
    val detectedOtp: DetectedOtp? = null
)

/**
 * Explicit Mailbox lifecycle states:
 * IDLE -> GENERATING -> MAILBOX_CREATED -> MONITORING -> EMAIL_RECEIVED / CODE_DETECTED / EMAIL_RECEIVED_NO_CODE / TIMEOUT / AUTH_ERROR / ERROR / STOPPED
 */
sealed class MailboxStatus {
    object Idle : MailboxStatus()
    object Generating : MailboxStatus()

    data class MailboxCreated(
        val email: String,
        val sessionId: String = "",
        val timestamp: Long = System.currentTimeMillis()
    ) : MailboxStatus()

    data class Active(
        val email: String,
        val sessionId: String = "",
        val lastChecked: Long = System.currentTimeMillis()
    ) : MailboxStatus()

    data class Monitoring(
        val email: String,
        val sessionId: String = "",
        val elapsedSeconds: Int = 0,
        val remainingSeconds: Int = 600,
        val attempt: Int = 1,
        val lastChecked: Long = System.currentTimeMillis(),
        val transientError: String? = null
    ) : MailboxStatus()

    data class Checking(
        val email: String,
        val sessionId: String = "",
        val attempt: Int = 1
    ) : MailboxStatus()

    data class EmailReceived(
        val email: String,
        val sessionId: String = "",
        val messageCount: Int,
        val latestSubject: String? = null,
        val latestSender: String? = null,
        val latestMessageId: String? = null
    ) : MailboxStatus()

    data class EmailReceivedNoCode(
        val email: String,
        val sessionId: String = "",
        val message: EmailMessage
    ) : MailboxStatus()

    data class CodeDetected(
        val email: String,
        val sessionId: String = "",
        val code: String,
        val sender: String? = null,
        val subject: String? = null,
        val messageId: String? = null
    ) : MailboxStatus()

    data class Timeout(
        val email: String,
        val sessionId: String = "",
        val durationMinutes: Int = 10
    ) : MailboxStatus()

    data class AuthError(
        val message: String = "NowCare authentication required/rejected",
        val httpCode: Int = 401,
        val email: String? = null
    ) : MailboxStatus()

    data class NetworkError(
        val message: String,
        val email: String? = null
    ) : MailboxStatus()

    data class ServerError(
        val message: String,
        val httpCode: Int,
        val email: String? = null
    ) : MailboxStatus()

    data class Stopped(
        val email: String? = null,
        val sessionId: String = ""
    ) : MailboxStatus()

    data class Error(
        val message: String,
        val isServerError: Boolean = false,
        val httpCode: Int? = null
    ) : MailboxStatus()

    val currentEmailOrNull: String?
        get() = when (this) {
            is MailboxCreated -> email
            is Active -> email
            is Monitoring -> email
            is Checking -> email
            is EmailReceived -> email
            is EmailReceivedNoCode -> email
            is CodeDetected -> email
            is Timeout -> email
            is AuthError -> email
            is NetworkError -> email
            is ServerError -> email
            is Stopped -> email
            else -> null
        }

    val isActivelySearching: Boolean
        get() = this is Monitoring || this is Checking
}

sealed class ProviderConnectionStatus {
    object Idle : ProviderConnectionStatus()
    object Checking : ProviderConnectionStatus()
    data class Connected(val message: String = "Connected") : ProviderConnectionStatus()
    data class Error(val error: String, val httpCode: Int? = null) : ProviderConnectionStatus()
}

data class ProviderDiagnostics(
    val providerName: String = "AHEM / NowCare",
    val apiServerUrl: String = "https://nowcare.us",
    val allowedDomains: List<String> = listOf("nowcare.us"),
    val authHeaderRequired: Boolean = false,
    val status: ProviderConnectionStatus = ProviderConnectionStatus.Connected("Ready"),
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val maxMailboxLife: Long = 86400,
    val maxAge: Long = 86400,
    val rawPropertiesJson: String? = null
)

data class TotpResult(
    val code: String,
    val remainingSeconds: Int,
    val progress: Float, // 0.0 to 1.0
    val period: Int = 30,
    val label: String = "2FA Code",
    val isCopied: Boolean = true
)
