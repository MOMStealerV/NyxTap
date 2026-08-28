package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.UpdateInfo
import com.example.data.model.UpdateState
import com.example.data.model.VersionParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val checkMutex = Mutex()
    private var downloadJob: Job? = null

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    companion object {
        const val GITHUB_OWNER = "MOMStealerV"
        const val GITHUB_REPO = "NyxTap"
        const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"
        private const val MIN_CHECK_INTERVAL_MS = 60 * 1000L // 1 minute throttle for auto-check
    }

    /**
     * Check GitHub for updates asynchronously.
     * @param isManual If true, bypasses throttle and forces a fresh query.
     */
    suspend fun checkForUpdates(isManual: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        if (!checkMutex.tryLock()) {
            // Check already in progress, avoid duplicate concurrent network calls
            return@withContext Result.success(null)
        }

        try {
            val lastChecked = settingsRepository.lastUpdateCheckTimestamp.value
            val now = System.currentTimeMillis()

            if (!isManual && (now - lastChecked < MIN_CHECK_INTERVAL_MS) && _updateState.value is UpdateState.UpdateAvailable) {
                // Return cached update state without polling GitHub excessively
                val state = _updateState.value as UpdateState.UpdateAvailable
                return@withContext Result.success(state.updateInfo)
            }

            _updateState.value = UpdateState.Checking

            val includeBeta = settingsRepository.includeBetaUpdates.value

            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NyxTap-Android-App/$currentVersionName")
                .get()
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                _updateState.value = UpdateState.Error("Network error: ${e.localizedMessage ?: "Unable to connect"}", canRetry = true)
                return@withContext Result.failure(e)
            }

            response.use { res ->
                val code = res.code
                if (code == 404 || code == 410) {
                    settingsRepository.setLastUpdateCheckTimestamp(now)
                    _updateState.value = UpdateState.UpToDate(now, currentVersionName)
                    return@withContext Result.success(null)
                }

                if (code == 403 || code == 429) {
                    val msg = "GitHub rate limit reached. Please try again later."
                    _updateState.value = UpdateState.Error(msg, canRetry = false)
                    return@withContext Result.failure(IOException(msg))
                }

                if (!res.isSuccessful) {
                    val msg = "Server returned HTTP $code"
                    _updateState.value = UpdateState.Error(msg, canRetry = true)
                    return@withContext Result.failure(IOException(msg))
                }

                val bodyString = res.body?.string() ?: ""
                val releasesArray = try {
                    JSONArray(bodyString)
                } catch (e: Exception) {
                    // Try parsing as single object if GitHub returned latest release object
                    try {
                        JSONArray().put(JSONObject(bodyString))
                    } catch (e2: Exception) {
                        _updateState.value = UpdateState.Error("Invalid release data received", canRetry = true)
                        return@withContext Result.failure(e2)
                    }
                }

                var newestUpdate: UpdateInfo? = null

                for (i in 0 until releasesArray.length()) {
                    val releaseObj = releasesArray.getJSONObject(i)
                    val isDraft = releaseObj.optBoolean("draft", false)
                    if (isDraft) continue

                    val isPrerelease = releaseObj.optBoolean("prerelease", false)
                    if (isPrerelease && !includeBeta) continue

                    val tagName = releaseObj.optString("tag_name", "")
                    val releaseTitle = releaseObj.optString("name", tagName)
                    val releaseBody = releaseObj.optString("body", "")
                    val publishedAt = releaseObj.optString("published_at", "")

                    val releaseVersionCode = VersionParser.parseVersionCode(
                        tag = tagName,
                        title = releaseTitle,
                        body = releaseBody
                    )

                    // Strictly numerical comparison: latestVersionCode > currentVersionCode (e.g. 303 > 302)
                    if (releaseVersionCode > currentVersionCode) {
                        val assetsArray = releaseObj.optJSONArray("assets") ?: JSONArray()
                        var apkUrl: String? = null
                        var apkName: String? = null
                        var apkSize: Long = 0L
                        var expectedSha: String? = VersionParser.extractSha256(releaseBody)

                        var bestApkScore = -1
                        for (j in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(j)
                            val name = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            val size = asset.optLong("size", 0L)

                            if (name.endsWith(".apk", ignoreCase = true)) {
                                var score = 1
                                if (name.startsWith("NyxTap", ignoreCase = true)) score += 10
                                if (name.contains("release", ignoreCase = true)) score += 5
                                if (name.contains("universal", ignoreCase = true)) score += 2
                                if (name.contains("debug", ignoreCase = true)) score -= 10

                                if (score > bestApkScore) {
                                    bestApkScore = score
                                    apkUrl = downloadUrl
                                    apkName = name
                                    apkSize = size
                                }
                            }

                            // Check if asset is a checksum file
                            if (expectedSha == null && (name.endsWith(".sha256", ignoreCase = true) || name.contains("checksum", ignoreCase = true))) {
                                try {
                                    val checkReq = Request.Builder().url(downloadUrl).get().build()
                                    val checkRes = client.newCall(checkReq).execute()
                                    checkRes.use {
                                        if (it.isSuccessful) {
                                            expectedSha = VersionParser.extractSha256(it.body?.string())
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (apkUrl != null && apkName != null) {
                            val versionName = VersionParser.extractVersionName(tagName, releaseTitle)
                            val candidate = UpdateInfo(
                                isUpdateAvailable = true,
                                currentVersionCode = currentVersionCode,
                                currentVersionName = currentVersionName,
                                latestVersionCode = releaseVersionCode,
                                latestVersionName = versionName,
                                releaseTag = tagName,
                                releaseTitle = releaseTitle.ifBlank { "NyxTap $versionName" },
                                releaseNotes = releaseBody.ifBlank { "Performance improvements and bug fixes." },
                                apkDownloadUrl = apkUrl,
                                apkFileName = apkName,
                                apkSizeBytes = apkSize,
                                expectedSha256 = expectedSha,
                                isPrerelease = isPrerelease,
                                publishedAt = publishedAt
                            )

                            if (newestUpdate == null || candidate.latestVersionCode > newestUpdate.latestVersionCode) {
                                newestUpdate = candidate
                            }
                        }
                    }
                }

                settingsRepository.setLastUpdateCheckTimestamp(now)

                if (newestUpdate != null) {
                    _updateState.value = UpdateState.UpdateAvailable(newestUpdate)
                    Result.success(newestUpdate)
                } else {
                    _updateState.value = UpdateState.UpToDate(now, currentVersionName)
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Error checking for updates: ${e.localizedMessage ?: "Unknown"}", canRetry = true)
            Result.failure(e)
        } finally {
            checkMutex.unlock()
        }
    }

    /**
     * Start downloading the APK asset with progress reporting, verify its checksum, and prepare installation.
     */
    fun startDownload(
        scope: CoroutineScope,
        updateInfo: UpdateInfo,
        onPermissionNeeded: () -> Unit = {}
    ) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(
                    updateInfo = updateInfo,
                    progressPercent = 0,
                    bytesDownloaded = 0L,
                    totalBytes = updateInfo.apkSizeBytes
                )

                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updatesDir, "NyxTap-${updateInfo.latestVersionName.replace(" ", "_")}.apk")

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val request = Request.Builder()
                    .url(updateInfo.apkDownloadUrl)
                    .header("User-Agent", "NyxTap-Android-App/$currentVersionName")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error(
                        message = "Failed to download APK: HTTP ${response.code}",
                        canRetry = true,
                        failedUpdateInfo = updateInfo
                    )
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    _updateState.value = UpdateState.Error(
                        message = "Empty response received from server",
                        canRetry = true,
                        failedUpdateInfo = updateInfo
                    )
                    return@launch
                }

                val contentLength = if (body.contentLength() > 0) body.contentLength() else updateInfo.apkSizeBytes
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressEmit = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmit > 100 || totalBytesRead == contentLength) {
                                lastProgressEmit = now
                                val percent = if (contentLength > 0) {
                                    ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                _updateState.value = UpdateState.Downloading(
                                    updateInfo = updateInfo,
                                    progressPercent = percent,
                                    bytesDownloaded = totalBytesRead,
                                    totalBytes = contentLength
                                )
                            }
                        }
                    }
                }

                // Verify downloaded file
                _updateState.value = UpdateState.Verifying(updateInfo)

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    _updateState.value = UpdateState.Error(
                        message = "Downloaded APK file is corrupt or empty",
                        canRetry = true,
                        failedUpdateInfo = updateInfo
                    )
                    return@launch
                }

                // SHA-256 checksum verification if available
                if (!updateInfo.expectedSha256.isNullOrBlank()) {
                    val computedSha = calculateFileSha256(targetFile)
                    if (!computedSha.equals(updateInfo.expectedSha256, ignoreCase = true)) {
                        targetFile.delete()
                        _updateState.value = UpdateState.Error(
                            message = "Update verification failed: SHA-256 checksum mismatch",
                            canRetry = true,
                            failedUpdateInfo = updateInfo
                        )
                        return@launch
                    }
                }

                // Verify that downloaded package is a valid Android APK matching installed app
                val packageValidationError = validateDownloadedApk(targetFile, updateInfo.latestVersionCode)
                if (packageValidationError != null) {
                    targetFile.delete()
                    _updateState.value = UpdateState.Error(
                        message = packageValidationError,
                        canRetry = false,
                        failedUpdateInfo = updateInfo
                    )
                    return@launch
                }

                _updateState.value = UpdateState.ReadyToInstall(updateInfo, targetFile)

                // Trigger package installation
                withContext(Dispatchers.Main) {
                    launchPackageInstaller(targetFile, onPermissionNeeded)
                }

            } catch (e: CancellationException) {
                _updateState.value = UpdateState.Idle
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    message = "Download failed: ${e.localizedMessage ?: "Unknown error"}",
                    canRetry = true,
                    failedUpdateInfo = updateInfo
                )
            }
        }
    }

    /**
     * Launch the Android Package Installer safely with FileProvider.
     */
    fun launchPackageInstaller(apkFile: File, onPermissionNeeded: () -> Unit = {}): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    onPermissionNeeded()
                    return false
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(
                message = "Could not open installer: ${e.localizedMessage ?: "System error"}",
                canRetry = false
            )
            false
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _updateState.value = UpdateState.Idle
    }

    fun dismissUpdate() {
        if (_updateState.value !is UpdateState.Downloading) {
            _updateState.value = UpdateState.Idle
        }
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Pre-validates that the downloaded file is a valid Android APK for this application,
     * not a downgrade, and signed with a certificate compatible with the currently running app.
     */
    fun validateDownloadedApk(apkFile: File, expectedVersionCode: Int): String? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            } ?: return "Downloaded APK is invalid or corrupt (cannot parse Android package)."

            // 1. Verify applicationId / package name matches
            if (archiveInfo.packageName != context.packageName) {
                return "Package name mismatch: APK is for '${archiveInfo.packageName}', but installed app is '${context.packageName}'."
            }

            // 2. Verify versionCode is not a downgrade
            val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode
            }

            val currentVersionCode = try {
                val curPkg = pm.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    curPkg.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    curPkg.versionCode
                }
            } catch (_: Exception) {
                0
            }

            if (apkVersionCode < currentVersionCode) {
                return "Cannot install downgrade: APK version ($apkVersionCode) is older than installed version ($currentVersionCode)."
            }

            // 3. Verify signing certificate matches the running app to prevent installation conflicts
            val certificatesMatch = try {
                val currentPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, flags)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val apkCerts = archiveInfo.signingInfo?.signingCertificateHistory
                        ?: archiveInfo.signingInfo?.apkContentsSigners
                    val currentCerts = currentPkg.signingInfo?.signingCertificateHistory
                        ?: currentPkg.signingInfo?.apkContentsSigners

                    if (apkCerts != null && currentCerts != null && apkCerts.isNotEmpty() && currentCerts.isNotEmpty()) {
                        apkCerts.any { apkCert ->
                            currentCerts.any { curCert ->
                                apkCert.toByteArray().contentEquals(curCert.toByteArray())
                            }
                        }
                    } else {
                        true // Fallback to OS installer if signingInfo is unavailable in userspace
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val apkSigs = archiveInfo.signatures
                    @Suppress("DEPRECATION")
                    val currentSigs = currentPkg.signatures

                    if (apkSigs != null && currentSigs != null && apkSigs.isNotEmpty() && currentSigs.isNotEmpty()) {
                        apkSigs.any { apkSig ->
                            currentSigs.any { curSig ->
                                apkSig.toByteArray().contentEquals(curSig.toByteArray())
                            }
                        }
                    } else {
                        true
                    }
                }
            } catch (_: Exception) {
                true // Allow installer to verify if reflection/inspection fails
            }

            if (!certificatesMatch) {
                return "Signature mismatch: APK is signed with a conflicting key. Update refused to prevent installation failure."
            }

            null // Validation successful
        } catch (e: Exception) {
            "Failed to validate APK package: ${e.localizedMessage}"
        }
    }
}
