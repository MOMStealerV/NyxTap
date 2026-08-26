package com.example.ui.twofa

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TempMail2FAApp
import com.example.ui.components.AppCard
import com.example.ui.components.CodeDisplayCard
import com.example.ui.theme.StitchGlassBorder
import com.example.ui.theme.StitchGlassBorderSubtle
import com.example.ui.theme.StitchGlassCardElevated
import com.example.ui.theme.StitchPillDark
import com.example.ui.theme.StitchPillDarkBorder
import com.example.ui.theme.StitchPillDarkOn
import com.example.ui.theme.StitchPillWhite
import com.example.ui.theme.StitchPillWhiteOn
import com.example.ui.theme.StitchRed
import com.example.ui.theme.StitchRedBadgeBg
import com.example.ui.theme.StitchRedBadgeText
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary

@Composable
fun TwoFaScreen(
    modifier: Modifier = Modifier
) {
    val app = TempMail2FAApp.instance
    val currentTotp by app.twoFaRepository.currentTotp.collectAsState()
    val focusManager = LocalFocusManager.current

    var manualSecretInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedToast by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Header
        Column {
            Text(
                text = "2FA Authenticator",
                style = MaterialTheme.typography.displaySmall,
                color = StitchTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Instant RFC 6238 TOTP code generator",
                style = MaterialTheme.typography.bodyMedium,
                color = StitchTextSecondary
            )
        }

        // Active 2FA Code Display Card
        if (currentTotp != null) {
            CodeDisplayCard(
                code = currentTotp!!.code,
                label = "Active 2FA code",
                remainingSeconds = currentTotp!!.remainingSeconds,
                progress = currentTotp!!.progress,
                copied = true,
                onCopyClick = {
                    app.clipboardManager.copyTotp(currentTotp!!.code)
                    copiedToast = "✓ 2FA code copied"
                },
                modifier = Modifier.testTag("totp_code_display_card")
            )
        }

        // Error message banner
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = StitchRedBadgeBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, StitchRed.copy(alpha = 0.4f))
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = StitchRedBadgeText,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Primary Action: Read from Clipboard & Generate
        Button(
            onClick = {
                val result = app.twoFaRepository.generateFromClipboard()
                if (result.isSuccess) {
                    errorMessage = null
                    copiedToast = "✓ 2FA code generated & copied"
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Invalid 2FA secret in clipboard"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("read_clipboard_2fa_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StitchPillWhite,
                contentColor = StitchPillWhiteOn
            )
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Read clipboard & generate 2FA",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Manual Secret Entry Card
        AppCard {
            Text(
                text = "Manual secret input",
                style = MaterialTheme.typography.titleSmall,
                color = StitchTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = manualSecretInput,
                onValueChange = {
                    manualSecretInput = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        text = "e.g. JBSWY3DPEHPK3PXP or otpauth://...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StitchTextSecondary.copy(alpha = 0.6f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = StitchTextPrimary),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (manualSecretInput.isNotEmpty()) {
                        IconButton(onClick = { manualSecretInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = StitchTextSecondary)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StitchGlassBorder,
                    unfocusedBorderColor = StitchGlassBorderSubtle,
                    focusedContainerColor = StitchGlassCardElevated,
                    unfocusedContainerColor = StitchGlassCardElevated
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (manualSecretInput.isNotBlank()) {
                        val result = app.twoFaRepository.generateFromSecret(manualSecretInput)
                        if (result.isSuccess) {
                            errorMessage = null
                            copiedToast = "✓ 2FA code generated & copied"
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Invalid secret key"
                        }
                    }
                })
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        focusManager.clearFocus()
                        if (manualSecretInput.isNotBlank()) {
                            val result = app.twoFaRepository.generateFromSecret(manualSecretInput)
                            if (result.isSuccess) {
                                errorMessage = null
                                copiedToast = "✓ 2FA code generated & copied"
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Invalid secret key"
                            }
                        } else {
                            errorMessage = "Please enter a Base32 secret key"
                        }
                    },
                color = StitchPillDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Generate from input",
                        style = MaterialTheme.typography.labelLarge,
                        color = StitchPillDarkOn
                    )
                }
            }
        }
    }
}
