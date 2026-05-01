package com.example.projekt_pam.domain.model

data class Location(val latitude: Double, val longitude: Double)

enum class AccessType {
    DOWNLOAD, VIEW_ONLY
}

data class Study(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accessType: AccessType = AccessType.VIEW_ONLY
)

data class AnimalTrack(
    val individualId: Long,
    val locations: List<Location>
)

data class Individual(
    val id: Long,
    val identifier: String,
    val taxon: String,
    val lastLat: Double? = null,
    val lastLon: Double? = null
)

data class SensorEvent(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val individualId: Long
)
