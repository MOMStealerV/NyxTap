package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MailboxStatus
import com.example.ui.theme.NyxTypography
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCard
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchGreen
import com.example.ui.theme.StitchGreenBadgeBg
import com.example.ui.theme.StitchGreenBadgeText
import com.example.ui.theme.StitchPillDark
import com.example.ui.theme.StitchPillDarkBorder
import com.example.ui.theme.StitchPillDarkOn
import com.example.ui.theme.StitchRed
import com.example.ui.theme.StitchRedBadgeBg
import com.example.ui.theme.StitchRedBadgeText
import com.example.ui.theme.StitchTextMuted
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import com.example.ui.theme.StitchAmber
import com.example.ui.theme.StitchAmberBadgeBg
import com.example.ui.theme.StitchAmberBadgeText

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = StitchGlassCard,
    borderColor: Color = StitchGlassBorder,
    cornerRadius: Dp = 18.dp,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Surface(
        modifier = clickableModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        color = backgroundColor,
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(padding)
        ) {
            content()
        }
    }
}

@Composable
fun StatusBadge(
    status: MailboxStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        is MailboxStatus.Idle -> Triple(
            StitchGlassCardElevated,
            StitchTextSecondary,
            "Idle"
        )
        is MailboxStatus.Generating -> Triple(
            StitchAmberBadgeBg,
            StitchAmberBadgeText,
            "Generating..."
        )
        is MailboxStatus.Active -> Triple(
            StitchGreenBadgeBg,
            StitchGreenBadgeText,
            "Active"
        )
        is MailboxStatus.Monitoring -> {
            val mins = status.remainingSeconds / 60
            val secs = status.remainingSeconds % 60
            val timeStr = if (mins > 0) "${mins}m" else "${secs}s"
            Triple(
                StitchGlassCardElevated,
                StitchTextPrimary,
                "Monitoring ($timeStr)"
            )
        }
        is MailboxStatus.Checking -> Triple(
            StitchGlassCardElevated,
            StitchTextPrimary,
            "Checking (#${status.attempt})"
        )
        is MailboxStatus.EmailReceived -> Triple(
            StitchGreenBadgeBg,
            StitchGreenBadgeText,
            "Received (${status.messageCount})"
        )
        is MailboxStatus.EmailReceivedNoCode -> Triple(
            StitchGlassCardElevated,
            StitchTextPrimary,
            "Email received"
        )
        is MailboxStatus.CodeDetected -> Triple(
            StitchGreenBadgeBg,
            StitchGreenBadgeText,
            "OTP detected"
        )
        is MailboxStatus.Timeout -> Triple(
            StitchAmberBadgeBg,
            StitchAmberBadgeText,
            "Timeout (10m)"
        )
        is MailboxStatus.MailboxCreated -> Triple(
            StitchGreenBadgeBg,
            StitchGreenBadgeText,
            "Mailbox created"
        )
        is MailboxStatus.Stopped -> Triple(
            StitchGlassCardElevated,
            StitchTextSecondary,
            "Stopped"
        )
        is MailboxStatus.AuthError -> Triple(
            StitchRedBadgeBg,
            StitchRedBadgeText,
            "Auth error"
        )
        is MailboxStatus.NetworkError -> Triple(
            StitchAmberBadgeBg,
            StitchAmberBadgeText,
            "Network error"
        )
        is MailboxStatus.ServerError -> Triple(
            StitchRedBadgeBg,
            StitchRedBadgeText,
            "Server error (${status.httpCode})"
        )
        is MailboxStatus.Error -> Triple(
            StitchRedBadgeBg,
            StitchRedBadgeText,
            "Error"
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(100.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StitchGlassBorderSubtle),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = NyxTypography.StatusBadge,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CodeDisplayCard(
    code: String,
    label: String = "2FA Authenticator",
    remainingSeconds: Int? = null,
    progress: Float? = null,
    copied: Boolean = false,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        padding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = StitchTextSecondary
            )

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

        Spacer(modifier = Modifier.height(10.dp))

        val formattedCode = if (code.length == 6) {
            "${code.substring(0, 3)} ${code.substring(3)}"
        } else {
            code
        }

        AnimatedContent(
            targetState = formattedCode,
            transitionSpec = {
                (fadeIn(tween(140, easing = FastOutSlowInEasing)) + slideInVertically(tween(140)) { it / 3 })
                    .togetherWith(fadeOut(tween(100, easing = FastOutLinearInEasing)) + slideOutVertically(tween(100)) { -it / 3 })
            },
            label = "code_display_anim"
        ) { targetCode ->
            Text(
                text = targetCode,
                style = NyxTypography.OtpCode,
                color = StitchTextPrimary
            )
        }

        if (remainingSeconds != null && progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "code_card_progress"
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (remainingSeconds <= 5) StitchRed else StitchTextPrimary,
                    trackColor = StitchGlassBorderSubtle
                )
                Text(
                    text = "${remainingSeconds}s",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (remainingSeconds <= 5) StitchRed else StitchTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .clickable { onCopyClick() },
            color = if (copied) StitchGreenBadgeBg else StitchPillDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (copied) StitchGreen.copy(alpha = 0.4f) else StitchPillDarkBorder),
            shape = RoundedCornerShape(100.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) StitchGreenBadgeText else StitchPillDarkOn,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (copied) "Copied to clipboard" else "Copy code",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = if (copied) StitchGreenBadgeText else StitchPillDarkOn
                )
            }
        }
    }
}

@Composable
fun CountdownGauge(
    seconds: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        seconds <= 5 -> StitchRed
        seconds <= 10 -> StitchAmber
        else -> StitchTextPrimary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(20.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(18.dp),
                color = ringColor,
                strokeWidth = 2.dp,
                trackColor = StitchGlassBorderSubtle
            )
        }
        Text(
            text = "${seconds}s",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = ringColor
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = StitchTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = StitchTextSecondary
            )
        }
    }
}
