package com.mlbb.assistant.presentation.settings

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.capture.AspectRatioPreset
import com.mlbb.assistant.capture.CvFeatureFlags
import com.mlbb.assistant.domain.engine.WeightCalibrator
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetDraftHistoryUseCase
import com.mlbb.assistant.utils.DevModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val getDraftHistoryUseCase: GetDraftHistoryUseCase,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val KEY_META = floatPreferencesKey("weight_meta")
        val KEY_COUNTER = floatPreferencesKey("weight_counter")
        val KEY_SYNERGY = floatPreferencesKey("weight_synergy")
        val KEY_OPACITY = floatPreferencesKey("overlay_opacity")
        val KEY_AUTO_SHOW = booleanPreferencesKey("auto_show_overlay")
        val KEY_VOICE = booleanPreferencesKey("voice_alerts")
        val KEY_DEFAULT_RANK = stringPreferencesKey("default_rank")
        val KEY_ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val KEY_ENABLE_OCR = booleanPreferencesKey("enable_ocr_phase_detection")
    }

    private val _state = MutableStateFlow(
        // Developer mode is stored in SharedPreferences (synchronous) so we can
        // read the initial value right here without a coroutine.
        SettingsState(developerModeEnabled = DevModeManager.isEnabled(context))
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _state.update {
                    it.copy(
                        metaWeight = prefs[KEY_META] ?: 0.40f,
                        counterWeight = prefs[KEY_COUNTER] ?: 0.30f,
                        synergyWeight = prefs[KEY_SYNERGY] ?: 0.30f,
                        overlayOpacity = prefs[KEY_OPACITY] ?: 0.87f,
                        autoShowOverlay = prefs[KEY_AUTO_SHOW] ?: true,
                        voiceAlertsEnabled = prefs[KEY_VOICE] ?: false,
                        defaultRank = prefs[KEY_DEFAULT_RANK] ?: "6 bans (Epic)",
                        overlayGranted = Settings.canDrawOverlays(context),
                        accessibilityGranted = isAccessibilityEnabled(),
                        aspectRatioPreset = AspectRatioPreset.fromKey(
                            prefs[KEY_ASPECT_RATIO] ?: AspectRatioPreset.AUTO.key
                        ),
                        enableOcrPhaseDetection = (prefs[KEY_ENABLE_OCR] ?: true).also {
                            // CvFeatureFlags is the single source of truth read by the
                            // capture pipeline; keep it in sync on every preference emission.
                            CvFeatureFlags.setEnableOcr(it)
                        }
                    )
                }
            }
        }
        runCalibration()
    }

    fun setMetaWeight(v: Float) = save { it[KEY_META] = v }
    fun setCounterWeight(v: Float) = save { it[KEY_COUNTER] = v }
    fun setSynergyWeight(v: Float) = save { it[KEY_SYNERGY] = v }
    fun setOpacity(v: Float) = save { it[KEY_OPACITY] = v }
    fun setAutoShow(v: Boolean) = save { it[KEY_AUTO_SHOW] = v }
    fun setVoiceAlerts(v: Boolean) = save { it[KEY_VOICE] = v }

    /**
     * Toggles the ML Kit OCR phase-detection cross-check (see [CvFeatureFlags.enableOcr]).
     * Persisted to DataStore and mirrored immediately so the change takes effect
     * on the next captured frame.
     */
    fun setEnableOcrPhaseDetection(v: Boolean) {
        CvFeatureFlags.setEnableOcr(v)
        save { it[KEY_ENABLE_OCR] = v }
    }

    fun resetWeights() = viewModelScope.launch {
        dataStore.edit {
            it[KEY_META] = 0.40f
            it[KEY_COUNTER] = 0.30f
            it[KEY_SYNERGY] = 0.30f
        }
    }

    /** Toggles Developer mode and updates the launcher activity alias. */
    fun setDeveloperMode(enabled: Boolean) {
        DevModeManager.setEnabled(context, enabled)
        _state.update { it.copy(developerModeEnabled = enabled) }
    }

    // ── Calibration ─────────────────────────────────────────────────────────

    fun runCalibration() {
        viewModelScope.launch {
            _state.update { it.copy(isCalibrating = true) }
            try {
                val history = getDraftHistoryUseCase.all().first()
                val currentWeights = ScoreWeights.normalized(
                    meta = _state.value.metaWeight,
                    synergy = _state.value.synergyWeight,
                    counter = _state.value.counterWeight
                )
                val result = WeightCalibrator.calibrate(history, currentWeights)
                _state.update { it.copy(calibrationResult = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Failed to calculate scoring-weight calibration")
                _state.update { it.copy(calibrationResult = null) }
            } finally {
                _state.update { it.copy(isCalibrating = false) }
            }
        }
    }

    fun applyCalibrationWeights() {
        val suggested = _state.value.calibrationResult?.suggestedWeights ?: return
        viewModelScope.launch {
            dataStore.edit {
                it[KEY_META] = suggested.meta
                it[KEY_COUNTER] = suggested.counter
                it[KEY_SYNERGY] = suggested.synergy
            }
        }
    }

    fun setDefaultRank(rank: String) = save { it[KEY_DEFAULT_RANK] = rank }
    fun setAspectRatioPreset(preset: AspectRatioPreset) = save { it[KEY_ASPECT_RATIO] = preset.key }

    private fun save(block: suspend (MutablePreferences) -> Unit) =
        viewModelScope.launch { dataStore.edit { block(it) } }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        val service = "${context.packageName}/${com.mlbb.assistant.service.MLBBAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        enabled?.contains(service) == true
    }.getOrDefault(false)
}
