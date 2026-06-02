package com.jotty.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jotty.android.ui.JottyAppContent
import com.jotty.android.ui.common.ProvideReducedMotion
import com.jotty.android.ui.notes.LocalReaderTextScale
import com.jotty.android.ui.theme.DEFAULT_CUSTOM_ACCENT_HEX
import com.jotty.android.ui.theme.JottyTheme

class MainActivity : FragmentActivity() {
    /** Shared mutable state for deep-link note ID, updated by both onCreate and onNewIntent. */
    private val deepLinkNoteId = mutableStateOf<String?>(null)

    /** Text shared into the app via ACTION_SEND; drives a prefilled create-note dialog. */
    private val sharedNoteText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkNoteId.value = parseDeepLinkNoteId(intent)
        sharedNoteText.value = parseNoteCreationText(intent)
        enableEdgeToEdge()
        setContent {
            val settingsRepository = (applicationContext as JottyApp).settingsRepository
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val themeColor by settingsRepository.themeColor.collectAsStateWithLifecycle(initialValue = "default")
            val themeCustomAccentHex by settingsRepository.themeCustomAccentHex.collectAsStateWithLifecycle(
                initialValue = DEFAULT_CUSTOM_ACCENT_HEX,
            )
            val themeCustomTintedBackgrounds by settingsRepository.themeCustomTintedBackgrounds.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val readerTextScale by settingsRepository.readerTextScale.collectAsStateWithLifecycle(initialValue = 1.0f)
            JottyTheme(
                themeMode = themeMode,
                themeColor = themeColor,
                themeCustomAccentHex = themeCustomAccentHex,
                themeCustomTintedBackgrounds = themeCustomTintedBackgrounds,
            ) {
                ProvideReducedMotion(settingsRepository = settingsRepository) {
                    CompositionLocalProvider(LocalReaderTextScale provides readerTextScale) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            JottyAppContent(
                                settingsRepository = settingsRepository,
                                deepLinkNoteId = deepLinkNoteId,
                                sharedNoteText = sharedNoteText,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Propagate deep-link note ID so the composable reacts to new intents
        parseDeepLinkNoteId(intent)?.let { deepLinkNoteId.value = it }
        parseNoteCreationText(intent)?.let { sharedNoteText.value = it }
    }

    companion object {
        /** Extra set by the home-screen widget to open the create-note dialog. */
        const val EXTRA_QUICK_ADD_NOTE = "com.jotty.android.action.QUICK_ADD_NOTE"

        /** Parses note id from jotty-android://open/note/{id} */
        fun parseDeepLinkNoteId(intent: Intent?): String? = intent?.data?.lastPathSegment?.takeIf { it.isNotBlank() && it != "note" }

        /**
         * Returns the text that should prefill a new note (opening the create dialog), or null when
         * the intent is neither a share nor a quick-add. Quick-add yields an empty (non-null) string.
         */
        fun parseNoteCreationText(intent: Intent?): String? {
            if (intent?.getBooleanExtra(EXTRA_QUICK_ADD_NOTE, false) == true) return ""
            if (intent?.action != Intent.ACTION_SEND) return null
            if (intent.type?.startsWith("text/") != true) return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
            return when {
                !text.isNullOrBlank() && !subject.isNullOrBlank() -> "$subject\n\n$text"
                !text.isNullOrBlank() -> text
                !subject.isNullOrBlank() -> subject
                else -> null
            }
        }
    }
}
