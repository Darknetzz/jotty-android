package com.jotty.android.data.updates

import retrofit2.http.GET

/**
 * GitHub REST API for checking latest release.
 * Base URL: https://api.github.com/
 */
interface GitHubApi {

    @GET("repos/Darknetzz/jotty-android/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseResponse
}
