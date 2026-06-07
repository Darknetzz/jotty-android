package com.jotty.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jotty.android.R
import com.jotty.android.util.ServerUrlScheme
import com.jotty.android.util.combineServerUrl
import com.jotty.android.util.extractSchemeFromTypedHost
import com.jotty.android.util.parseServerUrl

/**
 * Server URL entry: http/https scheme dropdown plus host/path field.
 * Typing a scheme prefix into the host field moves it to the dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerUrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    resetKey: Any? = null,
) {
    val initial = remember(resetKey) { parseServerUrl(value) }
    var scheme by remember(resetKey) { mutableStateOf(initial.scheme) }
    var host by remember(resetKey) { mutableStateOf(initial.hostAndPath) }
    var schemeExpanded by remember { mutableStateOf(false) }

    fun emitChange(
        newScheme: ServerUrlScheme = scheme,
        newHost: String = host,
    ) {
        scheme = newScheme
        host = newHost
        onValueChange(combineServerUrl(newScheme, newHost))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = host,
            onValueChange = { input ->
                val (typedScheme, hostRemainder) = extractSchemeFromTypedHost(input)
                emitChange(
                    newScheme = typedScheme ?: scheme,
                    newHost = hostRemainder,
                )
            },
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            prefix = {
                ExposedDropdownMenuBox(
                    expanded = schemeExpanded,
                    onExpandedChange = { schemeExpanded = it },
                ) {
                    TextButton(
                        onClick = { schemeExpanded = true },
                        modifier = Modifier.menuAnchor(),
                        contentPadding = PaddingValues(end = 0.dp),
                    ) {
                        Row {
                            ServerSchemeLabel(scheme.prefix)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.cd_choose_server_scheme),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ExposedDropdownMenu(
                        expanded = schemeExpanded,
                        onDismissRequest = { schemeExpanded = false },
                        modifier = Modifier.widthIn(min = 120.dp),
                    ) {
                        ServerUrlScheme.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { ServerSchemeLabel(option.prefix) },
                                onClick = {
                                    emitChange(newScheme = option)
                                    schemeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            },
        )
        Text(
            text = stringResource(R.string.server_url_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ServerSchemeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
    )
}
