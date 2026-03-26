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

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

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
        _state.update { it.copy(searchQuery = query) }
        // Add logic to filter state.filteredIndividuals here
    }

    /**
     * This is the missing function causing your error.
     */
    fun selectIndividual(studyId: Long, individual: Individual) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // Replace this with your actual API/Repository call
                // val track = repository.getTrack(studyId, individual.id)

                // Example: updating the state with the selected track
                // _state.update { it.copy(selectedTrack = track, isLoading = false) }
            } catch (e: Exception) {
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
                    val result = repository.getEvents(291157141, individual.id)
                    result.onSuccess { individuals ->
                        // Parsuj jako SensorEvent z Individual
                        val events = emptyList<SensorEvent>() // TODO: if API returns events

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
