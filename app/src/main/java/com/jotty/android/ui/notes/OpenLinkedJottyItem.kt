package com.jotty.android.ui.notes

import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.normalizedForClient
import com.jotty.android.util.JottyItemRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens a linked note in the notes tab or notifies [onOpenChecklist] for checklist targets.
 * Shows [showNotFound] when the item cannot be resolved.
 */
fun CoroutineScope.openLinkedJottyItem(
    ref: JottyItemRef,
    notes: List<Note>,
    api: JottyApi?,
    isOnline: Boolean,
    setSelectedNote: (Note) -> Unit,
    onOpenChecklist: (String) -> Unit,
    showNotFound: suspend () -> Unit,
) {
    launch {
        when (ref.type) {
            JottyItemRef.Type.CHECKLIST -> onOpenChecklist(ref.id)
            JottyItemRef.Type.NOTE -> {
                notes.find { it.id == ref.id }?.let {
                    setSelectedNote(it)
                    return@launch
                }
                if (isOnline && api != null) {
                    val fetched =
                        runCatching {
                            api.getNote(ref.id).data.normalizedForClient()
                        }.getOrNull()
                    if (fetched != null) {
                        setSelectedNote(fetched)
                        return@launch
                    }
                }
                showNotFound()
            }
        }
    }
}
