package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.TempMail2FAApp
import com.example.data.model.MailboxStatus
import com.example.ui.overlay.FloatingOverlayComposable
import com.example.ui.theme.TempMail2FATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var otpListenerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForegroundServiceNotification()
        initOverlay()
        listenForOtpEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        if (overlayComposeView == null) {
            initOverlay()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = buildForegroundNotification("Floating overlay active on top of all apps")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayComposeView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = resources.displayMetrics

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var currentPosX = 40f
        var currentPosY = 120f

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        val app = TempMail2FAApp.instance

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@FloatingOverlayService))

            setContent {
                TempMail2FATheme {
                    val mailboxStatus by app.mailRepository.mailboxStatus.collectAsState()
                    val currentTotp by app.twoFaRepository.currentTotp.collectAsState()

                    FloatingOverlayComposable(
                        mailboxStatus = mailboxStatus,
                        currentTotp = currentTotp,
                        onGenerateMail = {
                            serviceScope.launch {
                                app.mailRepository.generateNewMailbox()
                            }
                        },
                        onGenerateName = {
                            val name = app.nameRepository.generateAndCopy()
                            name.copiedText
                        },
                        onContinueMonitoring = {
                            app.mailRepository.continueWaiting(10)
                        },
                        onCheckInboxForOtp = {
                            app.mailRepository.checkForOtpNow()
                        },
                        onGenerateTwoFaFromClipboard = {
                            app.twoFaRepository.generateFromClipboard()
                        },
                        onCopyEmail = { email ->
                            app.clipboardManager.copyEmail(email)
                        },
                        onCopyOtp = { otp ->
                            app.clipboardManager.copyOtp(otp)
                        },
                        onCloseOverlay = {
                            app.settingsRepository.setOverlayEnabled(false)
                            stopSelf()
                        },
                        onOpenMainApp = {
                            val launchIntent = Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(launchIntent)
                        },
                        onDragDelta = { dx, dy ->
                            val params = this@FloatingOverlayService.layoutParams
                            val wm = this@FloatingOverlayService.windowManager
                            val view = this@FloatingOverlayService.overlayComposeView
                            if (params != null && wm != null && view != null) {
                                val maxX = (displayMetrics.widthPixels - 60).toFloat().coerceAtLeast(100f)
                                val maxY = (displayMetrics.heightPixels - 100).toFloat().coerceAtLeast(100f)
                                currentPosX = (currentPosX + dx).coerceIn(0f, maxX)
                                currentPosY = (currentPosY + dy).coerceIn(20f, maxY)
                                val newX = currentPosX.roundToInt()
                                val newY = currentPosY.roundToInt()
                                if (params.x != newX || params.y != newY) {
                                    params.x = newX
                                    params.y = newY
                                    try {
                                        wm.updateViewLayout(view, params)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val screenWidth = displayMetrics.widthPixels
                            val overlayWidth = 100
                            val targetX = if (currentPosX + overlayWidth / 2 < screenWidth / 2) {
                                16f
                            } else {
                                (screenWidth - overlayWidth - 16f).coerceAtLeast(16f)
                            }
                            serviceScope.launch {
                                val startX = currentPosX
                                val duration = 180L
                                val startTime = System.currentTimeMillis()
                                while (isActive) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                                    val eased = 1f - (1f - fraction) * (1f - fraction)
                                    currentPosX = startX + (targetX - startX) * eased
                                    val params = this@FloatingOverlayService.layoutParams ?: break
                                    val wm = this@FloatingOverlayService.windowManager ?: break
                                    val view = this@FloatingOverlayService.overlayComposeView ?: break
                                    params.x = currentPosX.roundToInt()
                                    try {
                                        wm.updateViewLayout(view, params)
                                    } catch (e: Exception) {
                                        break
                                    }
                                    if (fraction >= 1f) break
                                    delay(16)
                                }
                            }
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, layoutParams)
            overlayComposeView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun listenForOtpEvents() {
        val app = TempMail2FAApp.instance
        otpListenerJob = serviceScope.launch {
            app.mailRepository.latestOtpEvent.collectLatest { (email, otp) ->
                showOtpNotification(otp, email)
            }
        }
    }

    private fun showOtpNotification(otp: String, email: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val copyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OTP", otp)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            101,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TempMail2FAApp.CHANNEL_OTP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("✓ Verification Code: $otp")
            .setContentText("Detected from $email. Copied to clipboard.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_OTP_ID, notification)
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TempMail2FAApp.CHANNEL_SERVICE)
            .setContentTitle("Temp Mail & 2FA Overlay")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()

        if (overlayComposeView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayComposeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayComposeView = null
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_OTP_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
