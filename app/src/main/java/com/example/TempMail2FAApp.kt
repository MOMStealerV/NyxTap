package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.provider.AhemMailProvider
import com.example.data.repository.MailRepository
import com.example.data.repository.NameRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TwoFaRepository
import com.example.data.repository.UpdateRepository
import com.example.util.AppClipboardManager

class TempMail2FAApp : Application() {

    lateinit var clipboardManager: AppClipboardManager
        private set
    lateinit var mailRepository: MailRepository
        private set
    lateinit var twoFaRepository: TwoFaRepository
        private set
    lateinit var nameRepository: NameRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var updateRepository: UpdateRepository
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        clipboardManager = AppClipboardManager(this)
        settingsRepository = SettingsRepository(this)
        updateRepository = UpdateRepository(this, settingsRepository)
        database = AppDatabase.getDatabase(this)
        mailRepository = MailRepository(this, AhemMailProvider(), clipboardManager)
        twoFaRepository = TwoFaRepository(this, clipboardManager)
        nameRepository = NameRepository(this, clipboardManager)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Floating Overlay & Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating overlay utility and mailbox monitoring"
                setShowBadge(false)
            }

            val otpChannel = NotificationChannel(
                CHANNEL_OTP,
                "Verification Code Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant notifications when verification OTP arrives"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(otpChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "tempmail_2fa_service"
        const val CHANNEL_OTP = "tempmail_2fa_otp"

        lateinit var instance: TempMail2FAApp
            private set
    }
}
