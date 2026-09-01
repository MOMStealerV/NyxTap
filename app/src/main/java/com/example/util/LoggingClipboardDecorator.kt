package com.example.util

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.LinkedList

/**
 * Decorator around ClipboardManager calls that intercepts read/write operations,
 * logs comprehensive audit diagnostics, verifies permission consistency (e.g. overlay permissions),
 * monitors application lifecycle states, and safely sanitizes secret data in logs.
 */
class LoggingClipboardDecorator(
    private val delegate: ClipboardManagerClient,
    private val context: Context,
    private val lifecycleTracker: AppLifecycleTracker = AppLifecycleTracker
) : ClipboardManagerClient {

    companion object {
        const val TAG = "NyxTapClipboard"
        private const val MAX_LOG_ENTRIES = 100
    }

    private val logBuffer = Collections.synchronizedList(LinkedList<ClipboardAuditLog>())
    private val _auditLogsFlow = MutableStateFlow<List<ClipboardAuditLog>>(emptyList())
    val auditLogsFlow: StateFlow<List<ClipboardAuditLog>> = _auditLogsFlow.asStateFlow()

    private val systemClipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override fun copyEmail(email: String, callerContext: String): Boolean {
        val env = captureEnvironment(callerContext)
        val success = delegate.copyEmail(email, callerContext)
        val maskedEmail = maskEmail(email)

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.COPY_EMAIL,
            callerContext = callerContext,
            success = success,
            itemCount = if (success) 1 else 0,
            mimeType = ClipDescription.MIMETYPE_TEXT_PLAIN,
            sanitizedSummary = "Copied email: $maskedEmail (length=${email.length})",
            isSensitiveFlagApplied = false,
            environment = env
        )
        recordAndLog(log)
        return success
    }

    override fun copyOtp(otp: String, callerContext: String): Boolean {
        val env = captureEnvironment(callerContext)
        val success = delegate.copyOtp(otp, callerContext)
        val sensitiveApplied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.COPY_SECRET_OTP,
            callerContext = callerContext,
            success = success,
            itemCount = if (success) 1 else 0,
            mimeType = ClipDescription.MIMETYPE_TEXT_PLAIN,
            sanitizedSummary = "Copied verification OTP (len=${otp.length}, sensitiveFlag=$sensitiveApplied)",
            isSensitiveFlagApplied = sensitiveApplied,
            environment = env
        )
        recordAndLog(log)
        return success
    }

    override fun copyTotp(code: String, callerContext: String): Boolean {
        val env = captureEnvironment(callerContext)
        val success = delegate.copyTotp(code, callerContext)
        val sensitiveApplied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.COPY_SECRET_TOTP,
            callerContext = callerContext,
            success = success,
            itemCount = if (success) 1 else 0,
            mimeType = ClipDescription.MIMETYPE_TEXT_PLAIN,
            sanitizedSummary = "Copied TOTP code (len=${code.length}, sensitiveFlag=$sensitiveApplied)",
            isSensitiveFlagApplied = sensitiveApplied,
            environment = env
        )
        recordAndLog(log)
        return success
    }

    override fun copyPlainText(label: String, text: String, callerContext: String): Boolean {
        val env = captureEnvironment(callerContext)
        val success = delegate.copyPlainText(label, text, callerContext)

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.COPY_PLAIN_TEXT,
            callerContext = callerContext,
            success = success,
            itemCount = if (success) 1 else 0,
            mimeType = ClipDescription.MIMETYPE_TEXT_PLAIN,
            sanitizedSummary = "Copied text label='$label' (len=${text.length})",
            isSensitiveFlagApplied = false,
            environment = env
        )
        recordAndLog(log)
        return success
    }

    override fun readPrimaryClipText(callerContext: String): String? {
        val env = captureEnvironment(callerContext)
        val rawText = delegate.readPrimaryClipText(callerContext)
        val hasClip = systemClipboard?.hasPrimaryClip() == true
        val itemCount = systemClipboard?.primaryClip?.itemCount ?: 0
        val mimeType = systemClipboard?.primaryClipDescription?.getMimeType(0)

        val summary = sanitizeClipboardRead(rawText)
        val warning = assessReadWarnings(rawText, env, hasClip)

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.READ_PRIMARY_CLIP,
            callerContext = callerContext,
            success = rawText != null,
            itemCount = itemCount,
            mimeType = mimeType,
            sanitizedSummary = summary,
            isSensitiveFlagApplied = false,
            environment = env,
            diagnosticWarning = warning
        )
        recordAndLog(log)
        return rawText
    }

    override fun readTotpSecret(callerContext: String): String? {
        val env = captureEnvironment(callerContext)
        val secret = delegate.readTotpSecret(callerContext)
        val hasClip = systemClipboard?.hasPrimaryClip() == true
        val itemCount = systemClipboard?.primaryClip?.itemCount ?: 0
        val mimeType = systemClipboard?.primaryClipDescription?.getMimeType(0)

        val summary = if (secret != null) {
            "Valid 2FA TOTP secret detected (Base32, len=${secret.length})"
        } else {
            "No valid TOTP secret parsed from clipboard"
        }
        val warning = assessReadWarnings(secret, env, hasClip)

        val log = ClipboardAuditLog(
            operation = ClipboardOperation.READ_SECRET_TOTP,
            callerContext = callerContext,
            success = secret != null,
            itemCount = itemCount,
            mimeType = mimeType,
            sanitizedSummary = summary,
            isSensitiveFlagApplied = false,
            environment = env,
            diagnosticWarning = warning
        )
        recordAndLog(log)
        return secret
    }

    override fun hasPrimaryClip(): Boolean {
        return delegate.hasPrimaryClip()
    }

    override fun getAuditLogs(): List<ClipboardAuditLog> {
        synchronized(logBuffer) {
            return logBuffer.toList()
        }
    }

    override fun clearAuditLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _auditLogsFlow.value = emptyList()
        }
    }

    private fun captureEnvironment(callerContext: String): ClipboardEnvironmentSnapshot {
        return lifecycleTracker.captureEnvironment(context, callerContext)
    }

    private fun assessReadWarnings(
        resultText: String?,
        env: ClipboardEnvironmentSnapshot,
        hasClip: Boolean
    ): String? {
        val warnings = mutableListOf<String>()

        // 1. Overlay caller permission consistency
        val isOverlayCaller = env.callerContext.contains("Overlay", ignoreCase = true)
        if (isOverlayCaller && !env.hasOverlayPermission) {
            warnings.add("Caller '${env.callerContext}' accessed clipboard while SYSTEM_ALERT_WINDOW permission is NOT granted.")
        }

        // 2. Android 10+ (API 29+) Background clipboard restriction check
        if (resultText == null && env.isBackgroundReadRestricted) {
            warnings.add("Clipboard read returned null. App is in background (${env.lifecycleState}) on Android ${env.sdkInt}. Android Q+ restricts clipboard reading without active window focus.")
        } else if (resultText == null && !hasClip) {
            warnings.add("Clipboard is empty or contains no primary clip.")
        }

        return if (warnings.isNotEmpty()) warnings.joinToString("; ") else null
    }

    private fun sanitizeClipboardRead(text: String?): String {
        if (text == null) return "Clipboard read returned null / empty"
        if (text.isBlank()) return "Clipboard read returned blank whitespace"

        val trimmed = text.trim()
        return when {
            trimmed.startsWith("otpauth://", ignoreCase = true) -> {
                val uriType = if (trimmed.contains("totp", ignoreCase = true)) "TOTP" else "HOTP"
                "URI format: otpauth://$uriType (length=${trimmed.length})"
            }
            trimmed.length in 4..10 && trimmed.all { it.isDigit() } -> {
                "Plain OTP digits (len=${trimmed.length})"
            }
            trimmed.contains("@") && trimmed.contains(".") -> {
                "Email address detected: ${maskEmail(trimmed)}"
            }
            trimmed.length in 16..64 && trimmed.all { it.isLetterOrDigit() || it == '=' } -> {
                "Potential Base32 secret key (len=${trimmed.length})"
            }
            else -> {
                "General text content (length=${trimmed.length})"
            }
        }
    }

    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 1) return "***@***"
        val namePart = email.substring(0, atIndex)
        val domainPart = email.substring(atIndex + 1)
        val maskedName = if (namePart.length <= 2) namePart.take(1) + "***" else namePart.take(2) + "***"
        return "$maskedName@$domainPart"
    }

    private fun recordAndLog(log: ClipboardAuditLog) {
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOG_ENTRIES) {
                logBuffer.removeAt(0)
            }
            logBuffer.add(log)
            _auditLogsFlow.value = logBuffer.toList()
        }

        val logMessage = buildString {
            append("[${log.operation}] caller='${log.callerContext}' ")
            append("success=${log.success} ")
            append("lifecycle=${log.environment.lifecycleState} ")
            append("overlayPerm=${log.environment.hasOverlayPermission} ")
            append("foreground=${log.environment.isAppInForeground} ")
            append("thread='${log.environment.threadName}' | ")
            append(log.sanitizedSummary)
            if (log.diagnosticWarning != null) {
                append(" | WARNING: ${log.diagnosticWarning}")
            }
        }

        if (log.diagnosticWarning != null) {
            Log.w(TAG, logMessage)
        } else {
            Log.i(TAG, logMessage)
        }
    }
}
