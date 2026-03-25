package com.example.projekt_pam.ui.map

import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent

data class MapState(
    val isLoading: Boolean = false,
    val individuals: List<Individual> = emptyList(),
    val filteredIndividuals: List<Individual> = emptyList(),
    val selectedTrack: List<SensorEvent> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
)
