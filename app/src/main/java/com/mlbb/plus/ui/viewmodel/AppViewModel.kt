package com.mlbb.plus.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val isDataReady: Boolean = GameData.isLoaded,
    val heroCount: Int = GameData.heroes.size,
    val itemCount: Int = GameData.items.size,
    val patchVersion: String = GameData.patchNotes.shortVersion,
    val selectedRole: String = "Roam",
    val selectedRoleType: String = RoleTypes.defaultTypeFor("Roam"),
    val autoStartLastRole: Boolean = false,
    val preferencesLoaded: Boolean = false,
    val overlayGranted: Boolean = false,
    val overlayRunning: Boolean = false
)

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun loadPreferences(context: Context) {
        if (_uiState.value.preferencesLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRole = prefs.getString(KEY_LAST_ROLE, "Roam") ?: "Roam"
        val validRole = savedRole.takeIf { it in RoleTypes.roles } ?: "Roam"
        val validTypes = RoleTypes.typesFor(validRole)
        val savedType = prefs.getString(KEY_LAST_ROLE_TYPE, RoleTypes.defaultTypeFor(validRole))
            ?: RoleTypes.defaultTypeFor(validRole)
        val validType = savedType.takeIf { it in validTypes } ?: validTypes.firstOrNull().orEmpty()
        val autoStart = prefs.getBoolean(KEY_AUTO_START, false)
        _uiState.update {
            it.copy(
                selectedRole = validRole,
                selectedRoleType = validType,
                autoStartLastRole = autoStart,
                preferencesLoaded = true
            )
        }
    }

    fun selectRole(role: String, context: Context? = null) {
        val validRole = role.takeIf { it in RoleTypes.roles } ?: "Roam"
        val validTypes = RoleTypes.typesFor(validRole)
        val currentType = _uiState.value.selectedRoleType
        val nextType = if (currentType in validTypes) currentType else validTypes.firstOrNull().orEmpty()
        _uiState.update {
            it.copy(selectedRole = validRole, selectedRoleType = nextType)
        }
        context?.let { persistRole(it, validRole, nextType) }
    }

    fun selectRoleType(roleType: String, context: Context? = null) {
        val currentRole = _uiState.value.selectedRole
        val validTypes = RoleTypes.typesFor(currentRole)
        val nextType = roleType.takeIf { it in validTypes } ?: validTypes.firstOrNull().orEmpty()
        _uiState.update { it.copy(selectedRoleType = nextType) }
        context?.let { persistRole(it, currentRole, nextType) }
    }

    fun setAutoStartLastRole(enabled: Boolean, context: Context? = null) {
        _uiState.update { it.copy(autoStartLastRole = enabled) }
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit {
                putBoolean(KEY_AUTO_START, enabled)
            }
    }

    fun refreshOverlayState(granted: Boolean, running: Boolean) {
        _uiState.update {
            it.copy(
                overlayGranted = granted,
                overlayRunning = running
            )
        }
    }

    fun refreshDataState() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDataReady = GameData.isLoaded,
                    heroCount = GameData.heroes.size,
                    itemCount = GameData.items.size,
                    patchVersion = GameData.patchNotes.shortVersion
                )
            }
        }
    }

    private fun persistRole(context: Context, role: String, roleType: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_ROLE, role)
            putString(KEY_LAST_ROLE_TYPE, roleType)
        }
    }

    companion object {
        const val PREFS_NAME = "mlbb_plus_prefs"
        const val KEY_LAST_ROLE = "last_role"
        const val KEY_LAST_ROLE_TYPE = "last_role_type"
        const val KEY_AUTO_START = "auto_start_last_role"
    }
}
