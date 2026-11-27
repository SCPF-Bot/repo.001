package com.mlbb.plus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.plus.ui.model.GameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================================
// MLBB Plus — lightweight ViewModel (no Hilt, no Room).
//
// Why this exists: MainActivity currently holds all root
// state and reads the GameData singleton directly from
// composables. That works for bundled offline data but
// makes state untestable and detail selection non-saveable.
// This ViewModel is the single source of truth for root UI
// state. It does NOT change data loading: GameData.load()
// still runs synchronously in MainActivity.onCreate()
// (offline-first, small JSON) — see docs/ux-redesign.md §7
// for the staged async-load migration.
//
// Adoption (one line in MainActivity):
//   val vm: AppViewModel by viewModels()
//   val uiState by vm.uiState.collectAsStateWithLifecycle()
// Screens keep their current signatures; pass uiState fields
// down (state hoisting). Do not read GameData from new
// composables — read from AppUiState instead.
// ============================================================

data class AppUiState(
    val isDataReady: Boolean = GameData.isLoaded,
    val heroCount: Int = GameData.heroes.size,
    val itemCount: Int = GameData.items.size,
    val patchVersion: String = GameData.patchNotes.shortVersion,
    val overlayGranted: Boolean = false,
    val overlayRunning: Boolean = false
)

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun refreshOverlayState(granted: Boolean, running: Boolean) {
        _uiState.value = _uiState.value.copy(
            overlayGranted = granted,
            overlayRunning = running
        )
    }

    fun refreshDataState() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDataReady = GameData.isLoaded,
                heroCount = GameData.heroes.size,
                itemCount = GameData.items.size,
                patchVersion = GameData.patchNotes.shortVersion
            )
        }
    }
}
