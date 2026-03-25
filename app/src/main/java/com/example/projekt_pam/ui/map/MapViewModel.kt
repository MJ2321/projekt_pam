package com.example.projekt_pam.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.repository.WildlifeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: WildlifeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapState())
    val state = _state.asStateFlow()

    // ID badania: LifeTrack White Stork SW Germany (Tysiące punktów!)
    private val CURRENT_STUDY_ID = 291157141L

    init {
        loadIndividuals(CURRENT_STUDY_ID) 
    }

    fun loadIndividuals(studyId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            // Pobieramy listę osobników
            repository.getIndividuals(studyId)
                .onSuccess { list ->
                    _state.update { it.copy(
                        isLoading = false,
                        individuals = list,
                        filteredIndividuals = list
                    ) }
                }
                .onFailure { error ->
                    _state.update { it.copy(
                        isLoading = false,
                        error = "Błąd połączenia: ${error.localizedMessage}. Sprawdź internet!"
                    ) }
                }
        }
    }

    fun onFilterChanged(query: String) {
        _state.update { currentState ->
            val filtered = if (query.isEmpty()) {
                currentState.individuals
            } else {
                currentState.individuals.filter {
                    it.taxon.contains(query, ignoreCase = true) ||
                    it.identifier.contains(query, ignoreCase = true)
                }
            }
            currentState.copy(
                searchQuery = query,
                filteredIndividuals = filtered
            )
        }
    }

    fun selectIndividual(studyId: Long, individual: Individual) {
        viewModelScope.launch {
            repository.getEvents(studyId, individual.id)
                .onSuccess { track ->
                    _state.update { it.copy(selectedTrack = track) }
                }
        }
    }
}
