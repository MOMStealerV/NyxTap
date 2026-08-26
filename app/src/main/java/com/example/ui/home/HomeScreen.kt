package com.example.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.TempMail2FAApp
import com.example.data.model.MailboxStatus
import com.example.service.FloatingOverlayService
import com.example.ui.components.AppCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.NyxTypography
import com.example.ui.theme.StitchBlack
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchGreen
import com.example.ui.theme.StitchGreenBadgeBg
import com.example.ui.theme.StitchGreenBadgeText
import com.example.ui.theme.StitchPillDark
import com.example.ui.theme.StitchPillDarkBorder
import com.example.ui.theme.StitchPillDarkOn
import com.example.ui.theme.StitchPillWhite
import com.example.ui.theme.StitchPillWhiteOn
import com.example.ui.theme.StitchRed
import com.example.ui.theme.StitchTextMuted
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    onNavigateToMail: () -> Unit,
    onNavigateToTwoFa: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = TempMail2FAApp.instance
    val scope = rememberCoroutineScope()

    val mailboxStatus by app.mailRepository.mailboxStatus.collectAsState()
    val currentTotp by app.twoFaRepository.currentTotp.collectAsState()
    val overlayEnabled by app.settingsRepository.overlayEnabled.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val hasPerm = Settings.canDrawOverlays(context)
                hasOverlayPermission = hasPerm
                if (hasPerm && overlayEnabled) {
                    FloatingOverlayService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stitch Header: Greeting, Dashboard Title, NyxTap Logo (Opens Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StitchTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = StitchTextPrimary
                )
            }

            // Clickable App Icon that navigates to Settings
            Surface(
                shape = CircleShape,
                color = StitchGlassCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorder),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToSettings)
                    .testTag("home_settings_app_icon")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.nyxtap_logo_exact),
                        contentDescription = "App Settings",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }
        }

        // Active Status Indicator Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (overlayEnabled && hasOverlayPermission) StitchGreen else StitchTextMuted)
            )
            Text(
                text = if (overlayEnabled && hasOverlayPermission) "Floating overlay active" else if (!hasOverlayPermission) "Overlay permission required" else "Floating overlay disabled",
                style = MaterialTheme.typography.labelMedium,
                color = if (!hasOverlayPermission) StitchRed else StitchTextSecondary
            )
        }

        if (!hasOverlayPermission) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                color = StitchGlassCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, StitchRed.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Permission Required",
                        tint = StitchRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable 'Display Over Other Apps'",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StitchTextPrimary
                        )
                        Text(
                            text = "Tap to grant permission for the floating utility widget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StitchTextSecondary
                        )
                    }
                }
            }
        }

        // Card 1: Temporary Mailbox
        val currentEmail = when (val status = mailboxStatus) {
            is MailboxStatus.Active -> status.email
            is MailboxStatus.CodeDetected -> status.email
            is MailboxStatus.EmailReceived -> status.email
            else -> app.mailRepository.currentEmail
        }

        val latestOtp = when (val status = mailboxStatus) {
            is MailboxStatus.CodeDetected -> status.code
            else -> null
        }

        AppCard(
            onClick = onNavigateToMail,
            modifier = Modifier.testTag("home_mail_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = StitchGlassCardElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = StitchTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Temporary mailbox",
                        style = MaterialTheme.typography.titleMedium,
                        color = StitchTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                StatusBadge(status = mailboxStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentEmail ?: "Tap generate for instant inbox",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = if (currentEmail != null) FontFamily.Monospace else FontFamily.SansSerif,
                    fontWeight = if (currentEmail != null) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (currentEmail != null) StitchTextPrimary else StitchTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = StitchGlassBorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Latest OTP: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextSecondary
                    )
                    Text(
                        text = latestOtp ?: "Waiting...",
                        style = if (latestOtp != null) NyxTypography.DiagnosticValue.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = if (latestOtp != null) StitchGreenBadgeText else StitchTextMuted
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            if (!currentEmail.isNullOrBlank()) {
                                app.clipboardManager.copyEmail(currentEmail)
                            } else {
                                scope.launch { app.mailRepository.generateNewMailbox() }
                            }
                        },
                    color = StitchPillDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = if (!currentEmail.isNullOrBlank()) "Copy address" else "Generate new",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = StitchPillDarkOn,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Card 2: 2FA Authenticator
        AppCard(
            onClick = onNavigateToTwoFa,
            modifier = Modifier.testTag("home_2fa_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = StitchGlassCardElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = StitchTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "2FA Authenticator",
                        style = MaterialTheme.typography.titleMedium,
                        color = StitchTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = StitchGreenBadgeBg,
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchGreen.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Active",
                        style = NyxTypography.StatusBadge,
                        color = StitchGreenBadgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val totpCode = currentTotp?.code ?: "842 119"
            val formattedTotp = if (totpCode.length == 6) {
                "${totpCode.substring(0, 3)} ${totpCode.substring(3)}"
            } else {
                totpCode
            }

            Text(
                text = formattedTotp,
                style = NyxTypography.OtpCode,
                color = StitchTextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { currentTotp?.progress ?: 0.75f },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = StitchTextPrimary,
                    trackColor = StitchGlassBorderSubtle
                )
                Text(
                    text = "${currentTotp?.remainingSeconds ?: 22}s",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = StitchTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            if (currentTotp != null) {
                                app.clipboardManager.copyTotp(currentTotp!!.code)
                            } else {
                                app.twoFaRepository.generateFromClipboard()
                            }
                        },
                    color = StitchPillDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = if (currentTotp != null) "Copy code" else "Generate from clipboard",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = StitchPillDarkOn,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Card 3: Floating Overlay Preference & Guide
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = StitchGlassCardElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = StitchTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Floating overlay",
                            style = MaterialTheme.typography.titleMedium,
                            color = StitchTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (overlayEnabled) "Active on screen" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = StitchTextSecondary
                        )
                    }
                }

                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { isChecked ->
                        app.settingsRepository.setOverlayEnabled(isChecked)
                        if (isChecked) {
                            if (Settings.canDrawOverlays(context)) {
                                FloatingOverlayService.start(context)
                                hasOverlayPermission = true
                            } else {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        } else {
                            FloatingOverlayService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StitchPillWhiteOn,
                        checkedTrackColor = StitchPillWhite,
                        uncheckedThumbColor = StitchTextSecondary,
                        uncheckedTrackColor = StitchGlassCardElevated
                    ),
                    modifier = Modifier.testTag("overlay_toggle_switch")
                )
            }
        }
    }
}
