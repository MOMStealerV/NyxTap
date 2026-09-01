package com.example.util

import android.os.Build
import java.util.UUID

/**
 * Operations supported on the clipboard manager.
 */
enum class ClipboardOperation {
    READ_PRIMARY_CLIP,
    READ_SECRET_TOTP,
    COPY_SECRET_OTP,
    COPY_SECRET_TOTP,
    COPY_EMAIL,
    COPY_PLAIN_TEXT
}

/**
 * Coarse-grained application/service lifecycle state at time of clipboard interaction.
 */
enum class ClipboardLifecycleState {
    FOREGROUND_ACTIVITY,
    STARTED_ACTIVITY,
    CREATED_ACTIVITY,
    FOREGROUND_SERVICE,
    BACKGROUND_PROCESS,
    UNKNOWN
}

/**
 * Snapshot of the runtime environment, permissions, and lifecycle state when
 * a clipboard read or write operation is executed.
 */
data class ClipboardEnvironmentSnapshot(
    val callerContext: String,
    val hasOverlayPermission: Boolean,
    val lifecycleState: ClipboardLifecycleState,
    val lifecycleStateDetail: String,
    val isAppInForeground: Boolean,
    val isOverlayServiceRunning: Boolean,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val threadName: String = Thread.currentThread().name,
    val isBackgroundReadRestricted: Boolean = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isAppInForeground)
)

/**
 * An immutable audit entry recording a decorated clipboard operation,
 * permission check results, and lifecycle context without exposing raw secrets.
 */
data class ClipboardAuditLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val operation: ClipboardOperation,
    val callerContext: String,
    val success: Boolean,
    val itemCount: Int = 0,
    val mimeType: String? = null,
    val sanitizedSummary: String,
    val isSensitiveFlagApplied: Boolean = false,
    val environment: ClipboardEnvironmentSnapshot,
    val diagnosticWarning: String? = null
)

/**
 * Contract defining high-level clipboard actions with caller context tracking.
 */
interface ClipboardManagerClient {
    fun copyEmail(email: String, callerContext: String = "App"): Boolean
    fun copyOtp(otp: String, callerContext: String = "App"): Boolean
    fun copyTotp(code: String, callerContext: String = "App"): Boolean
    fun copyPlainText(label: String, text: String, callerContext: String = "App"): Boolean
    fun readTotpSecret(callerContext: String = "App"): String?
    fun readPrimaryClipText(callerContext: String = "App"): String?
    fun hasPrimaryClip(): Boolean
    fun getAuditLogs(): List<ClipboardAuditLog>
    fun clearAuditLogs()
}
