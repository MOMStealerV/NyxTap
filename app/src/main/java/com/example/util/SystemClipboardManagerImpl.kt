package com.example.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Underlying system-level clipboard implementation executing direct calls
 * to Android's ClipboardManager service.
 */
class SystemClipboardManagerImpl(
    private val context: Context
) : ClipboardManagerClient {

    private val clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override fun copyEmail(email: String, callerContext: String): Boolean {
        if (email.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("Temporary Email", email)
        clipboard.setPrimaryClip(clip)
        return true
    }

    override fun copyOtp(otp: String, callerContext: String): Boolean {
        if (otp.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("Verification Code", otp)
        applySensitiveFlag(clip)
        clipboard.setPrimaryClip(clip)
        return true
    }

    override fun copyTotp(code: String, callerContext: String): Boolean {
        if (code.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("2FA Code", code)
        applySensitiveFlag(clip)
        clipboard.setPrimaryClip(clip)
        return true
    }

    override fun copyPlainText(label: String, text: String, callerContext: String): Boolean {
        if (text.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        return true
    }

    override fun readTotpSecret(callerContext: String): String? {
        val text = readPrimaryClipText(callerContext) ?: return null
        return when (val result = TotpGenerator.parseAndValidateSecret(text)) {
            is TotpParseResult.Success -> result.secret
            is TotpParseResult.Failure -> null
        }
    }

    override fun readPrimaryClipText(callerContext: String): String? {
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.text?.toString()
    }

    override fun hasPrimaryClip(): Boolean {
        return clipboard?.hasPrimaryClip() == true
    }

    override fun getAuditLogs(): List<ClipboardAuditLog> = emptyList()

    override fun clearAuditLogs() {}

    private fun applySensitiveFlag(clip: ClipData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
    }
}
