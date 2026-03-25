package com.example.projekt_pam.data.remote

import com.example.projekt_pam.data.remote.dto.IndividualDto
import com.example.projekt_pam.data.remote.dto.SensorEventDto
import com.example.projekt_pam.data.remote.dto.StudyDto
import retrofit2.http.GET
import retrofit2.http.Query

interface MovebankApi {
    @GET("json")
    suspend fun getStudies(
        @Query("entity_type") entityType: String = "study",
        @Query("has_study_individual_sensors") hasSensors: Boolean = true
    ): List<StudyDto>

    @GET("json")
    suspend fun getIndividuals(
        @Query("entity_type") entityType: String = "individual",
        @Query("study_id") studyId: Long
    ): List<IndividualDto>

    @GET("json")
    suspend fun getEvents(
        @Query("entity_type") entityType: String = "event",
        @Query("study_id") studyId: Long,
        @Query("individual_id") individualId: Long? = null
    ): List<SensorEventDto>

    companion object {
        const val BASE_URL = "https://www.movebank.org/movebank/service/"
    }
}
