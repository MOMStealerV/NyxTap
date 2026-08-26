package com.example.service

import androidx.compose.ui.graphics.vector.ImageVector

enum class OverlayActionType {
    COLLAPSE,
    MAIL,
    TWO_FA,
    CLOSE,
    SETTINGS
}

data class OverlayActionItem(
    val type: OverlayActionType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isPrimary: Boolean = false
)
