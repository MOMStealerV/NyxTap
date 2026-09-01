package com.example.data.repository

import android.content.Context
import com.example.data.model.TotpResult
import com.example.util.AppClipboardManager
import com.example.util.TotpGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.util.TotpParseResult

class TwoFaRepository(
    private val context: Context,
    private val clipboardManager: AppClipboardManager = AppClipboardManager(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _currentTotp = MutableStateFlow<TotpResult?>(null)
    val currentTotp: StateFlow<TotpResult?> = _currentTotp.asStateFlow()

    private val _lastCopiedFeedback = MutableStateFlow<String?>(null)
    val lastCopiedFeedback: StateFlow<String?> = _lastCopiedFeedback.asStateFlow()

    var digits: Int = 6
    var periodSeconds: Int = 30
    var autoCopyCode: Boolean = true

    private var activeSecret: String? = null

    /**
     * Reads clipboard secret directly upon explicit user action, computes current TOTP,
     * auto-copies code to clipboard, and starts ticker updates.
     */
    fun generateFromClipboard(callerContext: String = "App"): Result<TotpResult> {
        val rawText = clipboardManager.readPrimaryClipText(callerContext = callerContext)
        val parseResult = TotpGenerator.parseAndValidateSecret(rawText)
        return when (parseResult) {
            is TotpParseResult.Success -> {
                generateFromCleanSecret(parseResult.secret, callerContext = callerContext)
            }
            is TotpParseResult.Failure -> {
                Result.failure(IllegalArgumentException(parseResult.errorMessage))
            }
        }
    }

    /**
     * Generates TOTP from explicit secret string.
     */
    fun generateFromSecret(secret: String, callerContext: String = "App"): Result<TotpResult> {
        val parseResult = TotpGenerator.parseAndValidateSecret(secret)
        return when (parseResult) {
            is TotpParseResult.Success -> {
                generateFromCleanSecret(parseResult.secret, callerContext = callerContext)
            }
            is TotpParseResult.Failure -> {
                Result.failure(IllegalArgumentException(parseResult.errorMessage))
            }
        }
    }

    private fun generateFromCleanSecret(cleanSecret: String, callerContext: String = "App"): Result<TotpResult> {
        return try {
            activeSecret = cleanSecret
            val result = calculateCurrentTotp(cleanSecret)
            _currentTotp.value = result

            if (autoCopyCode) {
                clipboardManager.copyTotp(result.code, callerContext = callerContext)
                _lastCopiedFeedback.value = "✓ Code ${result.code} copied"
            }

            startTicker()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid TOTP secret"))
        }
    }

    private fun calculateCurrentTotp(secret: String): TotpResult {
        val code = TotpGenerator.generateTotp(
            secretBase32 = secret,
            timeStepSeconds = periodSeconds,
            digits = digits
        )
        val remaining = TotpGenerator.getRemainingSeconds(timeStepSeconds = periodSeconds)
        val progress = TotpGenerator.getProgressFraction(timeStepSeconds = periodSeconds)

        return TotpResult(
            code = code,
            remainingSeconds = remaining,
            progress = progress,
            period = periodSeconds,
            isCopied = true
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && activeSecret != null) {
                val secret = activeSecret ?: break
                val updated = calculateCurrentTotp(secret)
                _currentTotp.value = updated
                delay(500)
            }
        }
    }

    fun clear() {
        tickerJob?.cancel()
        tickerJob = null
        activeSecret = null
        _currentTotp.value = null
        _lastCopiedFeedback.value = null
    }
}
