package com.example.ui.mail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TempMail2FAApp
import com.example.data.model.EmailMessage
import com.example.data.model.MailboxStatus
import com.example.ui.components.AppCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusBadge
import com.example.ui.theme.NyxTypography
import com.example.ui.theme.StitchAmber
import com.example.ui.theme.StitchAmberBadgeBg
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MailScreen(
    modifier: Modifier = Modifier
) {
    val app = TempMail2FAApp.instance
    val scope = rememberCoroutineScope()

    val mailboxStatus by app.mailRepository.mailboxStatus.collectAsState()
    val emailList by app.mailRepository.allEmails.collectAsState(initial = emptyList())
    val diagnostics by app.mailRepository.diagnostics.collectAsState()

    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var feedbackToast by remember { mutableStateOf<String?>(null) }

    val currentEmail = mailboxStatus.currentEmailOrNull ?: app.mailRepository.currentEmail
    val isGenerating = mailboxStatus is MailboxStatus.Generating

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Header with Diagnostics action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mail",
                        style = MaterialTheme.typography.displaySmall,
                        color = StitchTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NowCare AHEM inbox with automatic OTP extraction",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StitchTextSecondary
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { showDiagnosticsDialog = true }
                        .testTag("mail_diagnostics_button"),
                    color = StitchGlassCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = StitchTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Diagnostics",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = StitchTextPrimary
                        )
                    }
                }
            }
        }

        // Active Email Card
        item {
            AppCard {
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
                            text = "Active inbox",
                            style = MaterialTheme.typography.labelMedium,
                            color = StitchTextSecondary
                        )
                        Surface(
                            color = StitchGlassCardElevated,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "nowcare.us",
                                style = NyxTypography.DiagnosticValue.copy(fontSize = 11.sp),
                                color = StitchTextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    StatusBadge(status = mailboxStatus)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!currentEmail.isNullOrBlank()) {
                    Text(
                        text = currentEmail,
                        style = NyxTypography.MailboxAddress,
                        color = StitchTextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    app.clipboardManager.copyEmail(currentEmail)
                                    feedbackToast = "✓ Email address copied to clipboard"
                                },
                            color = StitchPillDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = StitchPillDarkOn,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy email", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = StitchPillDarkOn)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    scope.launch {
                                        val (otp, count) = app.mailRepository.checkForOtpNow()
                                        feedbackToast = if (!otp.isNullOrBlank()) {
                                            "✓ OTP Found: $otp (Copied!)"
                                        } else {
                                            if (count > 0) "$count email(s) checked, no OTP found" else "Inbox checked (0 emails)"
                                        }
                                    }
                                },
                            color = StitchPillDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = StitchPillDarkOn,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check inbox", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = StitchPillDarkOn)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No active disposable email",
                        style = MaterialTheme.typography.bodyLarge,
                        color = StitchTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Generate new email' to create a NowCare address instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StitchTextMuted
                    )
                }
            }
        }

        // Monitoring State Banner
        when (val status = mailboxStatus) {
            is MailboxStatus.Monitoring -> {
                item {
                    val remainingMins = status.remainingSeconds / 60
                    val remainingSecs = status.remainingSeconds % 60
                    val countdownStr = "${remainingMins}m ${remainingSecs.toString().padStart(2, '0')}s"

                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = StitchTextPrimary,
                                    strokeWidth = 2.dp
                                )
                                Column {
                                    Text(
                                        text = "Monitoring inbox",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = StitchTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Waiting for email...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StitchTextSecondary
                                    )
                                }
                            }

                            Text(
                                text = countdownStr,
                                style = NyxTypography.DiagnosticValue.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = StitchTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { (status.remainingSeconds.toFloat() / 600f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = StitchTextPrimary,
                            trackColor = StitchGlassBorderSubtle
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (status.transientError != null) status.transientError else "Poll attempt #${status.attempt} · Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.transientError != null) StitchAmber else StitchTextSecondary
                            )

                            TextButton(
                                onClick = { app.mailRepository.stopMonitoring() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = StitchTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", style = MaterialTheme.typography.labelMedium, color = StitchTextSecondary)
                            }
                        }
                    }
                }
            }

            is MailboxStatus.CodeDetected -> {
                item {
                    AppCard(
                        backgroundColor = StitchGreenBadgeBg,
                        borderColor = StitchGreen.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StitchGreenBadgeText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Verification code detected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StitchGreenBadgeText
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val formattedOtp = if (status.code.length == 6) {
                                    "${status.code.substring(0, 3)} ${status.code.substring(3)}"
                                } else {
                                    status.code
                                }

                                Text(
                                    text = formattedOtp,
                                    style = NyxTypography.OtpCode,
                                    color = StitchGreenBadgeText
                                )

                                if (!status.subject.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = status.subject,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = StitchGreenBadgeText.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .clickable {
                                        app.clipboardManager.copyOtp(status.code)
                                        feedbackToast = "✓ OTP ${status.code} copied"
                                    },
                                color = StitchGreen,
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = "Copy code",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = StitchPillWhiteOn,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            else -> Unit
        }

        // Primary Action: Generate New Email
        item {
            Button(
                onClick = {
                    scope.launch {
                        val email = app.mailRepository.generateNewMailbox()
                        feedbackToast = "✓ NowCare email copied: $email"
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_email_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StitchPillWhite,
                    contentColor = StitchPillWhiteOn
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = StitchPillWhiteOn,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generating NowCare address...",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentEmail == null) "Generate new email" else "Generate new address",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Section Title: Recent Emails
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Recent emails",
                    subtitle = "${emailList.size} messages received"
                )

                if (emailList.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            scope.launch { app.mailRepository.clearHistory() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear inbox",
                            tint = StitchTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Email Items List
        if (emailList.isEmpty()) {
            item {
                AppCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = StitchTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Inbox is empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchTextPrimary
                        )
                        Text(
                            text = "Incoming emails to your NowCare address will appear here automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = StitchTextSecondary
                        )
                    }
                }
            }
        } else {
            items(emailList, key = { it.id }) { email ->
                EmailListItem(
                    email = email,
                    onClick = { selectedEmail = email },
                    onCopyOtp = { otp ->
                        app.clipboardManager.copyOtp(otp)
                        feedbackToast = "✓ OTP $otp copied"
                    }
                )
            }
        }
    }

    // Detail Dialog when email is clicked
    if (selectedEmail != null) {
        val email = selectedEmail!!
        val formattedDate = remember(email.timestamp) {
            SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(email.timestamp))
        }

        AlertDialog(
            onDismissRequest = { selectedEmail = null },
            containerColor = StitchGlassCardElevated,
            title = {
                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.titleLarge,
                    color = StitchTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "From: ${email.sender}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StitchTextPrimary
                    )

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = StitchTextSecondary
                    )

                    if (!email.extractedOtp.isNullOrBlank()) {
                        AppCard(
                            backgroundColor = StitchGreenBadgeBg,
                            borderColor = StitchGreen.copy(alpha = 0.4f),
                            padding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DETECTED CODE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StitchGreenBadgeText
                                    )
                                    Text(
                                        text = email.extractedOtp,
                                        style = NyxTypography.OtpCode.copy(
                                            fontSize = 24.sp,
                                            color = StitchGreenBadgeText
                                        )
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .clickable {
                                            app.clipboardManager.copyOtp(email.extractedOtp)
                                            feedbackToast = "✓ OTP copied"
                                        },
                                    color = StitchGreen,
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "Copy code",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = StitchPillWhiteOn,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    AppCard(
                        backgroundColor = StitchGlassCardElevated,
                        borderColor = StitchGlassBorderSubtle,
                        padding = 12.dp
                    ) {
                        Text(
                            text = email.bodyText.ifBlank { "(No plain text content)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = StitchTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEmail = null }) {
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = StitchTextPrimary)
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
private fun EmailListItem(
    email: EmailMessage,
    onClick: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    val formattedTime = remember(email.timestamp) {
        val diffMinutes = (System.currentTimeMillis() - email.timestamp) / (1000 * 60)
        when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(email.timestamp))
        }
    }

    AppCard(
        onClick = onClick,
        cornerRadius = 16.dp,
        padding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = email.sender,
                style = MaterialTheme.typography.titleSmall,
                color = StitchTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = StitchTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = email.subject,
            style = MaterialTheme.typography.bodyLarge,
            color = StitchTextSecondary
        )

        if (!email.extractedOtp.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StitchGreenBadgeBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "OTP:",
                        style = MaterialTheme.typography.labelSmall,
                        color = StitchGreenBadgeText
                    )
                    Text(
                        text = email.extractedOtp,
                        style = NyxTypography.OtpCode.copy(fontSize = 16.sp),
                        color = StitchGreenBadgeText
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { onCopyOtp(email.extractedOtp) },
                    color = StitchGreen,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = StitchPillWhiteOn,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
