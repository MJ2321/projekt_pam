package com.example.projekt_pam.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MovebankApi {

    @GET("direct-read")
    suspend fun getIndividuals(
        @Query("entity_type") entityType: String = "individual",
        @Query("study_id") studyId: Long,
        @Query("license_accepted") licenseAccepted: Boolean = true
    ): Response<ResponseBody>

    @GET("direct-read")
    suspend fun getAllStudies(
        @Query("entity_type") entityType: String = "study",
        @Query("study_id") studyId: String = "2978860867,10449318,3000102503,205020261,358632085", // Ograniczamy do kilku przykładowych otwartych badań
        @Query("attributes") attributes: String = "id,name,main_location_lat,main_location_long,i_have_download_access",
        @Query("license_accepted") licenseAccepted: Boolean = true
    ): Response<ResponseBody>

    @GET("direct-read")
    suspend fun getEvents(
        @Query("entity_type") entityType: String = "event",
        @Query("study_id") studyId: Long,
        @Query("individual_id") individualId: Long? = null,
        @Query("attributes") attributes: String = "timestamp,location_lat,location_long,individual_id",
        @Query("license_accepted") licenseAccepted: Boolean = true
    ): Response<ResponseBody>

    // Pobiera ostatnią znaną pozycję dla KAŻDEGO zwierzęcia w badaniu
    @GET("direct-read")
    suspend fun getLatestEventsForAll(
        @Query("entity_type") entityType: String = "event",@Query("study_id") studyId: Long,
        @Query("sensor_type_id") sensorTypeId: Long = 653, // Add this for GPS
        @Query("max_events_per_individual") maxEvents: Int = 1,
        @Query("attributes") attributes: String = "location_lat,location_long,individual_id",
        @Query("license_accepted") licenseAccepted: Boolean = true
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://www.movebank.org/movebank/service/"
    }
}
