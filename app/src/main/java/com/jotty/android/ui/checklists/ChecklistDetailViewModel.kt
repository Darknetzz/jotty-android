package com.jotty.android.ui.checklists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.ChecklistItem
import com.jotty.android.data.api.DEFAULT_TASK_STATUSES
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.TaskStatus
import com.jotty.android.data.api.UpdateChecklistRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ChecklistDetailViewModel(
    private val checklistId: String,
    private val api: JottyApi,
) : ViewModel() {
    private val _items = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val items: StateFlow<List<ChecklistItem>> = _items.asStateFlow()

    private val _displayTitle = MutableStateFlow("")
    val displayTitle: StateFlow<String> = _displayTitle.asStateFlow()

    private val _taskStatuses = MutableStateFlow(DEFAULT_TASK_STATUSES)
    val taskStatuses: StateFlow<List<TaskStatus>> = _taskStatuses.asStateFlow()

    private val _canUseKanbanBoard = MutableStateFlow(true)
    val canUseKanbanBoard: StateFlow<Boolean> = _canUseKanbanBoard.asStateFlow()

    private val _projectView = MutableStateFlow(KanbanProjectView.Board)
    val projectView: StateFlow<KanbanProjectView> = _projectView.asStateFlow()

    private val _selectedKanbanPath = MutableStateFlow<String?>(null)
    val selectedKanbanPath: StateFlow<String?> = _selectedKanbanPath.asStateFlow()

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()

    private val _showManageStatusesDialog = MutableStateFlow(false)
    val showManageStatusesDialog: StateFlow<Boolean> = _showManageStatusesDialog.asStateFlow()

    private val _showShareDialog = MutableStateFlow(false)
    val showShareDialog: StateFlow<Boolean> = _showShareDialog.asStateFlow()

    private val _editingItemKey = MutableStateFlow<String?>(null)
    val editingItemKey: StateFlow<String?> = _editingItemKey.asStateFlow()

    fun syncFromChecklist(checklist: Checklist) {
        _displayTitle.value = checklist.title
        _items.value = checklist.items
        _editingItemKey.value = null
    }

    fun setItems(items: List<ChecklistItem>) {
        _items.value = items
    }

    fun setDisplayTitle(title: String) {
        _displayTitle.value = title
    }

    fun setProjectView(view: KanbanProjectView) {
        _projectView.value = view
    }

    fun setSelectedKanbanPath(path: String?) {
        _selectedKanbanPath.value = path
    }

    fun setShowRenameDialog(show: Boolean) {
        _showRenameDialog.value = show
    }

    fun setShowManageStatusesDialog(show: Boolean) {
        _showManageStatusesDialog.value = show
    }

    fun setShowShareDialog(show: Boolean) {
        _showShareDialog.value = show
    }

    fun setEditingItemKey(key: String?) {
        _editingItemKey.value = key
    }

    fun resetProjectState() {
        _taskStatuses.value = DEFAULT_TASK_STATUSES
        _canUseKanbanBoard.value = true
        _projectView.value = KanbanProjectView.Board
        _selectedKanbanPath.value = null
    }

    suspend fun refreshItemsFromServer(): Checklist? {
        val updated = api.getChecklists().checklists.find { it.id == checklistId } ?: return null
        _items.value = updated.items
        return updated
    }

    fun refreshItemsFromServer(
        onUpdated: (Checklist) -> Unit,
        onFailed: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                refreshItemsFromServer()?.let(onUpdated)
            } catch (_: Exception) {
                onFailed()
            }
        }
    }

    fun refreshTaskStatuses(isProject: Boolean) {
        if (!isProject) return
        viewModelScope.launch {
            try {
                val statuses = api.getTaskStatuses(checklistId).statuses.sortedBy { it.order }
                _taskStatuses.value = if (statuses.isNotEmpty()) statuses else DEFAULT_TASK_STATUSES
                _canUseKanbanBoard.value = true
            } catch (e: Exception) {
                if (e is HttpException && e.code() in setOf(404, 405)) {
                    _canUseKanbanBoard.value = false
                    return@launch
                }
                _canUseKanbanBoard.value = false
            }
        }
    }

    fun renameChecklist(
        currentCategory: String,
        newTitle: String,
        newCategory: String,
        onUpdated: (Checklist) -> Unit,
        onFailed: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val response =
                    api.updateChecklist(
                        checklistId,
                        UpdateChecklistRequest(title = newTitle, category = newCategory),
                    )
                if (response.success) {
                    _displayTitle.value = response.data.title
                    onUpdated(response.data)
                } else {
                    onFailed()
                }
            } catch (_: Exception) {
                onFailed()
            }
        }
    }

    fun setCanUseKanbanBoard(value: Boolean) {
        _canUseKanbanBoard.value = value
    }

    fun pullItemsFromServer(
        onUpdated: (Checklist) -> Unit,
        onFailed: () -> Unit = {},
    ) {
        refreshItemsFromServer(onUpdated, onFailed)
    }
}
