package com.jotty.android.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.local.OfflineNotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OfflineEnabledNotesViewModel(
    private val offlineRepository: OfflineNotesRepository,
    private val api: JottyApi,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleCategoryChip(category: String) {
        _selectedCategory.value =
            if (_selectedCategory.value == category) null else category
    }

    fun applyConflictSearchFilter() {
        _searchQuery.value = "(Local copy)"
    }

    val filteredNotes: StateFlow<List<Note>> =
        combine(
            offlineRepository.getNotesFlow(),
            _searchQuery.debounce { q -> if (q.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
            _selectedCategory,
        ) { notes, query, category ->
            Triple(notes, query, category)
        }
            .flatMapLatest { (notes, query, category) ->
                flow {
                    emit(
                        when {
                            query.isNotBlank() -> offlineRepository.searchNotes(query)
                            category != null -> offlineRepository.getNotesByCategory(category)
                            else -> notes
                        },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()

    fun setSelectedNote(note: Note?) {
        _selectedNote.value = note
    }

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    private val _noteCategories = MutableStateFlow<List<String>>(emptyList())
    val noteCategories: StateFlow<List<String>> = _noteCategories.asStateFlow()

    fun loadCategories(
        isOnline: Boolean,
        localNotes: List<Note>,
    ) {
        viewModelScope.launch {
            try {
                if (isOnline) {
                    _noteCategories.value =
                        api.getCategories().categories.notes.map { it.name }.distinct()
                } else {
                    _noteCategories.value = localNotes.map { it.category }.distinct()
                }
            } catch (_: Exception) {
                _noteCategories.value = localNotes.map { it.category }.distinct()
            }
        }
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
