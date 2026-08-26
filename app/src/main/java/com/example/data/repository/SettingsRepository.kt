package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _overlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_ENABLED, true))
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _overlayPositionRight = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_POS_RIGHT, true))
    val overlayPositionRight: StateFlow<Boolean> = _overlayPositionRight.asStateFlow()

    private val _mailProviderName = MutableStateFlow(prefs.getString(KEY_MAIL_PROVIDER, "AHEM Mail") ?: "AHEM Mail")
    val mailProviderName: StateFlow<String> = _mailProviderName.asStateFlow()

    private val _pollingInterval = MutableStateFlow(prefs.getInt(KEY_POLLING_INTERVAL, 8))
    val pollingInterval: StateFlow<Int> = _pollingInterval.asStateFlow()

    private val _autoCopyEmail = MutableStateFlow(prefs.getBoolean(KEY_AUTO_COPY_EMAIL, true))
    val autoCopyEmail: StateFlow<Boolean> = _autoCopyEmail.asStateFlow()

    private val _autoCopyOtp = MutableStateFlow(prefs.getBoolean(KEY_AUTO_COPY_OTP, true))
    val autoCopyOtp: StateFlow<Boolean> = _autoCopyOtp.asStateFlow()

    private val _twoFaDigits = MutableStateFlow(prefs.getInt(KEY_2FA_DIGITS, 6))
    val twoFaDigits: StateFlow<Int> = _twoFaDigits.asStateFlow()

    private val _twoFaPeriod = MutableStateFlow(prefs.getInt(KEY_2FA_PERIOD, 30))
    val twoFaPeriod: StateFlow<Int> = _twoFaPeriod.asStateFlow()

    private val _autoCopyTwoFa = MutableStateFlow(prefs.getBoolean(KEY_AUTO_COPY_2FA, true))
    val autoCopyTwoFa: StateFlow<Boolean> = _autoCopyTwoFa.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _overlayEnabled.value = enabled
    }

    fun setOverlayPositionRight(isRight: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_POS_RIGHT, isRight).apply()
        _overlayPositionRight.value = isRight
    }

    fun setMailProviderName(name: String) {
        prefs.edit().putString(KEY_MAIL_PROVIDER, name).apply()
        _mailProviderName.value = name
    }

    fun setPollingInterval(seconds: Int) {
        prefs.edit().putInt(KEY_POLLING_INTERVAL, seconds).apply()
        _pollingInterval.value = seconds
    }

    fun setAutoCopyEmail(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COPY_EMAIL, enabled).apply()
        _autoCopyEmail.value = enabled
    }

    fun setAutoCopyOtp(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COPY_OTP, enabled).apply()
        _autoCopyOtp.value = enabled
    }

    fun setTwoFaDigits(digits: Int) {
        prefs.edit().putInt(KEY_2FA_DIGITS, digits).apply()
        _twoFaDigits.value = digits
    }

    fun setTwoFaPeriod(period: Int) {
        prefs.edit().putInt(KEY_2FA_PERIOD, period).apply()
        _twoFaPeriod.value = period
    }

    fun setAutoCopyTwoFa(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COPY_2FA, enabled).apply()
        _autoCopyTwoFa.value = enabled
    }

    companion object {
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_OVERLAY_POS_RIGHT = "overlay_pos_right"
        private const val KEY_MAIL_PROVIDER = "mail_provider"
        private const val KEY_POLLING_INTERVAL = "polling_interval"
        private const val KEY_AUTO_COPY_EMAIL = "auto_copy_email"
        private const val KEY_AUTO_COPY_OTP = "auto_copy_otp"
        private const val KEY_2FA_DIGITS = "2fa_digits"
        private const val KEY_2FA_PERIOD = "2fa_period"
        private const val KEY_AUTO_COPY_2FA = "auto_copy_2fa"
    }
}
