package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UpdateState
import com.example.service.FloatingOverlayService
import com.example.ui.components.InstallPermissionDialog
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.components.UpdateDownloadingDialog
import com.example.ui.components.UpdateErrorDialog
import com.example.ui.home.HomeScreen
import com.example.ui.mail.MailScreen
import com.example.ui.name.NameScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.StitchBlack
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchPillWhite
import com.example.ui.theme.StitchPillWhiteOn
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import com.example.ui.theme.TempMail2FATheme
import com.example.ui.twofa.TwoFaScreen

enum class Screen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("HOME", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    MAIL("MAIL", Icons.Filled.Email, Icons.Outlined.Email, "nav_mail"),
    TWO_FA("2FA", Icons.Filled.Key, Icons.Outlined.Key, "nav_2fa"),
    NAME("NAME", Icons.Filled.Person, Icons.Outlined.Person, "nav_name"),
    SETTINGS("SETTINGS", Icons.Filled.Settings, Icons.Outlined.SettingsOutlined, "nav_settings");

    companion object {
        val bottomNavScreens = listOf(HOME, MAIL, TWO_FA, NAME)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = TempMail2FAApp.instance
        if (Settings.canDrawOverlays(this) && app.settingsRepository.overlayEnabled.value) {
            FloatingOverlayService.start(this)
        }

        setContent {
            TempMail2FATheme {
                var isSplashVisible by remember { mutableStateOf(true) }

                Crossfade(
                    targetState = isSplashVisible,
                    animationSpec = tween(durationMillis = 350),
                    label = "splash_to_main_crossfade"
                ) { showSplash ->
                    if (showSplash) {
                        SplashScreen(
                            onSplashFinished = { isSplashVisible = false }
                        )
                    } else {
                        MainAppContent()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = TempMail2FAApp.instance
        if (Settings.canDrawOverlays(this) && app.settingsRepository.overlayEnabled.value) {
            FloatingOverlayService.start(this)
        }
    }
}

@Composable
fun MainAppContent() {
    val app = TempMail2FAApp.instance
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    val updateState by app.updateRepository.updateState.collectAsState()
    val autoCheckUpdates by app.settingsRepository.autoCheckUpdates.collectAsState()
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var showInstallPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(autoCheckUpdates) {
        if (autoCheckUpdates) {
            app.updateRepository.checkForUpdates(isManual = false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StitchBlack,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(
                        width = 1.dp,
                        color = StitchGlassBorderSubtle
                    ),
                color = StitchBlack,
                shadowElevation = 0.dp
            ) {
                NavigationBar(
                    modifier = Modifier.testTag("main_bottom_nav"),
                    containerColor = StitchBlack,
                    tonalElevation = 0.dp
                ) {
                    Screen.bottomNavScreens.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StitchTextPrimary,
                                selectedTextColor = StitchTextPrimary,
                                indicatorColor = StitchGlassCardElevated,
                                unselectedIconColor = StitchTextSecondary,
                                unselectedTextColor = StitchTextSecondary
                            ),
                            modifier = Modifier.testTag(screen.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    onNavigateToMail = { currentScreen = Screen.MAIL },
                    onNavigateToTwoFa = { currentScreen = Screen.TWO_FA },
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                )
                Screen.MAIL -> MailScreen()
                Screen.TWO_FA -> TwoFaScreen()
                Screen.NAME -> NameScreen()
                Screen.SETTINGS -> SettingsScreen(
                    onNavigateBack = { currentScreen = Screen.HOME }
                )
            }
        }
    }

    // Global In-App Update Dialogs
    when (val state = updateState) {
        is UpdateState.UpdateAvailable -> {
            if (state.updateInfo.latestVersionName != dismissedUpdateVersion) {
                UpdateAvailableDialog(
                    updateInfo = state.updateInfo,
                    onUpdateNow = {
                        app.updateRepository.startDownload(
                            scope = coroutineScope,
                            updateInfo = state.updateInfo,
                            onPermissionNeeded = { showInstallPermissionDialog = true }
                        )
                    },
                    onDismiss = {
                        dismissedUpdateVersion = state.updateInfo.latestVersionName
                        app.updateRepository.dismissUpdate()
                    }
                )
            }
        }
        is UpdateState.Downloading -> {
            UpdateDownloadingDialog(
                versionName = state.updateInfo.latestVersionName,
                progressPercent = state.progressPercent,
                bytesDownloaded = state.bytesDownloaded,
                totalBytes = state.totalBytes,
                isVerifying = false,
                onCancel = { app.updateRepository.cancelDownload() }
            )
        }
        is UpdateState.Verifying -> {
            UpdateDownloadingDialog(
                versionName = state.updateInfo.latestVersionName,
                progressPercent = 100,
                bytesDownloaded = state.updateInfo.apkSizeBytes,
                totalBytes = state.updateInfo.apkSizeBytes,
                isVerifying = true,
                onCancel = { app.updateRepository.cancelDownload() }
            )
        }
        is UpdateState.Error -> {
            UpdateErrorDialog(
                errorMessage = state.message,
                canRetry = state.canRetry && state.failedUpdateInfo != null,
                onRetry = {
                    state.failedUpdateInfo?.let { info ->
                        app.updateRepository.startDownload(
                            scope = coroutineScope,
                            updateInfo = info,
                            onPermissionNeeded = { showInstallPermissionDialog = true }
                        )
                    }
                },
                onDismiss = { app.updateRepository.dismissUpdate() }
            )
        }
        else -> {}
    }

    if (showInstallPermissionDialog) {
        InstallPermissionDialog(
            onOpenSettings = {
                showInstallPermissionDialog = false
                app.updateRepository.openInstallPermissionSettings()
            },
            onDismiss = { showInstallPermissionDialog = false }
        )
    }
}
