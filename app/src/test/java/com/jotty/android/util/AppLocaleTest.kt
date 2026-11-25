package com.jotty.android.util

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppLocaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        prefs().edit { clear() }
    }

    private fun prefs() = context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)

    @Test
    fun wrap_withoutChoice_returnsBaseContext() {
        assertNull(AppLocale.current(context))
        assertSame(context, AppLocale.wrap(context))
    }

    @Test
    fun wrap_withStoredChoice_appliesLanguage() {
        prefs().edit { putString("tag", "nb") }

        val wrapped = AppLocale.wrap(context)

        assertEquals("nb", AppLocale.current(context))
        assertEquals("nb", wrapped.resources.configuration.locales[0].language)
    }
}
