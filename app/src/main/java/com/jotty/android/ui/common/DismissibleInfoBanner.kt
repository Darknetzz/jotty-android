package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R

@Composable
fun DismissibleInfoBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
