package com.jotty.android.data.updates

import com.google.gson.annotations.SerializedName

/**
 * Minimal DTO for GitHub API: GET /repos/{owner}/{repo}/releases/latest
 */
data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("assets") val assets: List<GitHubAsset>,
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)
