package com.jotty.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jotty.android.ui.JottyApp
import com.jotty.android.ui.theme.JottyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsRepository = (context.applicationContext as com.jotty.android.JottyApp).settingsRepository
            val themePref by settingsRepository.theme.collectAsState(initial = null)
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val deepLinkNoteId = remember { mutableStateOf(parseDeepLinkNoteId(intent)) }
            JottyTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    JottyApp(
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
    }

    companion object {
        /** Parses note id from jotty-android://open/note/{id} */
        fun parseDeepLinkNoteId(intent: Intent?): String? =
            intent?.data?.lastPathSegment?.takeIf { it.isNotBlank() && it != "note" }
    }
}
