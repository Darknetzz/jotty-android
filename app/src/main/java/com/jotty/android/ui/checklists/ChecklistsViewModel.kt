package com.jotty.android.ui.checklists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChecklistsViewModel(
    application: Application,
    private val api: JottyApi,
) : AndroidViewModel(application) {
    private val _checklists = MutableStateFlow<List<Checklist>>(emptyList())
    val checklists: StateFlow<List<Checklist>> = _checklists.asStateFlow()

    private val _selectedList = MutableStateFlow<Checklist?>(null)
    val selectedList: StateFlow<Checklist?> = _selectedList.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setSelectedList(list: Checklist?) {
        _selectedList.value = list
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun loadChecklists() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _checklists.value = api.getChecklists().checklists
                AppLog.d("checklists", "Loaded ${_checklists.value.size} checklists")
            } catch (e: Exception) {
                AppLog.e("checklists", "Load failed", e)
                _error.value = ApiErrorHelper.userMessage(getApplication(), e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun deleteChecklist(
        id: String,
        onError: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                deleteChecklistSuspend(id)
            } catch (e: Exception) {
                AppLog.e("checklists", "Delete checklist failed", e)
                onError()
            }
        }
    }

    /** For swipe-to-delete: throws on API failure. */
    suspend fun deleteChecklistSuspend(id: String) {
        api.deleteChecklist(id)
        _checklists.value = _checklists.value.filter { it.id != id }
        if (_selectedList.value?.id == id) {
            _selectedList.value = null
        }
    }

    /** Recreate a checklist after undo; returns false if the server rejected the create. */
    suspend fun recreateChecklistAfterUndo(
        title: String,
        type: String,
    ): Boolean {
        val created =
            api.createChecklist(
                CreateChecklistRequest(
                    title = title,
                    type = type,
                ),
            )
        return if (created.success) {
            loadChecklists()
            true
        } else {
            false
        }
    }

    fun createChecklist(
        title: String,
        projectTaskType: Boolean,
        onFailure: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val created =
                    api.createChecklist(
                        CreateChecklistRequest(
                            title = title,
                            type = if (projectTaskType) "task" else "simple",
                        ),
                    )
                if (created.success) {
                    _selectedList.value = created.data
                    _showCreateDialog.value = false
                    loadChecklists()
                } else {
                    onFailure()
                }
            } catch (_: Exception) {
                onFailure()
            }
        }
    }
}
