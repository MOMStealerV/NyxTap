package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.UpdateInfo
import com.example.data.model.UpdateState
import com.example.ui.theme.StitchBlack
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCard
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

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StitchGlassBorder, RoundedCornerShape(20.dp))
                .testTag("update_available_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = StitchGlassCardElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Logo & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp),
                            color = StitchGlassCard,
                            border = BorderStroke(1.dp, StitchGlassBorder)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.nyxtap_logo_exact),
                                contentDescription = "NyxTap Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = "NyxTap Update Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StitchTextPrimary
                            )
                            Text(
                                text = "Official GitHub Release",
                                style = MaterialTheme.typography.bodySmall,
                                color = StitchTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            tint = StitchTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Version Badge & Comparison
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StitchBlack.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, StitchGlassBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = StitchTextMuted
                        )
                        Text(
                            text = updateInfo.latestVersionName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StitchTextPrimary
                        )
                    }

                    Surface(
                        color = StitchGreenBadgeBg,
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, StitchGreen.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (updateInfo.isPrerelease) "BETA" else "STABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = StitchGreenBadgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Release Title & Notes
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchTextPrimary
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .background(StitchBlack.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .border(1.dp, StitchGlassBorderSubtle, RoundedCornerShape(10.dp)),
                        color = StitchBlack.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = StitchTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, StitchGlassBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StitchTextSecondary
                        )
                    ) {
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onUpdateNow,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                            .testTag("update_now_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StitchPillWhite,
                            contentColor = StitchPillWhiteOn
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Update Now",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDownloadingDialog(
    versionName: String,
    progressPercent: Int,
    bytesDownloaded: Long,
    totalBytes: Long,
    isVerifying: Boolean = false,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StitchGlassBorder, RoundedCornerShape(20.dp))
                .testTag("update_downloading_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = StitchGlassCardElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular icon
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    color = StitchGlassCard,
                    border = BorderStroke(1.dp, StitchGlassBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVerifying) Icons.Default.Security else Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = StitchTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Updating NyxTap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StitchTextPrimary
                    )
                    Text(
                        text = if (isVerifying) "Verifying APK checksum..." else "Downloading $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StitchTextSecondary
                    )
                }

                // Progress Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { if (isVerifying) 1f else (progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StitchTextPrimary,
                        trackColor = StitchGlassBorderSubtle,
                        strokeCap = StrokeCap.Round
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (totalBytes > 0) {
                                "${formatFileSize(bytesDownloaded)} / ${formatFileSize(totalBytes)}"
                            } else {
                                formatFileSize(bytesDownloaded)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = StitchTextMuted
                        )
                        Text(
                            text = if (isVerifying) "Verifying" else "$progressPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StitchTextPrimary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, StitchGlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StitchTextSecondary
                    )
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateErrorDialog(
    errorMessage: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StitchRed.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .testTag("update_error_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = StitchGlassCardElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    color = StitchRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, StitchRed.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StitchRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Text(
                    text = "Update Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StitchTextPrimary
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StitchTextSecondary,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, StitchGlassBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StitchTextSecondary
                        )
                    ) {
                        Text(text = "Dismiss")
                    }

                    if (canRetry) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("update_retry_button"),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StitchPillWhite,
                                contentColor = StitchPillWhiteOn
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StitchGlassBorder, RoundedCornerShape(20.dp))
                .testTag("install_permission_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = StitchGlassCardElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    color = StitchGlassCard,
                    border = BorderStroke(1.dp, StitchGlassBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = StitchTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StitchTextPrimary
                )

                Text(
                    text = "Android requires permission to install APK updates from NyxTap. Tap 'Open Settings' to enable 'Allow from this source'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StitchTextSecondary,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, StitchGlassBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StitchTextSecondary
                        )
                    ) {
                        Text(text = "Cancel")
                    }

                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .testTag("open_install_settings_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StitchPillWhite,
                            contentColor = StitchPillWhiteOn
                        )
                    ) {
                        Text(text = "Open Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
