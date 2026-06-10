package com.jotty.android.ui.setup

import androidx.lifecycle.ViewModel
import com.jotty.android.data.preferences.JottyInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SetupViewModel : ViewModel() {
    private val _showAddForm = MutableStateFlow(false)
    val showAddForm: StateFlow<Boolean> = _showAddForm.asStateFlow()

    private val _instanceToEdit = MutableStateFlow<JottyInstance?>(null)
    val instanceToEdit: StateFlow<JottyInstance?> = _instanceToEdit.asStateFlow()

    private val _instanceToDelete = MutableStateFlow<JottyInstance?>(null)
    val instanceToDelete: StateFlow<JottyInstance?> = _instanceToDelete.asStateFlow()

    fun syncWithInstances(instancesEmpty: Boolean) {
        when {
            instancesEmpty -> _showAddForm.value = true
            _showAddForm.value -> _showAddForm.value = false
        }
    }

    fun setShowAddForm(show: Boolean) {
        _showAddForm.value = show
    }

    fun setInstanceToEdit(instance: JottyInstance?) {
        _instanceToEdit.value = instance
    }

    fun setInstanceToDelete(instance: JottyInstance?) {
        _instanceToDelete.value = instance
    }
}
