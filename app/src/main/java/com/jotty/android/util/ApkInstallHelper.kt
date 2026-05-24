package com.jotty.android.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Validates and launches APK installs from app cache (in-app updates).
 */
object ApkInstallHelper {
    /**
     * Dev CI uses a monotonic [versionCode] per workflow run so in-app dev updates are not blocked
     * when [gradle.properties] [VERSION_CODE] is unchanged between pushes.
     */
    fun devCiVersionCode(
        baseVersionCode: Int,
        ciRunNumber: Int,
    ): Int = baseVersionCode * 10_000 + (ciRunNumber % 10_000)

    /**
     * Returns false when the downloaded APK is signed with a different key than the installed app.
     * Returns true when signatures match or either side cannot be read (installer will decide).
     */
    fun canInstallOverExisting(
        context: Context,
        apkFile: File,
    ): Boolean {
        val installed = signingCertificateDigests(context, context.packageName) ?: return true
        val incoming = signingCertificateDigestsFromApk(context, apkFile) ?: return true
        if (installed.isEmpty() || incoming.isEmpty()) return true
        return installed.intersect(incoming).isNotEmpty()
    }

    /**
     * Returns false when the APK [versionCode] is lower than the installed app (Android blocks downgrades).
     */
    fun isVersionCodeAllowedForUpdate(
        context: Context,
        apkFile: File,
    ): Boolean {
        val installedCode = installedVersionCode(context) ?: return true
        val apkCode = apkVersionCode(context, apkFile) ?: return true
        return apkCode >= installedCode
    }

    fun grantReadPermissionToInstallers(
        context: Context,
        uri: android.net.Uri,
        installIntent: Intent,
    ) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val resolvers =
            context.packageManager.queryIntentActivities(
                installIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        for (resolve in resolvers) {
            val pkg = resolve.activityInfo.packageName
            context.grantUriPermission(pkg, uri, flags)
        }
    }

    private fun installedVersionCode(context: Context): Long? {
        return try {
            val pm = context.packageManager
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, 0)
                }
            packageVersionCode(info)
        } catch (e: Exception) {
            AppLog.e(TAG, "Installed versionCode read failed", e)
            null
        }
    }

    private fun apkVersionCode(
        context: Context,
        apkFile: File,
    ): Long? {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return null
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
            }
            packageVersionCode(info)
        } catch (e: Exception) {
            AppLog.e(TAG, "APK versionCode read failed", e)
            null
        }
    }

    private fun packageVersionCode(info: android.content.pm.PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun signingCertificateDigests(
        context: Context,
        packageName: String,
    ): Set<String>? {
        return try {
            val pm = context.packageManager
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(
                            PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                        ),
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            digestsFromPackageInfo(info)
        } catch (e: Exception) {
            AppLog.e(TAG, "Installed signing read failed", e)
            null
        }
    }

    private fun signingCertificateDigestsFromApk(
        context: Context,
        apkFile: File,
    ): Set<String>? {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return null
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
            }
            digestsFromPackageInfo(info)
        } catch (e: Exception) {
            AppLog.e(TAG, "APK signing read failed", e)
            null
        }
    }

    private fun digestsFromPackageInfo(info: android.content.pm.PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val signatures =
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            return signatures?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
        }
        @Suppress("DEPRECATION")
        return info.signatures?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private const val TAG = "ApkInstallHelper"
}
