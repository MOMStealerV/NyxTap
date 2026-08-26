package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NyxTap — Stitch Design Palette
 * Pure Black + Dark Glass + White / Soft Gray + Minimal Geometric UI
 */

// Core Canvas & Glass Surfaces
val StitchBlack = Color(0xFF000000)
val StitchCanvasDark = Color(0xFF000000)
val StitchGlassSurface = Color(0xFF0E0E13)
val StitchGlassCard = Color(0xFF13131A)
val StitchGlassCardElevated = Color(0xFF191922)
val StitchGlassCardSubtle = Color(0xFF0B0B0F)

// Glass Borders & Outlines
val StitchGlassBorder = Color(0x26FFFFFF)          // ~15% white border
val StitchGlassBorderSubtle = Color(0x14FFFFFF)    // ~8% white border
val StitchGlassBorderHighlight = Color(0x40FFFFFF) // ~25% white border

// Typography & Content Colors
val StitchTextPrimary = Color(0xFFFFFFFF)
val StitchTextSecondary = Color(0xFF8E8E93)
val StitchTextMuted = Color(0xFF636366)
val StitchTextSubtle = Color(0xFF48484A)

// Buttons & Pills
val StitchPillWhite = Color(0xFFFFFFFF)
val StitchPillWhiteOn = Color(0xFF000000)
val StitchPillDark = Color(0xFF1A1A22)
val StitchPillDarkBorder = Color(0x26FFFFFF)
val StitchPillDarkOn = Color(0xFFFFFFFF)

// Subtle Minimal Status Accents
val StitchGreen = Color(0xFF34C759)
val StitchGreenBadgeBg = Color(0x1F34C759)
val StitchGreenBadgeText = Color(0xFF4ADE80)

val StitchAmber = Color(0xFFFF9F0A)
val StitchAmberBadgeBg = Color(0x1FFF9F0A)
val StitchAmberBadgeText = Color(0xFFFFB340)

val StitchRed = Color(0xFFFF453A)
val StitchRedBadgeBg = Color(0x1FFF453A)
val StitchRedBadgeText = Color(0xFFFF6961)

val StitchBlue = Color(0xFF0A84FF)
val StitchBlueBadgeBg = Color(0x1F0A84FF)
val StitchBlueBadgeText = Color(0xFF64D2FF)

// Backward compatible alias tokens used throughout the app
val PureBlack = StitchBlack
val DarkGlassSurface = StitchGlassSurface
val DarkGlassCard = StitchGlassCard
val SlateDarkPill = StitchPillDark
val SlateDarkPillOn = StitchPillDarkOn
val SlateBorderLight = StitchGlassBorder
val SlateTextMutedLight = StitchTextSecondary

val StatusGreen = StitchGreen
val StatusGreenBadgeBg = StitchGreenBadgeBg
val StatusGreenBadgeText = StitchGreenBadgeText
val SuccessGreen = StitchGreen

val WarningAmber = StitchAmber
val ErrorRed = StitchRed

val ProfessionalBlue = StitchTextPrimary
val ProfessionalBlueContainerLight = StitchGlassCardElevated
val ProfessionalBlueBadgeLight = StitchGlassBorderSubtle
val ProfessionalBlueTextLight = StitchTextPrimary

val ProfessionalIndigo = StitchTextPrimary
val ProfessionalIndigoContainerLight = StitchGlassCardElevated
val ProfessionalIndigoBadgeLight = StitchGlassBorderSubtle
val ProfessionalIndigoTextLight = StitchTextPrimary

val OtpHighlight = StitchTextPrimary
