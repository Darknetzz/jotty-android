package com.jotty.android.ui.notes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.jotty.android.R
import com.jotty.android.data.encryption.BiometricInvalidationNotifier

/**
 * Shows a one-shot dialog when remembered passphrases were cleared because device biometrics changed.
 */
@Composable
fun BiometricEnrollmentChangedDialogHost() {
    var pendingCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        BiometricInvalidationNotifier.events.collect { count ->
            pendingCount = count
        }
    }

    val count = pendingCount ?: return
    AlertDialog(
        onDismissRequest = { pendingCount = null },
        title = { Text(stringResource(R.string.biometric_enrollment_changed_title)) },
        text = {
            Text(
                if (count == 1) {
                    stringResource(R.string.biometric_enrollment_changed_message_one)
                } else {
                    stringResource(R.string.biometric_enrollment_changed_message_many, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { pendingCount = null }) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
