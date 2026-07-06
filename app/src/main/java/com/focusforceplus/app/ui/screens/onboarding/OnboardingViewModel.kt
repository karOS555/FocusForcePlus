package com.focusforceplus.app.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionStates(
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val overlay: Boolean = false,
    val fullScreenIntent: Boolean = false,
    val accessibility: Boolean = false,
    val usageStats: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val dndAccess: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _permissions = MutableStateFlow(PermissionStates())
    val permissions: StateFlow<PermissionStates> = _permissions.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        _permissions.value = PermissionStates(
            notifications = PermissionHelper.canPostNotifications(context),
            exactAlarms = PermissionHelper.canScheduleExactAlarms(context),
            overlay = PermissionHelper.canDrawOverlays(context),
            fullScreenIntent = PermissionHelper.canUseFullScreenIntent(context),
            accessibility = PermissionHelper.isAccessibilityServiceEnabled(context),
            usageStats = PermissionHelper.hasUsageStatsPermission(context),
            batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(context),
            dndAccess = PermissionHelper.hasDndAccess(context),
        )
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.saveOnboardingCompleted(true)
            onDone()
        }
    }
}
