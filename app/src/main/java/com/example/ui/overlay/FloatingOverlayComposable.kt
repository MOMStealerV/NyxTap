package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MailboxStatus
import com.example.data.model.TotpResult
import com.example.ui.theme.StitchBlack
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCard
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchGreen
import com.example.ui.theme.StitchGreenBadgeBg
import com.example.ui.theme.StitchRed
import com.example.ui.theme.StitchRedBadgeBg
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OverlayFeedbackCard(
    val title: String,
    val subtitle: String,
    val isSuccess: Boolean = true,
    val isMono: Boolean = false
)

@Composable
fun FloatingOverlayComposable(
    mailboxStatus: MailboxStatus,
    currentTotp: TotpResult?,
    onGenerateMail: () -> Unit,
    onGenerateName: (() -> String)? = null,
    onContinueMonitoring: (() -> Unit)? = null,
    onCheckInboxForOtp: (suspend () -> Pair<String?, Int>)? = null,
    onGenerateTwoFaFromClipboard: () -> Result<TotpResult>,
    onCopyEmail: (String) -> Unit,
    onCopyOtp: (String) -> Unit,
    onCloseOverlay: () -> Unit,
    onOpenMainApp: (() -> Unit)? = null,
    onDragDelta: ((Float, Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var resultCard by remember { mutableStateOf<OverlayFeedbackCard?>(null) }
    val scope = rememberCoroutineScope()
    var autoDismissJob by remember { mutableStateOf<Job?>(null) }

    fun showFeedback(card: OverlayFeedbackCard) {
        resultCard = card
        autoDismissJob?.cancel()
        autoDismissJob = scope.launch {
            delay(3200)
            resultCard = null
        }
    }

    // React to newly received OTP in mailboxStatus
    LaunchedEffect(mailboxStatus) {
        if (mailboxStatus is MailboxStatus.CodeDetected) {
            showFeedback(
                OverlayFeedbackCard(
                    title = mailboxStatus.code,
                    subtitle = "OTP copied to clipboard",
                    isSuccess = true,
                    isMono = true
                )
            )
        }
    }

    val expansionFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "overlay_expansion"
    )

    val mainIconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "main_icon_rotation"
    )

    Row(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta?.invoke(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = {
                        onDragEnd?.invoke()
                    }
                )
            }
            .padding(4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Vertical Action Stack (Main Icon + Sub Actions)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Main Circular Icon (48dp)
            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Tapping the overlay icon shrinks/hides or expands the action menu
                        isExpanded = !isExpanded
                    }
                    .testTag("floating_overlay_main_icon"),
                shape = CircleShape,
                color = StitchGlassCardElevated,
                border = BorderStroke(1.dp, StitchGlassBorder)
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.nyxtap_logo_exact),
                        contentDescription = "NyxTap Overlay Icon (Tap to expand/shrink)",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .rotate(mainIconRotation)
                    )
                }
            }

            // Expanded Sub-Action Buttons (Vertical Originating Stack)
            if (expansionFraction > 0.01f) {
                // Action 1: Mail
                OverlayActionButton(
                    icon = Icons.Default.Email,
                    contentDescription = "Mail",
                    tint = StitchTextPrimary,
                    bgColor = StitchGlassCardElevated,
                    borderColor = StitchGlassBorder,
                    index = 0,
                    progress = calculateStaggeredProgress(expansionFraction, 0),
                    onClick = {
                        val activeEmail = when (mailboxStatus) {
                            is MailboxStatus.Active -> mailboxStatus.email
                            is MailboxStatus.Monitoring -> mailboxStatus.email
                            is MailboxStatus.Checking -> mailboxStatus.email
                            else -> null
                        }
                        if (activeEmail != null) {
                            onCopyEmail(activeEmail)
                            showFeedback(
                                OverlayFeedbackCard(
                                    title = activeEmail,
                                    subtitle = "Copied to clipboard",
                                    isSuccess = true
                                )
                            )
                        } else {
                            onGenerateMail()
                            showFeedback(
                                OverlayFeedbackCard(
                                    title = "Creating inbox...",
                                    subtitle = "Generating disposable email",
                                    isSuccess = true
                                )
                            )
                        }
                    },
                    testTag = "overlay_action_mail"
                )

                // Action 2: Name Generator
                OverlayActionButton(
                    icon = Icons.Default.Person,
                    contentDescription = "Random Name",
                    tint = StitchTextPrimary,
                    bgColor = StitchGlassCardElevated,
                    borderColor = StitchGlassBorder,
                    index = 1,
                    progress = calculateStaggeredProgress(expansionFraction, 1),
                    onClick = {
                        val generated = onGenerateName?.invoke() ?: "Liam Anderson"
                        showFeedback(
                            OverlayFeedbackCard(
                                title = generated,
                                subtitle = "Copied to clipboard",
                                isSuccess = true
                            )
                        )
                    },
                    testTag = "overlay_action_name"
                )

                // Action 3: 2FA TOTP Generator from Clipboard
                OverlayActionButton(
                    icon = Icons.Default.Lock,
                    contentDescription = "2FA TOTP",
                    tint = StitchGreen,
                    bgColor = StitchGlassCardElevated,
                    borderColor = StitchGlassBorder,
                    index = 2,
                    progress = calculateStaggeredProgress(expansionFraction, 2),
                    onClick = {
                        val result = onGenerateTwoFaFromClipboard()
                        if (result.isSuccess) {
                            val totp = result.getOrNull()
                            val code = totp?.code ?: "TOTP Code"
                            showFeedback(
                                OverlayFeedbackCard(
                                    title = code,
                                    subtitle = "Copied",
                                    isSuccess = true,
                                    isMono = true
                                )
                            )
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message ?: "Invalid TOTP secret"
                            showFeedback(
                                OverlayFeedbackCard(
                                    title = errorMsg,
                                    subtitle = "2FA error",
                                    isSuccess = false
                                )
                            )
                        }
                    },
                    testTag = "overlay_action_2fa"
                )

                // Action 4: Close / Shrink Overlay
                OverlayActionButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close Overlay (Long press to close, tap to shrink)",
                    tint = StitchRed,
                    bgColor = StitchGlassCardElevated,
                    borderColor = StitchRed.copy(alpha = 0.4f),
                    index = 3,
                    progress = calculateStaggeredProgress(expansionFraction, 3),
                    onClick = {
                        // Tapping shrinks/hides the menu
                        isExpanded = false
                        showFeedback(
                            OverlayFeedbackCard(
                                title = "Hold to Close",
                                subtitle = "Long press X to close overlay",
                                isSuccess = false
                            )
                        )
                    },
                    onLongClick = {
                        // Long pressing closes overlay completely
                        onCloseOverlay()
                    },
                    testTag = "overlay_action_close"
                )
            }
        }

        // Compact Floating Glass Result Card beside Icon
        AnimatedVisibility(
            visible = resultCard != null,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.97f, animationSpec = tween(180)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.97f, animationSpec = tween(160))
        ) {
            resultCard?.let { card ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 190.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.6f))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { resultCard = null }
                        .testTag("overlay_result_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = StitchGlassCardElevated,
                    border = BorderStroke(1.dp, if (card.isSuccess) StitchGlassBorder else StitchRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = if (card.isSuccess) StitchGreenBadgeBg else StitchRedBadgeBg
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (card.isSuccess) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (card.isSuccess) StitchGreen else StitchRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = card.title,
                                style = if (card.isMono) {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        fontSize = 15.sp
                                    )
                                } else {
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                },
                                color = StitchTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = card.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (card.isSuccess) StitchGreen else StitchRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    bgColor: Color,
    borderColor: Color,
    index: Int,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    testTag: String
) {
    val scale = 0.75f + 0.25f * progress
    val alpha = progress
    val translationY = -((index + 1) * 6f) * (1f - progress)

    Surface(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
                this.translationY = translationY
            }
            .shadow(
                elevation = (4 * progress).dp,
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(CircleShape)
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
            .testTag(testTag),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun calculateStaggeredProgress(masterProgress: Float, index: Int): Float {
    val startFraction = (index * 0.10f).coerceAtMost(0.35f)
    return ((masterProgress - startFraction) / (1f - startFraction)).coerceIn(0f, 1f)
}
