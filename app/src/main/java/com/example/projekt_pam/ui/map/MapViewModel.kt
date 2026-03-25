package com.example.projekt_pam.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projekt_pam.domain.model.Individual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Assuming you have a repository and data models defined elsewhere
// import com.example.projekt_pam.repository.AnimalRepository

@HiltViewModel
class MapViewModel @Inject constructor(
    // private val repository: AnimalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

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
}

// Supporting data classes if not alread