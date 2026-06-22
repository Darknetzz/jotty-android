package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R

@Composable
fun ConflictCopiesBanner(
    conflictCopyCount: Int,
    onViewCopies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conflictCopyCount <= 0) return

    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = stringResource(R.string.cd_warning),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text =
                    if (conflictCopyCount == 1) {
                        stringResource(R.string.conflict_copy_pending)
                    } else {
                        stringResource(R.string.conflict_copies_pending, conflictCopyCount)
                    },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onViewCopies) {
                Text(stringResource(R.string.view_conflicts))
            }
        }
    }
}
