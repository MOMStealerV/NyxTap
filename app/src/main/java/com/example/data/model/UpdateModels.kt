package com.example.data.model

import java.io.File
import java.util.regex.Pattern

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long = 0L,
    val expectedSha256: String? = null,
    val isPrerelease: Boolean = false,
    val publishedAt: String = ""
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val checkedAt: Long, val currentVersion: String) : UpdateState()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
    data class Downloading(
        val updateInfo: UpdateInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class Verifying(val updateInfo: UpdateInfo) : UpdateState()
    data class ReadyToInstall(val updateInfo: UpdateInfo, val apkFile: File) : UpdateState()
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val failedUpdateInfo: UpdateInfo? = null
    ) : UpdateState()
}

object VersionParser {

    private val EXPLICIT_CODE_REGEX = Pattern.compile(
        """(?:versionCode|version_code|code)[\s:=]+(\d+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val SEMVER_REGEX = Pattern.compile(
        """v?(\d+)\.(\d+)(?:\.(\d+))?""",
        Pattern.CASE_INSENSITIVE
    )

    private val DIGITS_ONLY_REGEX = Pattern.compile("""^v?(\d{3,6})$""", Pattern.CASE_INSENSITIVE)

    /**
     * Parse integer versionCode from release metadata (tag, title, body).
     * Compares numerically with installed versionCode (e.g. 302).
     */
    fun parseVersionCode(tag: String, title: String? = null, body: String? = null): Int {
        // 1. Look for explicit versionCode in release notes body or title
        if (!body.isNullOrBlank()) {
            val matcher = EXPLICIT_CODE_REGEX.matcher(body)
            if (matcher.find()) {
                matcher.group(1)?.toIntOrNull()?.let { return it }
            }
        }
        if (!title.isNullOrBlank()) {
            val matcher = EXPLICIT_CODE_REGEX.matcher(title)
            if (matcher.find()) {
                matcher.group(1)?.toIntOrNull()?.let { return it }
            }
        }

        // 2. Look for semantic version string in tag (e.g., "v30.3", "v31.0") or title ("NyxTap v30.3")
        val tagMatcher = SEMVER_REGEX.matcher(tag)
        if (tagMatcher.find()) {
            val major = tagMatcher.group(1)?.toIntOrNull() ?: 0
            val minor = tagMatcher.group(2)?.toIntOrNull() ?: 0
            val patch = tagMatcher.group(3)?.toIntOrNull()

            return if (patch != null) {
                major * 100 + minor * 10 + patch
            } else {
                if (minor < 10) {
                    major * 10 + minor // e.g. 30.2 -> 302, 30.3 -> 303, 31.0 -> 310
                } else {
                    major * 100 + minor
                }
            }
        }

        if (!title.isNullOrBlank()) {
            val titleMatcher = SEMVER_REGEX.matcher(title)
            if (titleMatcher.find()) {
                val major = titleMatcher.group(1)?.toIntOrNull() ?: 0
                val minor = titleMatcher.group(2)?.toIntOrNull() ?: 0
                val patch = titleMatcher.group(3)?.toIntOrNull()

                return if (patch != null) {
                    major * 100 + minor * 10 + patch
                } else {
                    if (minor < 10) major * 10 + minor else major * 100 + minor
                }
            }
        }

        // 3. Digits only in tag (e.g. "303")
        val digitsMatcher = DIGITS_ONLY_REGEX.matcher(tag.trim())
        if (digitsMatcher.matches()) {
            digitsMatcher.group(1)?.toIntOrNull()?.let { return it }
        }

        return 0
    }

    /**
     * Extracts or formats a clean versionName (e.g. "v30.3").
     */
    fun extractVersionName(tag: String, title: String? = null): String {
        val tagMatcher = SEMVER_REGEX.matcher(tag)
        if (tagMatcher.find()) {
            val match = tagMatcher.group(0) ?: tag
            return if (match.startsWith("v", ignoreCase = true)) match else "v$match"
        }
        if (!title.isNullOrBlank()) {
            val titleMatcher = SEMVER_REGEX.matcher(title)
            if (titleMatcher.find()) {
                val match = titleMatcher.group(0) ?: title
                return if (match.startsWith("v", ignoreCase = true)) match else "v$match"
            }
        }
        return if (tag.startsWith("v", ignoreCase = true)) tag else "v$tag"
    }

    /**
     * Extracts SHA-256 hex string from release notes or checksum files if present.
     */
    fun extractSha256(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val shaRegex = Pattern.compile("""(?:sha-?256[\s:=]+)?([a-fA-F0-9]{64})""", Pattern.CASE_INSENSITIVE)
        val matcher = shaRegex.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
}
