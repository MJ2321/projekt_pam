package com.example.projekt_pam.ui.map

import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study

data class MapState(
    val studies: List<Study> = emptyList(),
    val individuals: List<Individual> = emptyList(),
    val filteredIndividuals: List<Individual> = emptyList(),
    val selectedTrack: List<SensorEvent> = emptyList(),
    val selectedIndividual: Individual? = null,
    val selectedStudy: Study? = null,
    val isTrackMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    // Zapisane położenie i zoom mapy
    val mapCenterLatitude: Double = 20.0,
    val mapCenterLongitude: Double = 10.0,
    val mapZoom: Double = 3.0,
    // Zoom level do śledzenia szczegółowości mapy
    val currentZoom: Double = 3.0,
    // Licznik kliknięć zoomu (po 8 kliknięciach pojawią się szczegóły)
    val zoomClickCount: Int = 0,
    // Mapa: individualId -> lista SensorEvent (ścieżek)
    val detailedTracks: Map<Long, List<SensorEvent>> = emptyMap(),
    // Które zwierzęta mają załadowane szczegóły
    val loadedDetailIndividuals: Set<Long> = emptySet(),
    // Flagi ładowania dla poszczególnych zwierząt
    val loadingIndividuals: Set<Long> = emptySet()
)
