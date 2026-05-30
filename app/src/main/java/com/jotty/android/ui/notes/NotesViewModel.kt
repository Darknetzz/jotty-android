package com.jotty.android.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jotty.android.data.api.JottyApi
import com.jotty.android.data.api.Note
import com.jotty.android.data.api.normalizedForClient
import com.jotty.android.util.ApiErrorHelper
import com.jotty.android.util.AppLog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.OptIn

/**
 * Holds list / search / filter / selection state for the online-only [NotesScreen].
 */
@OptIn(FlowPreview::class)
class NotesViewModel(
    application: Application,
    private val api: JottyApi,
) : AndroidViewModel(application) {
    private val app = application

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _debouncedSearchQuery = MutableStateFlow("")
    val debouncedSearchQuery: StateFlow<String> = _debouncedSearchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _noteCategories = MutableStateFlow<List<String>>(emptyList())
    val noteCategories: StateFlow<List<String>> = _noteCategories.asStateFlow()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce { q -> if (q.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .collect { _debouncedSearchQuery.value = it }
        }
        viewModelScope.launch {
            combine(_selectedCategory, _debouncedSearchQuery) { _, _ -> }
                .collect { loadNotes() }
        }
        viewModelScope.launch {
            runCatching {
                _noteCategories.value =
                    api.getCategories().categories.notes.map { it.name }.distinct()
            }.onFailure {
                _noteCategories.value = emptyList()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSelectedNote(note: Note?) {
        _selectedNote.value = note
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun loadNotes() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _notes.value =
                    api.getNotes(
                        category = _selectedCategory.value,
                        search = _debouncedSearchQuery.value.takeIf { it.isNotBlank() },
                    ).notes.orEmpty().map { it.normalizedForClient() }
                AppLog.d("notes", "Loaded ${_notes.value.size} notes")
            } catch (e: Exception) {
                AppLog.e("notes", "Load failed", e)
                _error.value = ApiErrorHelper.userMessage(app, e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeNoteFromList(noteId: String) {
        _notes.value = _notes.value.filter { it.id != noteId }
        if (_selectedNote.value?.id == noteId) {
            _selectedNote.value = null
        }
    }

    class Factory(
        private val application: Application,
        private val api: JottyApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(application, api) as T
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
