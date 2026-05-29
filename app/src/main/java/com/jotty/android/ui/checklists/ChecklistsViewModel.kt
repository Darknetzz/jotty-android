package com.jotty.android.ui.checklists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.AddItemRequest
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.CreateChecklistRequest
import com.jotty.android.data.api.JottyApi
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    val checklistCategories: StateFlow<List<String>> =
        _checklists
            .map { lists -> lists.map { it.category }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChecklists: StateFlow<List<Checklist>> =
        combine(_checklists, _searchQuery, _selectedCategory) { lists, query, category ->
            filterChecklists(lists, query, category)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedList(list: Checklist?) {
        _selectedList.value = list
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleCategoryChip(cat: String) {
        _selectedCategory.value = if (_selectedCategory.value == cat) null else cat
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

    /**
     * Recreate a checklist after undo, restoring its items (including nested sub-tasks and
     * completion state). Returns false if the server rejected the create.
     */
    suspend fun recreateChecklistAfterUndo(original: Checklist): Boolean {
        val type =
            if (original.type.equals("task", ignoreCase = true) ||
                original.type.equals("project", ignoreCase = true)
            ) {
                "task"
            } else {
                "simple"
            }
        val created =
            api.createChecklist(
                CreateChecklistRequest(
                    title = original.title,
                    category = original.category,
                    type = type,
                ),
            )
        if (!created.success) return false
        try {
            replayItems(created.data.id, original.items, null)
        } catch (e: Exception) {
            AppLog.e("checklists", "Failed restoring items after undo", e)
        }
        loadChecklists()
        return true
    }

    /** Re-adds [items] depth-first under [parentPath], preserving order and completion state. */
    private suspend fun replayItems(
        listId: String,
        items: List<ChecklistItem>,
        parentPath: String?,
    ) {
        items.forEachIndexed { index, item ->
            val path = if (parentPath == null) "$index" else "$parentPath.$index"
            api.addChecklistItem(
                listId,
                AddItemRequest(text = item.text, status = item.status, parentIndex = parentPath),
            )
            val children = item.children.orEmpty()
            if (children.isNotEmpty()) replayItems(listId, children, path)
            if (item.completed) api.checkItem(listId, path)
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
