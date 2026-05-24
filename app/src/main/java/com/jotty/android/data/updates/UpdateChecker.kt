package com.jotty.android.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.ApkInstallHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Which GitHub release track to use for "Check for updates". */
enum class UpdateChannel {
    Stable,
    Dev,
}

fun parseUpdateChannel(value: String?): UpdateChannel =
    when (value?.lowercase()?.trim()) {
        "dev" -> UpdateChannel.Dev
        else -> UpdateChannel.Stable
    }

/**
 * Result of checking for updates.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String? = null,
    ) : UpdateCheckResult()

    data object UpToDate : UpdateCheckResult()

    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Result of download-and-install attempt (for UI to show fallback e.g. "Open in browser").
 */
sealed class InstallResult {
    data object Started : InstallResult()

    data class Failed(val userMessage: String) : InstallResult()
}

object UpdateChecker {
    private const val GITHUB_API_BASE = "https://api.github.com/"
    private const val TAG = "UpdateChecker"

    private val userAgent: String
        get() = "Jotty-Android/${BuildConfig.VERSION_NAME ?: "0"}"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes; errors are not cached

    private data class CacheEntry(val at: Long, val result: UpdateCheckResult)

    private val cache = ConcurrentHashMap<UpdateChannel, CacheEntry>()

    private val githubClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", userAgent)
                        .build(),
                )
            }
            .build()

    private val downloadClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("User-Agent", userAgent)
                        .build(),
                )
            }
            .build()

    private val githubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(githubClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    /**
     * Fetches the chosen release from GitHub and compares with the installed build.
     * Success results (UpToDate, UpdateAvailable) are cached for 5 minutes per [channel]; errors are not cached.
     * [context] is used to resolve user-facing error strings.
     */
    suspend fun checkForUpdate(
        context: Context,
        channel: UpdateChannel = UpdateChannel.Stable,
    ): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cache[channel]?.let { entry ->
                if (now - entry.at < CACHE_TTL_MS && entry.result !is UpdateCheckResult.Error) {
                    return@withContext entry.result
                }
            }
            try {
                val result =
                    when (channel) {
                        UpdateChannel.Stable -> checkStableRelease(context)
                        UpdateChannel.Dev -> checkDevRelease(context)
                    }
                cache[channel] = CacheEntry(now, result)
                result
            } catch (e: Exception) {
                AppLog.e(TAG, "Check for update failed", e)
                UpdateCheckResult.Error(ApiErrorHelper.userMessage(context, e))
            }
        }

    private suspend fun checkStableRelease(context: Context): UpdateCheckResult {
        val release = githubApi.getLatestRelease()
        val latestVersion = release.tagName.removePrefix("v").trim()
        val apkAsset =
            preferredApkAsset(release.assets)
                ?: return UpdateCheckResult.Error(context.getString(R.string.no_apk_in_release))

        val current = BuildConfig.VERSION_NAME?.trim() ?: "0.0.0"
        val baseCurrent = baseVersionNameWithoutDevSuffix(current)
        return if (!isNewerVersion(latestVersion, baseCurrent)) {
            UpdateCheckResult.UpToDate
        } else {
            UpdateCheckResult.UpdateAvailable(
                versionName = latestVersion,
                downloadUrl = apkAsset.browserDownloadUrl,
                releaseNotes = release.body?.trim()?.takeIf { it.isNotBlank() },
            )
        }
    }

    private suspend fun checkDevRelease(context: Context): UpdateCheckResult {
        val release = githubApi.getDevLatestRelease()
        val apkAsset =
            preferredApkAsset(release.assets)
                ?: return UpdateCheckResult.Error(context.getString(R.string.no_apk_in_release))

        val remoteCommit =
            commitFromDevReleaseBody(release.body)
                ?: return UpdateCheckResult.Error(context.getString(R.string.update_check_dev_parse_error))

        val current = BuildConfig.VERSION_NAME?.trim() ?: "0.0.0"
        if (localDevBuildMatchesRemote(current, remoteCommit)) {
            return UpdateCheckResult.UpToDate
        }
        val short = remoteCommit.take(7)
        return UpdateCheckResult.UpdateAvailable(
            versionName = context.getString(R.string.update_version_dev_format, short),
            downloadUrl = apkAsset.browserDownloadUrl,
            releaseNotes = release.body?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /** `1.3.0-dev+abcdef0` → `1.3.0` for stable-vs-dev semver checks. */
    internal fun baseVersionNameWithoutDevSuffix(versionName: String): String {
        val idx = versionName.indexOf("-dev+")
        return if (idx > 0) versionName.substring(0, idx).trim() else versionName.trim()
    }

    internal fun shortShaFromDevVersionName(versionName: String): String? {
        return Regex("""-dev\+([a-fA-F0-9]+)$""").find(versionName)?.groupValues?.get(1)?.lowercase()
    }

    internal fun commitFromDevReleaseBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val m = Regex("""(?m)^Commit:\s*([a-fA-F0-9]{7,40})\b""").find(body) ?: return null
        return m.groupValues[1].lowercase()
    }

    internal fun localDevBuildMatchesRemote(
        versionName: String,
        remoteCommitFull: String,
    ): Boolean {
        val localSha = shortShaFromDevVersionName(versionName) ?: return false
        val remote = remoteCommitFull.lowercase()
        return remote.startsWith(localSha) || localSha.startsWith(remote.take(localSha.length))
    }

    /**
     * Returns true if [newVersion] is strictly greater than [currentVersion] (e.g. "1.2.0" > "1.1.2").
     */
    fun isNewerVersion(
        newVersion: String,
        currentVersion: String,
    ): Boolean {
        val newParts = parseVersionParts(newVersion)
        val currentParts = parseVersionParts(currentVersion)
        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val n = newParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (n != c) return n > c
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        return version.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }.ifEmpty { listOf(0) }
    }

    /** Prefer release-signed APK assets over `*-debug.apk` when both are attached. */
    internal fun preferredApkAsset(assets: List<GitHubAsset>): GitHubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apks.firstOrNull { !it.name.contains("-debug", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    /**
     * Downloads the APK from [downloadUrl] to app cache and starts the system installer.
     * [onProgress] is called on the main thread with 0f..1f when content-length is known; otherwise not called (use indeterminate progress in UI).
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: ((Float) -> Unit)? = null,
    ): InstallResult =
        withContext(Dispatchers.IO) {
            val apkFile =
                File(context.cacheDir, "updates").apply { mkdirs() }
                    .let { File(it, "jotty-android-update.apk") }
            try {
                val request = Request.Builder().url(downloadUrl).build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.e(TAG, "Download failed: ${response.code}")
                        return@withContext InstallResult.Failed("Download failed (${response.code})")
                    }
                    val body =
                        response.body ?: run {
                            AppLog.e(TAG, "Download body null")
                            return@withContext InstallResult.Failed("Download failed")
                        }
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead = 0L
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                output.write(buffer, 0, n)
                                bytesRead += n
                                if (totalBytes > 0 && onProgress != null) {
                                    val p = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) { onProgress(p) }
                                }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Download or install failed", e)
                InstallResult.Failed(ApiErrorHelper.userMessage(context, e))
            }
        }

    private fun installApk(
        context: Context,
        apkFile: File,
    ): InstallResult {
        if (!ApkInstallHelper.canInstallOverExisting(context, apkFile)) {
            AppLog.w(TAG, "Update APK signing does not match installed app")
            return InstallResult.Failed(context.getString(R.string.update_signing_mismatch_blocked))
        }
        if (!ApkInstallHelper.isVersionCodeAllowedForUpdate(context, apkFile)) {
            AppLog.w(TAG, "Update APK versionCode is lower than installed app")
            return InstallResult.Failed(context.getString(R.string.update_version_downgrade_blocked))
        }
        return try {
            val uri: Uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    Uri.fromFile(apkFile)
                }
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                }
            ApkInstallHelper.grantReadPermissionToInstallers(context, uri, intent)
            context.startActivity(intent)
            InstallResult.Started
        } catch (e: Exception) {
            AppLog.e(TAG, "Start install failed", e)
            InstallResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.install_failed_fallback))
        }
    }
}
