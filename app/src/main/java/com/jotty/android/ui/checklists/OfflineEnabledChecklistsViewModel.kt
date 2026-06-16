package com.jotty.android.ui.checklists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.OfflineChecklistsRepository
import com.jotty.android.util.filterChecklistsForCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OfflineEnabledChecklistsViewModel(
    private val offlineRepository: OfflineChecklistsRepository,
    @Suppress("unused") private val api: JottyApi,
) : ViewModel() {
    private val _selectedList = MutableStateFlow<Checklist?>(null)
    val selectedList: StateFlow<Checklist?> = _selectedList.asStateFlow()

    fun setSelectedList(list: Checklist?) {
        _selectedList.value = list
    }

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    private val _checklistCategories = MutableStateFlow<List<String>>(emptyList())
    val checklistCategories: StateFlow<List<String>> = _checklistCategories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleCategoryChip(
        cat: String,
        localLists: List<Checklist> = emptyList(),
    ) {
        val wasSelected = _selectedCategory.value == cat
        _selectedCategory.value = if (wasSelected) null else cat
        if (!wasSelected && _selectedCategory.value == cat &&
            filterChecklistsForCategory(localLists, cat).isEmpty()
        ) {
            viewModelScope.launch { _categoryFilterEmptyEvents.emit(cat) }
        }
    }

    private val _categoryFilterEmptyEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val categoryFilterEmptyEvents: SharedFlow<String> = _categoryFilterEmptyEvents.asSharedFlow()

    fun notifyCategoryFilterEmptyIfNeeded(
        category: String?,
        localLists: List<Checklist>,
    ) {
        if (category == null) return
        if (filterChecklistsForCategory(localLists, category).isEmpty()) {
            viewModelScope.launch { _categoryFilterEmptyEvents.emit(category) }
        }
    }

    fun applyConflictSearchFilter() {
        _searchQuery.value = "(Local copy)"
    }

    fun loadCategories(
        isOnline: Boolean,
        localLists: List<Checklist>,
    ) {
        viewModelScope.launch {
            runCatching {
                _checklistCategories.value =
                    if (isOnline) {
                        api.getCategories().categories.checklists.map { it.name }.distinct()
                    } else {
                        localLists.map { it.category }.distinct()
                    }
            }.onFailure {
                _checklistCategories.value = localLists.map { it.category }.distinct()
            }
        }
    }

    val filteredChecklists: StateFlow<List<Checklist>> =
        combine(
            offlineRepository.getChecklistsFlow(),
            _searchQuery,
            _selectedCategory,
        ) { lists, query, category ->
            filterChecklists(lists, query, category)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // After a local-only checklist syncs, Room uses the server id; keep detail selection in sync.
        viewModelScope.launch {
            offlineRepository.getChecklistsFlow().collect { lists ->
                val selected = _selectedList.value ?: return@collect
                lists.find { it.id == selected.id }?.let { current ->
                    if (current != selected) _selectedList.value = current
                    return@collect
                }
                offlineRepository.remappedChecklistId(selected.id)?.let { serverId ->
                    lists.find { it.id == serverId }?.let { _selectedList.value = it }
                }
            }
        }
    }
}
