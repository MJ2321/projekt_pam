package com.example.projekt_pam.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study
import com.example.projekt_pam.domain.repository.WildlifeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: WildlifeRepository
) : ViewModel() {

    private companion object {
        const val DEFAULT_STUDY_ID = 291157141L
    }

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()
    private var individualsLoaded = false

    init {
        loadStudies()
    }

    private fun loadStudies() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.getStudies()
            result.onSuccess { studies ->
                _state.update { it.copy(studies = studies, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun onFilterChanged(query: String) {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isNotBlank() && !individualsLoaded) {
            loadIndividuals(DEFAULT_STUDY_ID)
        }

        _state.update { state ->
            state.copy(
                searchQuery = query,
                filteredIndividuals = filterIndividuals(state.individuals, normalizedQuery),
                error = null
            )
        }
    }

    fun onSuggestionSelected(individualId: Long) {
        _state.update { state ->
            val selected = state.individuals.firstOrNull { it.id == individualId } ?: return@update state
            state.copy(
                searchQuery = selected.identifier,
                filteredIndividuals = listOf(selected),
                error = null
            )
        }
    }

    private fun loadIndividuals(studyId: Long) {
        viewModelScope.launch {
            val result = repository.getIndividuals(studyId)
            result.onSuccess { individuals ->
                individualsLoaded = true
                _state.update { state ->
                    state.copy(
                        individuals = individuals,
                        filteredIndividuals = filterIndividuals(individuals, state.searchQuery.trim()),
                        error = null
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, filteredIndividuals = emptyList()) }
            }
        }
    }

    private fun filterIndividuals(
        individuals: List<Individual>,
        query: String
    ): List<Individual> {
        if (query.isBlank()) return emptyList()
        return individuals.filter { individual ->
            individual.identifier.contains(query, ignoreCase = true) ||
                individual.taxon.contains(query, ignoreCase = true)
        }
    }

    fun onMarkerSelected(individual: Individual) {
        _state.update {
            it.copy(
                selectedIndividual = individual,
                selectedStudy = null,
                selectedTrack = emptyList(),
                isTrackMode = false,
                error = null
            )
        }
    }

    fun onStudyMarkerSelected(study: Study) {
        _state.update {
            it.copy(
                selectedStudy = study,
                selectedIndividual = null,
                selectedTrack = emptyList(),
                isTrackMode = false,
                error = null
            )
        }
    }

    fun selectIndividual(studyId: Long, individual: Individual) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedIndividual = individual,
                    selectedStudy = null,
                    selectedTrack = emptyList(),
                    isTrackMode = false,
                    error = null
                )
            }
        }
    }

    fun onShowTrackClicked() {
        val selectedStudy = _state.value.selectedStudy ?: return
        showTrackForStudy(selectedStudy)
    }

    fun showTrackForStudy(study: Study) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedStudy = study,
                    selectedIndividual = null,
                    isTrackMode = true,
                    selectedTrack = emptyList(),
                    isLoading = true,
                    error = null
                )
            }
            val result = repository.getEvents(study.id, null)
            result.onSuccess { events ->
                if (events.isEmpty()) {
                    _state.update {
                        it.copy(
                            selectedTrack = emptyList(),
                            isLoading = false,
                            error = "Brak danych trasy dla wybranego badania."
                        )
                    }
                } else {
                    _state.update { it.copy(selectedTrack = events, isLoading = false) }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun showTrackFor(individual: Individual) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedIndividual = individual,
                    selectedStudy = null,
                    isTrackMode = true,
                    selectedTrack = emptyList(),
                    isLoading = true
                )
            }
            val result = repository.getEvents(DEFAULT_STUDY_ID, individual.id)
            result.onSuccess { events ->
                _state.update { it.copy(selectedTrack = events, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    /**
     * Monitoruj zmianę zoomu i liczę kliknięcia
     * Po 8 kliknięciach załaduj szczegóły
     */
    fun onZoomChanged(zoomLevel: Double) {
        val previousZoom = _state.value.currentZoom

        // Jeśli zoom się zmienił, zwiększ licznik
        if (zoomLevel != previousZoom) {
            val newClickCount = _state.value.zoomClickCount + 1

            _state.update { it.copy(
                currentZoom = zoomLevel,
                zoomClickCount = newClickCount
            ) }

            // Załaduj szczegóły po 8 kliknięciach zoomu
            if (newClickCount >= 8) {
                loadDetailedTracksForVisibleIndividuals()
            }
        }
    }

    /**
     * Resetuj licznik kliknięć zoomu
     */
    fun resetZoomClickCount() {
        _state.update { it.copy(zoomClickCount = 0) }
    }

    /**
     * Załaduj szczegółowe ścieżki dla przefiltrowanych zwierząt
     * Używa debouncing aby nie załadować za dużo jednocześnie
     */
    private fun loadDetailedTracksForVisibleIndividuals() {
        val currentState = _state.value
        val individualsToLoad = currentState.filteredIndividuals
            .filter { it.id !in currentState.loadedDetailIndividuals }
            .filter { it.id !in currentState.loadingIndividuals }
            .take(5) // Limit do 5 naraz aby nie przeciążyć API

        if (individualsToLoad.isEmpty()) return

        individualsToLoad.forEach { individual ->
            _state.update { it.copy(loadingIndividuals = it.loadingIndividuals + individual.id) }

            viewModelScope.launch {
                try {
                    // Ładuj events/ścieżki dla każdego zwierzęcia
                    val result = repository.getEvents(DEFAULT_STUDY_ID, individual.id)
                    result.onSuccess { events ->
                        _state.update { state ->
                            state.copy(
                                detailedTracks = state.detailedTracks + (individual.id to events),
                                loadedDetailIndividuals = state.loadedDetailIndividuals + individual.id,
                                loadingIndividuals = state.loadingIndividuals - individual.id
                            )
                        }
                    }.onFailure { e ->
                        _state.update { state ->
                            state.copy(
                                loadingIndividuals = state.loadingIndividuals - individual.id,
                                error = "Nie udało się załadować szczegółów: ${e.message}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    _state.update { state ->
                        state.copy(
                            loadingIndividuals = state.loadingIndividuals - individual.id
                        )
                    }
                }
            }
        }
    }
}

// Supporting data classes if not already defined
