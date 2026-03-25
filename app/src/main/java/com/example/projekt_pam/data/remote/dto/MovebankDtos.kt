package com.example.projekt_pam.data.remote.dto

data class StudyDto(
    val id: Long,
    val name: String,
    val main_location_lat: Double,
    val main_location_long: Double
)

data class IndividualDto(
    val id: Long,
    val local_identifier: String,
    val taxon_canonical_name: String
)

data class SensorEventDto(
    val location_lat: Double?,
    val location_long: Double?,
    val timestamp: Long,
    val individual_id: Long
)
