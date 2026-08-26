package com.example.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

class AppClipboardManager(private val context: Context) {

    private val clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /**
     * Copies temporary email to clipboard.
     */
    fun copyEmail(email: String): Boolean {
        if (email.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("Temporary Email", email)
        clipboard.setPrimaryClip(clip)
        return true
    }

    /**
     * Copies OTP verification code to clipboard with sensitive marking.
     */
    fun copyOtp(otp: String): Boolean {
        if (otp.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("Verification Code", otp)
        applySensitiveFlag(clip)
        clipboard.setPrimaryClip(clip)
        return true
    }

    /**
     * Copies TOTP 6-digit code to clipboard with sensitive marking.
     */
    fun copyTotp(code: String): Boolean {
        if (code.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText("2FA Code", code)
        applySensitiveFlag(clip)
        clipboard.setPrimaryClip(clip)
        return true
    }

    /**
     * Copies plain text (e.g. Generated Name) to clipboard.
     */
    fun copyPlainText(label: String, text: String): Boolean {
        if (text.isBlank() || clipboard == null) return false
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        return true
    }

    /**
     * Reads clipboard text when user explicitly taps 2FA.
     * Sanitizes and validates Base32 or otpauth:// format.
     */
    fun readTotpSecret(): String? {
        val text = readPrimaryClipText() ?: return null
        return when (val result = TotpGenerator.parseAndValidateSecret(text)) {
            is TotpParseResult.Success -> result.secret
            is TotpParseResult.Failure -> null
        }
    }

    fun readPrimaryClipText(): String? {
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.text?.toString()
    }

    private fun applySensitiveFlag(clip: ClipData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
    }
}
