package com.focusforceplus.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.NavRepository
import com.focusforceplus.app.util.ModuleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    private val navRepository: NavRepository,
    private val moduleController: ModuleController,
) : ViewModel() {

    // While a row is being dragged the order changes many times a second. The list
    // on screen follows this local copy at once; the stored order catches up.
    private val localOrder = MutableStateFlow<List<AppModule>?>(null)

    /** null until the stored setup has loaded, so the bar never flashes defaults. */
    val config: StateFlow<NavConfig?> = combine(navRepository.config, localOrder) { stored, local ->
        if (local != null && local.toSet() == stored.order.toSet()) stored.copy(order = local) else stored
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showDockHint: StateFlow<Boolean> = navRepository.showDockHint
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() = _message.update { null }

    fun move(from: Int, to: Int) {
        val current = config.value?.order ?: return
        if (from !in current.indices || to !in current.indices) return
        val next = current.toMutableList().apply { add(to, removeAt(from)) }
        localOrder.value = next
        viewModelScope.launch { navRepository.saveOrder(next) }
    }

    fun setShown(module: AppModule, shown: Boolean) {
        viewModelScope.launch { navRepository.setHidden(module, !shown) }
    }

    fun setModuleOn(module: AppModule, on: Boolean) {
        viewModelScope.launch {
            moduleController.setModuleOn(module, on)?.let { _message.value = it }
        }
    }

    fun resetOrder() {
        localOrder.value = null
        viewModelScope.launch { navRepository.resetToDefaults() }
    }

    fun noteHintPlayed() {
        viewModelScope.launch { navRepository.noteDockHintPlayed() }
    }

    fun noteUserScrolled() {
        viewModelScope.launch { navRepository.noteDockScrolledByUser() }
    }
}
