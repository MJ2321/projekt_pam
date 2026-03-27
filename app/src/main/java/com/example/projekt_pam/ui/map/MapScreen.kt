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
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var lastUpdateHash by remember { mutableStateOf<Int>(0) }
    var lastCenteredIndividualId by remember { mutableStateOf<Long?>(null) }
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

    LaunchedEffect(state.searchQuery, state.filteredIndividuals, mapView) {
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            lastCenteredIndividualId = null
            return@LaunchedEffect
        }

        val target = state.filteredIndividuals.firstOrNull {
            it.lastLat != null && it.lastLon != null
        }

        if (target != null && target.id != lastCenteredIndividualId) {
            mapView?.controller?.animateTo(GeoPoint(target.lastLat!!, target.lastLon!!))
            lastCenteredIndividualId = target.id
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = {
                    viewModel.onFilterChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Wpisz zwierzę i wybierz z listy...") },
                singleLine = true
            )

        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        // Nie zapętlaj mapy poza granice świata.
                        setHorizontalMapRepetitionEnabled(false)
                        setVerticalMapRepetitionEnabled(false)
                        setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
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
            
            state.error?.let { rawError ->
                val displayError = if (
                    rawError.contains("<!doctype", ignoreCase = true) ||
                    rawError.length > 220
                ) {
                    "Serwer chwilowo zwraca błąd. Spróbuj ponownie za moment."
                } else {
                    rawError
                }
                Text(text = "Error: $displayError", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private fun updateMapContent(mapView: MapView, state: MapState) {
    mapView.overlays.clear()

    val isSearchActive = state.searchQuery.trim().isNotEmpty()

    // Przy aktywnym searchu pokazujemy tylko zwierzęta i ich lokalizacje.
    if (isSearchActive) {
        state.filteredIndividuals.forEach { individual ->
            val lat = individual.lastLat ?: return@forEach
            val lon = individual.lastLon ?: return@forEach

            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                title = individual.identifier
                subDescription = "${individual.taxon}\nLat: ${String.format(Locale.US, "%.4f", lat)}\nLon: ${String.format(Locale.US, "%.4f", lon)}"
                setOnMarkerClickListener { _, _ ->
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        if (state.zoomClickCount >= 8) {
            state.detailedTracks.forEach { (_, events) ->
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
    } else {
        // Brak searcha -> pokazujemy domyślne punkty badań.
        state.studies.forEach { study ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(study.latitude, study.longitude)
                title = study.name
                subDescription = "Study ID: ${study.id}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
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
