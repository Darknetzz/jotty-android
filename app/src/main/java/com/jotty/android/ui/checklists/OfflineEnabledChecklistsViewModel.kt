package com.jotty.android.ui.checklists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.Checklist
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.local.OfflineChecklistsRepository
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun setSelectedList(list: Checklist?) { _selectedList.value = list }

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setShowCreateDialog(show: Boolean) { _showCreateDialog.value = show }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    private val _checklistCategories = MutableStateFlow<List<String>>(emptyList())
    val checklistCategories: StateFlow<List<String>> = _checklistCategories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun setSelectedCategory(category: String?) { _selectedCategory.value = category }

    fun toggleCategoryChip(cat: String) {
        _selectedCategory.value = if (_selectedCategory.value == cat) null else cat
    }

    fun applyConflictSearchFilter() { _searchQuery.value = "(Local copy)" }

    fun loadCategories(isOnline: Boolean, localLists: List<Checklist>) {
        viewModelScope.launch {
            runCatching {
                _checklistCategories.value = if (isOnline) {
                    api.getCategories().categories.checklists.map { it.name }.distinct()
                } else {
                    localLists.map { it.category }.distinct()
                }
            }.onFailure {
                _checklistCategories.value = localLists.map { it.category }.distinct()
            }
        }
    }

    val filteredChecklists: StateFlow<List<Checklist>> = combine(
        offlineRepository.getChecklistsFlow(),
        _searchQuery,
        _selectedCategory,
    ) { lists, query, category ->
        when {
            query.isNotBlank() -> lists.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
            category != null -> lists.filter { it.category == category }
            else -> lists
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
