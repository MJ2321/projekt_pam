package com.example.projekt_pam.ui.map

import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study

data class MapState(
    val studies: List<Study> = emptyList(), // Add this line!
    val individuals: List<Individual> = emptyList(),
    val filteredIndividuals: List<Individual> = emptyList(),
    val selectedTrack: List<SensorEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)
