package com.mlbb.assistant.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.database.HeroPoolDao
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import com.mlbb.assistant.domain.usecase.SaveDraftSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val saveDraftSessionUseCase: SaveDraftSessionUseCase,
    private val draftSessionManager: DraftSessionManager,
    private val preferencesDataStore: PreferencesDataStore,
    private val heroPoolDao: HeroPoolDao
) : ViewModel() {

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    private var allHeroes: List<Hero> = emptyList()
    private var currentWeights = ScoreWeights.DEFAULT
    private var poolMap: Map<Int, Proficiency> = emptyMap()
    private var heroCollectJob: Job? = null
    private var suggestionsJob: Job? = null
    private var didSaveSession = false

    // Accessed by refreshSuggestions callers and scoring jobs on different dispatchers.
    private val suggestionsLock = Any()
    private var suggestionGeneration = 0L

    init {
        loadHeroes()
        observeScoringConfig()
        viewModelScope.launch {
            draftSessionManager.session.collect { session ->
                _state.update { s ->
                    s.copy(
                        allies = session.ourPickedHeroes,
                        enemies = session.enemyPickedHeroes,
                        bans = session.allBannedHeroes,
                        isLoading = false
                    )
                }
                // Persist the session as soon as the draft completes (only once per session).
                if (session.phase == DraftPhase.COMPLETE && !didSaveSession) {
                    didSaveSession = true
                    saveSession(session)
                }
                // Reset the save-guard when a new session is initialised.
                if (session.phase == DraftPhase.IDLE || session.phase == DraftPhase.SETUP) {
                    didSaveSession = false
                    _state.update { it.copy(sessionSaved = false) }
                }
                refreshSuggestions(session)
            }
        }
    }

    private fun loadHeroes() {
        if (heroCollectJob?.isActive == true) return
        heroCollectJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes.toList()
                _state.update { it.copy(isLoading = false) }
                // Session may have emitted before the hero flow; refresh here so
                // recommendations are populated as soon as the hero corpus arrives.
                refreshSuggestions(draftSessionManager.session.value)
            }
        }
    }

    /**
     * Observes the user's scoring configuration so in-app recommendations match
     * what they tuned in Settings (weights) and the Hero Pool screen (personal
     * pool). [PreferencesDataStore.scoreWeightsFlow] emits normalised weights.
     */
    private fun observeScoringConfig() {
        viewModelScope.launch {
            preferencesDataStore.scoreWeightsFlow.collect { weights ->
                currentWeights = weights
                refreshSuggestions(draftSessionManager.session.value)
            }
        }
        viewModelScope.launch {
            heroPoolDao.getAll().collect { entities ->
                poolMap = entities.associate { it.heroId to it.toProficiency() }
                refreshSuggestions(draftSessionManager.session.value)
            }
        }
    }

    private fun refreshSuggestions(session: DraftSession) {
        val request: Long
        val heroesSnapshot: List<Hero>
        val weightsSnapshot: ScoreWeights
        val poolSnapshot: Map<Int, Proficiency>
        synchronized(suggestionsLock) {
            request = ++suggestionGeneration
            heroesSnapshot = allHeroes.toList()
            weightsSnapshot = currentWeights
            poolSnapshot = poolMap.toMap()
            suggestionsJob?.cancel()
            suggestionsJob = viewModelScope.launch(Dispatchers.Default) {
                val scored = getSuggestionsUseCase(heroesSnapshot, session, weightsSnapshot, poolSnapshot)
                val warnings = CompositionAnalyzer.getCounterPickWarnings(
                    ourPicks = session.ourPickedHeroes,
                    enemyPicks = session.enemyPickedHeroes
                )
                currentCoroutineContext().ensureActive()
                synchronized(suggestionsLock) {
                    // A cancelled or superseded computation must not overwrite newer UI state.
                    if (request == suggestionGeneration) {
                        _state.update { it.copy(suggestions = scored, counterPickWarnings = warnings) }
                    }
                }
            }
        }
    }

    private fun saveSession(session: DraftSession) {
        // SaveDraftSessionUseCase owns dispatcher selection (withContext(Dispatchers.IO) inside).
        viewModelScope.launch {
            val rowId = saveDraftSessionUseCase(session)
            if (rowId >= 0) {
                Timber.i("Draft session saved — row id $rowId")
                _state.update { it.copy(sessionSaved = true) }
            } else {
                Timber.w("Draft session save failed for session rank=${session.rank}")
            }
        }
    }

    override fun onCleared() {
        heroCollectJob?.cancel()
        suggestionsJob?.cancel()
        super.onCleared()
    }
}
