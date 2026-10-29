package com.jotty.android.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.NoteTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
fun NoteFromTemplateDialog(
    api: JottyApi,
    onDismiss: () -> Unit,
    onSelect: (NoteTemplate) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var templates by remember { mutableStateOf<List<NoteTemplate>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val loadFailedMsg = stringResource(R.string.templates_load_failed)
    val notSupportedMsg = stringResource(R.string.templates_not_supported)
    val emptyMsg = stringResource(R.string.templates_empty)

    LaunchedEffect(Unit) {
        loading = true
        errorMessage = null
        runCatching {
            withContext(Dispatchers.IO) { api.getTemplates().templates }
        }.onSuccess { list ->
            templates = list.filter { it.encrypted != true }
            if (templates.isEmpty() && list.isNotEmpty()) {
                errorMessage = emptyMsg
            }
        }.onFailure { error ->
            errorMessage =
                when {
                    error is HttpException && (error.code() == 404 || error.code() == 405) ->
                        notSupportedMsg
                    else -> loadFailedMsg
                }
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_from_template)) },
        text = {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                errorMessage != null && templates.isEmpty() ->
                    Text(
                        text = errorMessage!!,
                        color =
                            if (errorMessage == emptyMsg) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                templates.isEmpty() -> Text(emptyMsg)
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(templates, key = { it.id }) { template ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(template) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    text =
                                        template.title.ifBlank {
                                            stringResource(R.string.untitled)
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
