package com.example.projekt_pam.ui.map

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastUpdateHash by remember { mutableStateOf<Int>(0) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Inicjalizacja osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Obserwuj zmiany stanu i aktualizuj mapę z debouncing
    LaunchedEffect(state.studies, state.filteredIndividuals, state.selectedTrack, state.searchQuery, state.zoomClickCount) {
        val contentHash = (state.studies.size + state.filteredIndividuals.size + 
                          state.selectedTrack.size + state.searchQuery.hashCode() + state.zoomClickCount).hashCode()
        
        if (contentHash != lastUpdateHash && mapView != null) {
            lastUpdateHash = contentHash
            
            // Anuluj poprzednią aktualizację jeśli istnieje
            debounceJob?.cancel()
            
            // Opóźnij aktualizację o 300ms aby nie laować
            debounceJob = scope.launch {
                delay(300)
                updateMapContent(mapView!!, state)
            }
        }
    }

    // Monitoruj zmiany zoomu i załaduj szczegóły jeśli zbliżamy się
    LaunchedEffect(mapView, state.currentZoom) {
        mapView?.let { map ->
            val currentZoom = map.zoomLevel.toDouble()
            if (currentZoom != state.currentZoom) {
                viewModel.onZoomChanged(currentZoom)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onFilterChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Filtruj gatunek lub ID (OSM)...") },
            singleLine = true
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(20.0, 10.0))
                    }
                },
                update = { map ->
                    mapView = map
                }
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            
            state.error?.let {
                Text(text = "Error: $it", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private fun updateMapContent(mapView: MapView, state: MapState) {
    mapView.overlays.clear()

    // Dodawanie markerów dla przefiltrowanych zwierząt
    state.filteredIndividuals.forEach { individual ->
        // Jeśli licznik >= 12, pokazuj szczegóły (rzeczywiste pozycje)
        val lat = if (state.zoomClickCount >= 12) {
            individual.lastLat ?: 0.0
        } else {
            0.0
        }
        val lon = if (state.zoomClickCount >= 12) {
            individual.lastLon ?: 0.0
        } else {
            0.0
        }

        val marker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            title = individual.identifier
            // Szczegóły zależne od licznika kliknięć
            subDescription = if (state.zoomClickCount >= 12) {
                "${individual.taxon}\nLat: ${String.format("%.4f", lat)}\nLon: ${String.format("%.4f", lon)}"
            } else {
                individual.taxon
            }
            setOnMarkerClickListener { _, _ ->
                true
            }
        }
        mapView.overlays.add(marker)
    }

    // Dodawanie badań jako punkty na mapie
    state.studies.forEach { study ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(study.latitude, study.longitude)
            title = study.name
            subDescription = "Study ID: ${study.id}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
    }

    // Gdy licznik >= 12, pokaż szczegółowe ścieżki dla załadowanych zwierząt
    if (state.zoomClickCount >= 12) {
        state.detailedTracks.forEach { (individualId, events) ->
            if (events.isNotEmpty()) {
                val polyline = Polyline().apply {
                    setPoints(events.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                mapView.overlays.add(polyline)
            }
        }
    }

    // Rysowanie trasy (Polyline) po kliknięciu
    if (state.selectedTrack.isNotEmpty()) {
        val polyline = Polyline().apply {
            setPoints(state.selectedTrack.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.color = android.graphics.Color.RED
            outlinePaint.strokeWidth = 8f
        }
        mapView.overlays.add(polyline)
    }
    
    mapView.invalidate() // Odświeżenie widoku mapy
}
