package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.TempMail2FAApp
import com.example.service.FloatingOverlayService
import com.example.ui.components.AppCard
import com.example.ui.components.SectionTitle
import com.example.ui.mail.MailDiagnosticsDialog
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchGreen
import com.example.ui.theme.StitchGreenBadgeBg
import com.example.ui.theme.StitchGreenBadgeText
import com.example.ui.theme.StitchPillWhite
import com.example.ui.theme.StitchPillWhiteOn
import com.example.ui.theme.StitchRed
import com.example.ui.theme.StitchTextMuted
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = TempMail2FAApp.instance
    val scope = rememberCoroutineScope()

    val overlayEnabled by app.settingsRepository.overlayEnabled.collectAsState()
    val autoCopyEmail by app.settingsRepository.autoCopyEmail.collectAsState()
    val autoCopyOtp by app.settingsRepository.autoCopyOtp.collectAsState()
    val autoCopyTwoFa by app.settingsRepository.autoCopyTwoFa.collectAsState()
    val diagnostics by app.mailRepository.diagnostics.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Header with Back button support
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(StitchGlassCardElevated, CircleShape)
                        .border(1.dp, StitchGlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = StitchTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = StitchTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Configuration & preferences",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StitchTextSecondary
                )
            }
        }

        // Section: Floating Overlay
        SectionTitle(title = "Overlay", subtitle = "Floating utility widget preferences")

        AppCard {
            SettingSwitchRow(
                icon = Icons.Default.Layers,
                title = "Floating Overlay",
                subtitle = "Keep compact utility active over other apps",
                checked = overlayEnabled,
                onCheckedChange = { isChecked ->
                    app.settingsRepository.setOverlayEnabled(isChecked)
                    if (isChecked) {
                        if (Settings.canDrawOverlays(context)) {
                            FloatingOverlayService.start(context)
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
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            SettingClickableRow(
                icon = Icons.Default.OpenInNew,
                title = "System Overlay Permission",
                subtitle = if (Settings.canDrawOverlays(context)) "Granted" else "Tap to open system settings",
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )
        }

        // Section: Mail Provider & Configuration
        SectionTitle(title = "Mail Backend & Provider", subtitle = "NowCare AHEM deployment status")

        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Active Provider",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchTextPrimary
                    )
                    Text(
                        text = "AHEM / NowCare (${diagnostics.apiServerUrl})",
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextSecondary
                    )
                }

                Surface(
                    color = StitchGreenBadgeBg,
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchGreen.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = StitchGreenBadgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discovered Domain",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchTextPrimary
                    )
                    Text(
                        text = "Discovered via /api/properties",
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextSecondary
                    )
                }

                Surface(
                    color = StitchGlassCardElevated,
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle)
                ) {
                    Text(
                        text = "@${diagnostics.allowedDomains.firstOrNull() ?: "nowcare.us"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = StitchTextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            SettingClickableRow(
                icon = Icons.Default.Dns,
                title = "Mail Provider Diagnostics",
                subtitle = "Inspect live server endpoints & test connection",
                onClick = { showDiagnosticsDialog = true }
            )
        }

        // Section: Automation
        SectionTitle(title = "Automation", subtitle = "Clipboard and active monitoring settings")

        AppCard {
            SettingSwitchRow(
                icon = Icons.Default.Email,
                title = "Auto-copy generated email",
                subtitle = "Automatically copy address when generated",
                checked = autoCopyEmail,
                onCheckedChange = { app.settingsRepository.setAutoCopyEmail(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            SettingSwitchRow(
                icon = Icons.Default.Email,
                title = "Auto-copy verification OTP",
                subtitle = "Copy detected code immediately on arrival",
                checked = autoCopyOtp,
                onCheckedChange = { app.settingsRepository.setAutoCopyOtp(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            SettingSwitchRow(
                icon = Icons.Default.Key,
                title = "Auto-copy 2FA code",
                subtitle = "Copy generated code immediately to clipboard",
                checked = autoCopyTwoFa,
                onCheckedChange = { app.settingsRepository.setAutoCopyTwoFa(it) }
            )
        }

        // Section: Privacy & Storage
        SectionTitle(title = "Privacy & Storage", subtitle = "On-device local management")

        AppCard {
            SettingClickableRow(
                icon = Icons.Default.Delete,
                title = "Clear Email History",
                subtitle = "Delete all stored temporary emails from local database",
                onClick = { showClearDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            SettingClickableRow(
                icon = Icons.Default.Security,
                title = "Security & Privacy Model",
                subtitle = "No remote telemetry · Sensitive clipboard flags · Local DB only",
                onClick = {}
            )
        }

        // Section: About & App Information
        SectionTitle(title = "About NyxTap", subtitle = "Application information & build details")

        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    color = StitchGlassCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorder)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.nyxtap_logo_exact),
                        contentDescription = "NyxTap Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NyxTap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StitchTextPrimary
                        )
                        Surface(
                            color = StitchGlassCardElevated,
                            shape = RoundedCornerShape(100.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle)
                        ) {
                            Text(
                                text = "v30.2",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = StitchTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Everything you need. One tap away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextSecondary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Developer Credit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextPrimary
                )
                Text(
                    text = "Developed by MOMStealerV",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = StitchTextSecondary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = StitchGlassBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextPrimary
                )
                Text(
                    text = "v30.2",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = StitchTextSecondary
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Email History?", color = StitchTextPrimary) },
            text = { Text("This will permanently delete all cached temporary emails from your device.", color = StitchTextSecondary) },
            containerColor = StitchGlassCardElevated,
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.mailRepository.clearHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text("Clear", color = StitchRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = StitchTextSecondary)
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        MailDiagnosticsDialog(
            onDismissRequest = { showDiagnosticsDialog = false }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StitchTextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = StitchTextSecondary
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StitchPillWhiteOn,
                checkedTrackColor = StitchPillWhite,
                uncheckedThumbColor = StitchTextSecondary,
                uncheckedTrackColor = StitchGlassCardElevated
            )
        )
    }
}

@Composable
private fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StitchTextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = StitchTextSecondary
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = StitchTextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}
