package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level Clipboard Manager implementing ClipboardManagerClient.
 * Applies the LoggingClipboardDecorator to log all secret reads/writes,
 * verify permission consistency (overlay and background restrictions),
 * and track app lifecycle states.
 */
class AppClipboardManager(
    private val context: Context,
    lifecycleTracker: AppLifecycleTracker = AppLifecycleTracker
) : ClipboardManagerClient {

    private val decorator: LoggingClipboardDecorator = LoggingClipboardDecorator(
        delegate = SystemClipboardManagerImpl(context),
        context = context,
        lifecycleTracker = lifecycleTracker
    )

    val auditLogsFlow: StateFlow<List<ClipboardAuditLog>> get() = decorator.auditLogsFlow

    /**
     * Copies temporary email to clipboard.
     */
    override fun copyEmail(email: String, callerContext: String): Boolean {
        return decorator.copyEmail(email, callerContext)
    }

    fun copyEmail(email: String): Boolean = copyEmail(email, "App")

    /**
     * Copies OTP verification code to clipboard with sensitive marking.
     */
    override fun copyOtp(otp: String, callerContext: String): Boolean {
        return decorator.copyOtp(otp, callerContext)
    }

    fun copyOtp(otp: String): Boolean = copyOtp(otp, "App")

    /**
     * Copies TOTP 6-digit code to clipboard with sensitive marking.
     */
    override fun copyTotp(code: String, callerContext: String): Boolean {
        return decorator.copyTotp(code, callerContext)
    }

    fun copyTotp(code: String): Boolean = copyTotp(code, "App")

    /**
     * Copies plain text (e.g. Generated Name) to clipboard.
     */
    override fun copyPlainText(label: String, text: String, callerContext: String): Boolean {
        return decorator.copyPlainText(label, text, callerContext)
    }

    fun copyPlainText(label: String, text: String): Boolean = copyPlainText(label, text, "App")

    /**
     * Reads clipboard text when user explicitly taps 2FA.
     * Sanitizes and validates Base32 or otpauth:// format.
     */
    override fun readTotpSecret(callerContext: String): String? {
        return decorator.readTotpSecret(callerContext)
    }

    fun readTotpSecret(): String? = readTotpSecret("App")

    override fun readPrimaryClipText(callerContext: String): String? {
        return decorator.readPrimaryClipText(callerContext)
    }

    fun readPrimaryClipText(): String? = readPrimaryClipText("App")

    override fun hasPrimaryClip(): Boolean {
        return decorator.hasPrimaryClip()
    }

    override fun getAuditLogs(): List<ClipboardAuditLog> {
        return decorator.getAuditLogs()
    }

    override fun clearAuditLogs() {
        decorator.clearAuditLogs()
    }
}

