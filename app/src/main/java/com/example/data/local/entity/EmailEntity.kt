package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    val mailbox: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String?,
    val timestamp: Long,
    val extractedOtp: String?,
    val isCopied: Boolean
)
