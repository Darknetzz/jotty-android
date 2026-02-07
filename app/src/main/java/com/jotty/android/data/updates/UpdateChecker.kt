package com.jotty.android.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.jotty.android.BuildConfig
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
    data class UpdateAvailable(val versionName: String, val downloadUrl: String) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateChecker {

    private const val GITHUB_API_BASE = "https://api.github.com/"
    private const val TAG = "UpdateChecker"

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val githubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    /**
     * Fetches latest release from GitHub and compares with [BuildConfig.VERSION_NAME].
     * Returns [UpdateCheckResult.UpdateAvailable] with first .apk asset URL if newer, else [UpdateCheckResult.UpToDate] or [UpdateCheckResult.Error].
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = githubApi.getLatestRelease()
            val latestVersion = release.tagName.removePrefix("v").trim()
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext UpdateCheckResult.Error("No APK in release")

            val current = BuildConfig.VERSION_NAME?.trim() ?: "0.0.0"
            if (!isNewerVersion(latestVersion, current)) {
                return@withContext UpdateCheckResult.UpToDate
            }
            UpdateCheckResult.UpdateAvailable(
                versionName = latestVersion,
                downloadUrl = apkAsset.browserDownloadUrl,
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Check for update failed", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
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
     * Call from UI context (e.g. Activity). Returns true if install intent was started.
     */
    suspend fun downloadAndInstall(context: Context, downloadUrl: String): Boolean = withContext(Dispatchers.IO) {
        val apkFile = File(context.cacheDir, "updates").apply { mkdirs() }
            .let { File(it, "jotty-android-update.apk") }
        try {
            val request = Request.Builder().url(downloadUrl).build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.e(TAG, "Download failed: ${response.code}")
                    return@withContext false
                }
                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    AppLog.e(TAG, "Download body null")
                    return@withContext false
                }
            }
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Download or install failed", e)
            false
        }
    }

    private fun installApk(context: Context, apkFile: File): Boolean {
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
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Start install failed", e)
            false
        }
    }
}
