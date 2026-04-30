package com.example.projekt_pam.ui.map

import android.widget.TextView
import com.example.projekt_pam.R
import com.example.projekt_pam.domain.model.Individual
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.util.Locale

class AnimalInfoWindow(
    mapView: MapView,
    private val onShowTrack: (Individual) -> Unit
) : InfoWindow(R.layout.map_marker_info, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val individual = marker.relatedObject as? Individual ?: return

        val titleView = mView.findViewById<TextView>(R.id.marker_title)
        val detailsView = mView.findViewById<TextView>(R.id.marker_details)

        titleView.text = individual.identifier
        detailsView.text = String.format(
            Locale.US,
            "%s\nLat: %.4f\nLon: %.4f",
            individual.taxon,
            individual.lastLat ?: 0.0,
            individual.lastLon ?: 0.0
        )
    }

    override fun onClose() {
        // No-op
    }
}
