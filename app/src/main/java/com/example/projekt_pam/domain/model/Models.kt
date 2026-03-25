package com.example.projekt_pam.domain.model

data class Study(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double
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
