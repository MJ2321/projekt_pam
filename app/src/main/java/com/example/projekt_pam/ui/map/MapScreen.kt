package com.example.projekt_pam.ui.map

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.Study
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
    LaunchedEffect(mapView, state.studies, state.filteredIndividuals, state.selectedTrack, state.animalTracks, state.searchQuery, state.zoomClickCount, state.selectedStudy, state.isTrackMode) {
        val contentHash = (state.studies.size + state.filteredIndividuals.size +
                          state.selectedTrack.size + state.animalTracks.size + state.searchQuery.hashCode() + state.zoomClickCount +
                          (state.selectedStudy?.id?.hashCode() ?: 0) + state.isTrackMode.hashCode()).hashCode()
        
        if (contentHash != lastUpdateHash && mapView != null) {
            lastUpdateHash = contentHash
            
            // Anuluj poprzednią aktualizację jeśli istnieje
            debounceJob?.cancel()
            
            // Opóźnij aktualizację o 300ms aby nie laować
            debounceJob = scope.launch {
                delay(300)
                updateMapContent(context, mapView!!, state, viewModel::onMarkerSelected, viewModel::showTrackFor, viewModel::onStudyMarkerSelected)
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

    // Centruj mapę na pierwszym punkcie trasy
    LaunchedEffect(state.selectedTrack, mapView) {
        if (state.selectedTrack.isNotEmpty()) {
            val first = state.selectedTrack.first()
            mapView?.controller?.animateTo(GeoPoint(first.latitude, first.longitude))
        }
    }

    // Centruj i przybliż mapę na trasę z badania
    LaunchedEffect(state.animalTracks, mapView) {
        if (state.animalTracks.isNotEmpty() && mapView != null) {
            val allPoints = state.animalTracks.flatMap { track ->
                track.locations.map { GeoPoint(it.latitude, it.longitude) }
            }
            if (allPoints.isNotEmpty()) {
                val boundingBox = BoundingBox.fromGeoPointsSafe(allPoints)
                // Add a small delay to ensure the map layout is ready before zooming to bounds
                delay(100)
                mapView?.zoomToBoundingBox(boundingBox, true)
            }
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
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f),
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

            val isSelected = state.selectedStudy != null
            val hasDownloadAccess = state.selectedStudy?.accessType == com.example.projekt_pam.domain.model.AccessType.DOWNLOAD
            val isButtonEnabled = isSelected && hasDownloadAccess
            val tracksVisible = state.animalTracks.isNotEmpty() || state.selectedTrack.isNotEmpty()

            if (tracksVisible) {
                Button(
                    onClick = { viewModel.onClearTracksClicked() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color(0xFFC62828)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding()
                        .zIndex(100f)
                ) {
                    Text("Wróć do mapy")
                }
            } else {
                Button(
                    onClick = { viewModel.onShowTrackClicked() },
                    enabled = isButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonEnabled) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                        contentColor = if (isButtonEnabled) Color.White else Color(0xFF616161)
                    ),
                    border = if (isButtonEnabled) BorderStroke(2.dp, Color(0xFF1B5E20)) else null,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isButtonEnabled) 8.dp else 0.dp,
                        pressedElevation = if (isButtonEnabled) 12.dp else 0.dp
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding()
                        .zIndex(100f)
                ) {
                    Text("Wyświetl trasę")
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(16.dp)
                        .zIndex(1f)
                )
            }
            
            // Only show errors when not actively searching (to avoid confusing "server error" messages)
            val isSearching = state.searchQuery.isNotBlank()
            if (!isSearching) {
                state.error?.let { rawError ->
                    val displayError = if (
                        rawError.contains("<!doctype", ignoreCase = true) ||
                        rawError.length > 220
                    ) {
                        "Serwer chwilowo zwraca błąd. Spróbuj ponownie za moment."
                    } else {
                        rawError
                    }
                    Text(
                        text = "Error: $displayError",
                        color = Color.Red,
                        modifier = Modifier
                            .padding(16.dp)
                            .zIndex(1f)
                    )
                }
            }

            // Obsługa dialogu licencji
            if (state.licenseDialog.show) {
                AlertDialog(
                    onDismissRequest = { viewModel.onLicenseDeclined() },
                    title = { Text("Wymagana akceptacja licencji") },
                    text = {
                        val scrollState = rememberScrollState()
                        Column(modifier = Modifier.verticalScroll(scrollState)) {
                            // Konwertowanie HTML z licencji na czysty tekst
                            Text(text = android.text.Html.fromHtml(
                                state.licenseDialog.licenseText, 
                                android.text.Html.FROM_HTML_MODE_COMPACT
                            ).toString())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onLicenseAccepted() }) {
                            Text("Akceptuj")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onLicenseDeclined() }) {
                            Text("Odrzuć")
                        }
                    }
                )
            }
        }
    }
}

private fun updateMapContent(
    context: Context,
    mapView: MapView,
    state: MapState,
    onMarkerSelected: (Individual) -> Unit,
    onShowTrack: (Individual) -> Unit,
    onStudySelected: (Study) -> Unit
) {
    mapView.overlays.clear()

    val isSearchActive = state.searchQuery.trim().isNotEmpty()
    var infoWindow = AnimalInfoWindow(mapView, onShowTrack)

    if (state.isTrackMode && state.selectedStudy != null) {
        val study = state.selectedStudy
        val marker = Marker(mapView).apply {
            position = GeoPoint(study.latitude, study.longitude)
            title = study.name
            subDescription = "Study ID: ${study.id}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
    } else {
        // Zawsze obsługujemy wyświetlanie badań, ewentualnie przefiltrowanych
        val studiesToShow = if (isSearchActive) {
            state.studies.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        } else {
            state.studies
        }

        studiesToShow.forEach { study ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(study.latitude, study.longitude)
                title = study.name
                subDescription = "Study ID: ${study.id}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                if (study.accessType == com.example.projekt_pam.domain.model.AccessType.DOWNLOAD) {
                    icon = androidx.core.content.ContextCompat.getDrawable(context, com.example.projekt_pam.R.drawable.ic_blue_pin)
                }
                setOnMarkerClickListener { clicked, _ ->
                    onStudySelected(study)
                    clicked.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        
        // Dodatkowo pokazujemy indywidualne zwierzęta np. przy podwójnym zoomie, tak jak do tej pory z DEFAULT_STUDY_ID
        if (isSearchActive) {
            state.filteredIndividuals.forEach { individual ->
                val lat = individual.lastLat ?: return@forEach
                val lon = individual.lastLon ?: return@forEach

                val marker = Marker(mapView).apply {
                    position = GeoPoint(lat, lon)
                    title = individual.identifier
                    subDescription = "${individual.taxon}\nLat: ${String.format(Locale.US, "%.4f", lat)}\nLon: ${String.format(Locale.US, "%.4f", lon)}"
                    relatedObject = individual
                    infoWindow = infoWindow
                    setOnMarkerClickListener { clicked, _ ->
                        onMarkerSelected(individual)
                        clicked.showInfoWindow()
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
        }
    }

    // Rysowanie trasy (Polyline) po kliknięciu
    if (state.selectedTrack.isNotEmpty()) {
        val tracksByIndividual = state.selectedTrack
            .groupBy { it.individualId }
            .entries
            .take(3) // Pokaż max 3 zwierzęta zamiast 5

        tracksByIndividual.forEach { (_, events) ->
            // Bierzemy co 3 punkt dla jeszcze większej płynności (lub wszystkie, jeśli mało)
            val simplifiedEvents = if (events.size > 50) events.filterIndexed { index, _ -> index % 3 == 0 } else events
            val points = simplifiedEvents.map { GeoPoint(it.latitude, it.longitude) }
            if (points.size > 1) {
                val polyline = Polyline().apply {
                    setPoints(points)
                    isGeodesic = true
                    outlinePaint.color = android.graphics.Color.parseColor("#800080")
                    outlinePaint.strokeWidth = 6f
                }
                mapView.overlays.add(polyline) // Dodajemy TYLKO linię. Brak markerów dla każdego punktu drastycznie przyspieszy renderowanie.
            }
        }
    }

    if (state.animalTracks.isNotEmpty()) {
        state.animalTracks.take(3).forEach { track -> // Pokaż max 3 zwierzęta zamiast 5
            val simplifiedLocations = if (track.locations.size > 50) track.locations.filterIndexed { index, _ -> index % 3 == 0 } else track.locations
            val points = simplifiedLocations.map { GeoPoint(it.latitude, it.longitude) }
            if (points.size > 1) {
                val polyline = Polyline().apply {
                    setPoints(points)
                    isGeodesic = true // Follow Earth's curvature for long distances
                    outlinePaint.color = android.graphics.Color.parseColor("#800080")
                    outlinePaint.strokeWidth = 6f
                }
                mapView.overlays.add(polyline)
            }
        }
    }
    
    mapView.invalidate() // Odświeżenie widoku mapy
}
