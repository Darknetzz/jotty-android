package com.jotty.android.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * Per-app language. Android 13+ stores it in the system ([LocaleManager]; also settable from the
 * system's per-app language screen, listed via `res/xml/locales_config.xml`). Older versions keep
 * it in a small preference read synchronously by [wrap] from `attachBaseContext`.
 */
object AppLocale {
    /** Language tags the app ships translations for, in picker order (keep in sync with locales_config.xml). */
    val SUPPORTED = listOf("en", "nb")

    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    /** The chosen language tag, or null when the app follows the system language. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeUnless { it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
                ?.let { tag -> SUPPORTED.firstOrNull { tag == it || tag.startsWith("$it-") } ?: tag }
        } else {
            storedTag(context)
        }

    /**
     * Switches to [tag] (null = system language). On Android 13+ the system recreates the activity;
     * on older versions [activity] is recreated so [wrap] picks up the new language.
     */
    fun set(
        activity: Activity,
        tag: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_TAG, tag) }
            activity.recreate()
        }
    }

    /** Context with the chosen language applied (pre-Android 13 only; newer versions return [base]). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }
}
