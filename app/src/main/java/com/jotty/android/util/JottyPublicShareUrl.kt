package com.jotty.android.util

import com.jotty.android.data.api.ApiClient

/**
 * Builds the public web path for a note or checklist on the Jotty instance.
 * Matches in-note links (`/note/{uuid}`, `/checklist/{uuid}`).
 */
fun publicShareUrl(
    baseUrl: String?,
    itemType: String,
    itemId: String,
): String? {
    if (baseUrl.isNullOrBlank() || itemId.isBlank()) return null
    val origin = ApiClient.normalizeBaseUrl(baseUrl).trimEnd('/')
    val path =
        when (itemType.lowercase()) {
            "checklist" -> "checklist"
            else -> "note"
        }
    return "$origin/$path/$itemId"
}
