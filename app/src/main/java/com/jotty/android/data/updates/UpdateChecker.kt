package com.jotty.android.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

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

    @Volatile
    private var cache: Pair<Long, UpdateCheckResult>? = null

    private val githubClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .build()
            )
        }
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .build()
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
     * Fetches latest release from GitHub and compares with [BuildConfig.VERSION_NAME].
     * Success results (UpToDate, UpdateAvailable) are cached for 5 minutes; errors are not cached.
     * [context] is used to resolve user-facing error strings.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache?.let { (cachedAt, result) ->
            if (now - cachedAt < CACHE_TTL_MS && result !is UpdateCheckResult.Error) {
                return@withContext result
            }
        }
        try {
            val release = githubApi.getLatestRelease()
            val latestVersion = release.tagName.removePrefix("v").trim()
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext UpdateCheckResult.Error(context.getString(R.string.no_apk_in_release))

            val current = BuildConfig.VERSION_NAME?.trim() ?: "0.0.0"
            val result = if (!isNewerVersion(latestVersion, current)) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.UpdateAvailable(
                    versionName = latestVersion,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    releaseNotes = release.body?.trim()?.takeIf { it.isNotBlank() },
                )
            }
            cache = now to result
            result
        } catch (e: Exception) {
            AppLog.e(TAG, "Check for update failed", e)
            UpdateCheckResult.Error(ApiErrorHelper.userMessage(context, e))
        }
    }

    /**
     * Returns true if [newVersion] is strictly greater than [currentVersion] (e.g. "1.2.0" > "1.1.2").
     */
    fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        val newParts = parseVersionParts(newVersion)
        val currentParts = parseVersionParts(currentVersion)
        for (i in 0 until maxOf(newParts.size, currentParts.size)) {
            val n = newParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (n != c) return n > c
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> =
        version.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }.ifEmpty { listOf(0) }

    /**
     * Downloads the APK from [downloadUrl] to app cache and starts the system installer.
     * [onProgress] is called on the main thread with 0f..1f when content-length is known; otherwise not called (use indeterminate progress in UI).
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: ((Float) -> Unit)? = null,
    ): InstallResult = withContext(Dispatchers.IO) {
        val apkFile = File(context.cacheDir, "updates").apply { mkdirs() }
            .let { File(it, "jotty-android-update.apk") }
        try {
            val request = Request.Builder().url(downloadUrl).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.e(TAG, "Download failed: ${response.code}")
                    return@withContext InstallResult.Failed("Download failed (${response.code})")
                }
                val body = response.body ?: run {
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

    private fun installApk(context: Context, apkFile: File): InstallResult {
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile,
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
            }
            context.startActivity(intent)
            InstallResult.Started
        } catch (e: Exception) {
            AppLog.e(TAG, "Start install failed", e)
            InstallResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.install_failed_fallback))
        }
    }
}
