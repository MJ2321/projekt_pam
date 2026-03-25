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

    // Inicjalizacja osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
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
                update = { mapView ->
                    mapView.overlays.clear()

                    // Dodawanie markerów dla przefiltrowanych zwierząt
                    state.filteredIndividuals.forEach { individual ->
                        val marker = Marker(mapView).apply {
                            // Domyślna pozycja (w realnej apce pobrana z ostatniego eventu)
                            position = GeoPoint(0.0, 0.0)
                            title = individual.identifier
                            subDescription = individual.taxon
                            setOnMarkerClickListener { _, _ ->
                                viewModel.selectIndividual(291157141, individual)
                                showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    }

                    // Rysowanie trasy (Polyline) po kliknięciu
                    if (state.selectedTrack.isNotEmpty()) {
                        val polyline = Polyline().apply {
                            setPoints(state.selectedTrack.map { GeoPoint(it.latitude, it.longitude) })
                            outlinePaint.color = android.graphics.Color.BLUE
                            outlinePaint.strokeWidth = 8f
                        }
                        mapView.overlays.add(polyline)
                    }
                    
                    mapView.invalidate() // Odświeżenie widoku mapy
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
