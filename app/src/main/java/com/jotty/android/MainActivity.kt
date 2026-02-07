package com.jotty.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jotty.android.ui.JottyAppContent
import com.jotty.android.ui.theme.JottyTheme

class MainActivity : ComponentActivity() {

    /** Shared mutable state for deep-link note ID, updated by both onCreate and onNewIntent. */
    private val deepLinkNoteId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkNoteId.value = parseDeepLinkNoteId(intent)
        enableEdgeToEdge()
        setContent {
            val settingsRepository = (applicationContext as JottyApp).settingsRepository
            val themeMode by settingsRepository.themeMode.collectAsState(initial = null)
            val themeColor by settingsRepository.themeColor.collectAsState(initial = "default")
            val debugLoggingEnabled by settingsRepository.debugLoggingEnabled.collectAsState(initial = false)
            LaunchedEffect(debugLoggingEnabled) {
                com.jotty.android.util.AppLog.setDebugEnabled(debugLoggingEnabled)
            }
            JottyTheme(themeMode = themeMode, themeColor = themeColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    JottyAppContent(
                        settingsRepository = settingsRepository,
                        deepLinkNoteId = deepLinkNoteId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Propagate deep-link note ID so the composable reacts to new intents
        parseDeepLinkNoteId(intent)?.let { deepLinkNoteId.value = it }
    }

    companion object {
        /** Parses note id from jotty-android://open/note/{id} */
        fun parseDeepLinkNoteId(intent: Intent?): String? =
            intent?.data?.lastPathSegment?.takeIf { it.isNotBlank() && it != "note" }
    }
}
