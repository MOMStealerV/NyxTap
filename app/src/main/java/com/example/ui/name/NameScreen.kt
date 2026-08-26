package com.example.ui.name

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TempMail2FAApp
import com.example.ui.components.AppCard
import com.example.ui.components.SectionTitle
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
import com.example.ui.theme.StitchPillWhite
import com.example.ui.theme.StitchPillWhiteOn
import com.example.ui.theme.StitchTextMuted
import com.example.ui.theme.StitchTextPrimary
import com.example.ui.theme.StitchTextSecondary
import com.example.util.NameGenerationMode

@Composable
fun NameScreen(
    modifier: Modifier = Modifier
) {
    val app = TempMail2FAApp.instance
    val currentName by app.nameRepository.currentName.collectAsState()
    val selectedMode by app.nameRepository.selectedMode.collectAsState()

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
                text = "Name Generator",
                style = MaterialTheme.typography.displaySmall,
                color = StitchTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Realistic random identity generator",
                style = MaterialTheme.typography.bodyMedium,
                color = StitchTextSecondary
            )
        }

        // Mode Selector Card
        AppCard {
            Text(
                text = "Format mode",
                style = MaterialTheme.typography.titleSmall,
                color = StitchTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            val modes = listOf(
                NameGenerationMode.FULL_NAME to "Full Name",
                NameGenerationMode.FIRST_NAME_ONLY to "First Only",
                NameGenerationMode.LAST_NAME_ONLY to "Last Only",
                NameGenerationMode.FIRST_AND_LAST_SEPARATE to "Separate"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { (mode, label) ->
                    val isSelected = selectedMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { app.nameRepository.setMode(mode) }
                            .testTag("name_mode_${mode.name.lowercase()}"),
                        color = if (isSelected) StitchPillWhite else StitchGlassCardElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) StitchTextPrimary else StitchGlassBorderSubtle
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) StitchPillWhiteOn else StitchTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Active / Generated Name Card
        AppCard(
            modifier = Modifier.testTag("generated_name_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generated Identity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StitchTextSecondary
                )

                Surface(
                    color = if (currentName != null) StitchGreenBadgeBg else StitchGlassCardElevated,
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (currentName != null) StitchGreen.copy(alpha = 0.3f) else StitchGlassBorderSubtle)
                ) {
                    Text(
                        text = if (currentName != null) "Generated" else "Ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (currentName != null) StitchGreenBadgeText else StitchTextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val displayedFullName = currentName?.fullName ?: "Liam Anderson"
            val displayedFirstName = currentName?.firstName ?: "Liam"
            val displayedLastName = currentName?.lastName ?: "Anderson"

            Text(
                text = when (selectedMode) {
                    NameGenerationMode.FIRST_NAME_ONLY -> displayedFirstName
                    NameGenerationMode.LAST_NAME_ONLY -> displayedLastName
                    NameGenerationMode.FIRST_AND_LAST_SEPARATE -> "$displayedFirstName\n$displayedLastName"
                    NameGenerationMode.FULL_NAME -> displayedFullName
                },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = StitchTextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = StitchGlassBorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Action Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy Full Name
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            app.clipboardManager.copyPlainText("Generated Name", displayedFullName)
                            copiedToast = "✓ Full name copied"
                        }
                        .testTag("copy_full_name_button"),
                    color = StitchPillDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = StitchPillDarkOn,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy full",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            color = StitchPillDarkOn
                        )
                    }
                }

                // Copy First
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            app.clipboardManager.copyPlainText("First Name", displayedFirstName)
                            copiedToast = "✓ First name copied"
                        },
                    color = StitchPillDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "First",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = StitchPillDarkOn,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                // Copy Last
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable {
                            app.clipboardManager.copyPlainText("Last Name", displayedLastName)
                            copiedToast = "✓ Last name copied"
                        },
                    color = StitchPillDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StitchPillDarkBorder),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Last",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = StitchPillDarkOn,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Primary Action: Generate
        Button(
            onClick = {
                val generated = app.nameRepository.generateAndCopy()
                copiedToast = "✓ ${generated.copiedText} copied"
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("generate_name_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StitchPillWhite,
                contentColor = StitchPillWhiteOn
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generate name",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
