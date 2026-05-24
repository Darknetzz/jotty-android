package com.jotty.android.data.updates

import com.jotty.android.util.ApkInstallHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `parseUpdateChannel maps known values and falls back to Stable`() {
        assertEquals(UpdateChannel.Stable, parseUpdateChannel(null))
        assertEquals(UpdateChannel.Stable, parseUpdateChannel(""))
        assertEquals(UpdateChannel.Stable, parseUpdateChannel("stable"))
        assertEquals(UpdateChannel.Stable, parseUpdateChannel("STABLE"))
        assertEquals(UpdateChannel.Stable, parseUpdateChannel("nonsense"))
        assertEquals(UpdateChannel.Dev, parseUpdateChannel("dev"))
        assertEquals(UpdateChannel.Dev, parseUpdateChannel(" Dev "))
    }

    @Test
    fun `baseVersionNameWithoutDevSuffix strips dev suffix`() {
        assertEquals("1.3.0", UpdateChecker.baseVersionNameWithoutDevSuffix("1.3.0-dev+abcdef0"))
        assertEquals("1.3.0", UpdateChecker.baseVersionNameWithoutDevSuffix("1.3.0"))
        assertEquals("1.3.0", UpdateChecker.baseVersionNameWithoutDevSuffix("  1.3.0  "))
    }

    @Test
    fun `shortShaFromDevVersionName extracts SHA only for dev versions`() {
        assertEquals("abcdef0", UpdateChecker.shortShaFromDevVersionName("1.3.0-dev+abcdef0"))
        assertEquals("abcdef0", UpdateChecker.shortShaFromDevVersionName("1.3.0-dev+ABCDEF0"))
        assertNull(UpdateChecker.shortShaFromDevVersionName("1.3.0"))
        assertNull(UpdateChecker.shortShaFromDevVersionName("1.3.0-dev+"))
        assertNull(UpdateChecker.shortShaFromDevVersionName("1.3.0-dev+xyz123"))
    }

    @Test
    fun `commitFromDevReleaseBody parses Commit line`() {
        val body =
            """
            Rolling pre-release build from `dev`.
            Commit: abcdef0123456789abcdef0123456789abcdef01
            Run: https://github.com/.../actions/runs/12345
            """.trimIndent()
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef01",
            UpdateChecker.commitFromDevReleaseBody(body),
        )
    }

    @Test
    fun `commitFromDevReleaseBody accepts short SHA`() {
        assertEquals(
            "abcdef0",
            UpdateChecker.commitFromDevReleaseBody("Commit: abcdef0"),
        )
    }

    @Test
    fun `commitFromDevReleaseBody returns null for missing or malformed body`() {
        assertNull(UpdateChecker.commitFromDevReleaseBody(null))
        assertNull(UpdateChecker.commitFromDevReleaseBody(""))
        assertNull(UpdateChecker.commitFromDevReleaseBody("No commit info here"))
        assertNull(UpdateChecker.commitFromDevReleaseBody("Commit: zzz"))
    }

    @Test
    fun `localDevBuildMatchesRemote matches when local short SHA prefixes remote full SHA`() {
        assertTrue(
            UpdateChecker.localDevBuildMatchesRemote(
                versionName = "1.3.0-dev+abcdef0",
                remoteCommitFull = "abcdef0123456789abcdef0123456789abcdef01",
            ),
        )
        assertTrue(
            UpdateChecker.localDevBuildMatchesRemote(
                versionName = "1.3.0-dev+ABCDEF0",
                remoteCommitFull = "abcdef0123456789abcdef0",
            ),
        )
    }

    @Test
    fun `localDevBuildMatchesRemote returns false for mismatched SHAs`() {
        assertFalse(
            UpdateChecker.localDevBuildMatchesRemote(
                versionName = "1.3.0-dev+abcdef0",
                remoteCommitFull = "1234567890abcdef1234567890abcdef12345678",
            ),
        )
    }

    @Test
    fun `localDevBuildMatchesRemote returns false when version has no dev SHA`() {
        assertFalse(
            UpdateChecker.localDevBuildMatchesRemote(
                versionName = "1.3.0",
                remoteCommitFull = "abcdef0123456789abcdef0123456789abcdef01",
            ),
        )
    }

    @Test
    fun `isNewerVersion compares semver parts`() {
        assertTrue(UpdateChecker.isNewerVersion("1.3.1", "1.3.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.4.0", "1.3.9"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.99.99"))
        assertFalse(UpdateChecker.isNewerVersion("1.3.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.2.9", "1.3.0"))
    }

    @Test
    fun `isNewerVersion treats stripped dev base as same when remote stable matches`() {
        val remote = "1.3.0"
        val local = UpdateChecker.baseVersionNameWithoutDevSuffix("1.3.0-dev+abcdef0")
        assertFalse(UpdateChecker.isNewerVersion(remote, local))
    }

    @Test
    fun `preferredApkAsset prefers release-signed over debug`() {
        val assets =
            listOf(
                GitHubAsset("jotty-android-1.3.3-dev-debug.apk", "https://example.com/debug"),
                GitHubAsset("jotty-android-1.3.3-dev.apk", "https://example.com/release"),
            )
        assertEquals("jotty-android-1.3.3-dev.apk", UpdateChecker.preferredApkAsset(assets)?.name)
    }

    @Test
    fun `preferredApkAsset falls back to debug when only debug attached`() {
        val assets = listOf(GitHubAsset("jotty-android-debug.apk", "https://example.com/debug"))
        assertEquals("jotty-android-debug.apk", UpdateChecker.preferredApkAsset(assets)?.name)
    }

    @Test
    fun `devCiVersionCode increments within base version band`() {
        assertEquals(270001, ApkInstallHelper.devCiVersionCode(27, 1))
        assertEquals(279999, ApkInstallHelper.devCiVersionCode(27, 9999))
        assertEquals(280042, ApkInstallHelper.devCiVersionCode(28, 42))
    }
}
