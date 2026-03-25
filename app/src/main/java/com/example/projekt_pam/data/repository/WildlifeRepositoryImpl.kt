package com.example.projekt_pam.data.repository

import com.example.projekt_pam.data.remote.MovebankApi
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study
import com.example.projekt_pam.domain.repository.WildlifeRepository
import javax.inject.Inject

class WildlifeRepositoryImpl @Inject constructor(
    private val api: MovebankApi
) : WildlifeRepository {

    override suspend fun getStudies(): Result<List<Study>> = Result.failure(Exception("Not implemented"))

    override suspend fun getIndividuals(studyId: Long): Result<List<Individual>> = try {
        val response = api.getIndividuals(studyId = studyId)
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val individuals = parseIndividualsCsv(csv)
            Result.success(individuals)
        } else {
            Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getEvents(studyId: Long, individualId: Long?): Result<List<SensorEvent>> = try {
        val response = api.getEvents(studyId = studyId, individualId = individualId)
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val events = parseEventsCsv(csv, individualId ?: -1)
            Result.success(events)
        } else {
            Result.failure(Exception("Error: ${response.code()} - ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun parseIndividualsCsv(csv: String): List<Individual> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val headers = lines[0].split(",")
        val idIdx = headers.indexOf("id")
        val localIdIdx = headers.indexOf("local_identifier")
        val taxonIdx = headers.indexOf("taxon_canonical_name")
        val latIdx = headers.indexOf("latest_location_lat")
        val lonIdx = headers.indexOf("latest_location_long")

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            if (cols.size > maxOf(idIdx, localIdIdx, taxonIdx)) {
                Individual(
                    id = cols[idIdx].toLongOrNull() ?: 0L,
                    identifier = cols[localIdIdx].trim('"'),
                    taxon = cols[taxonIdx].trim('"'),
                    lastLat = if (latIdx != -1 && latIdx < cols.size) cols[latIdx].toDoubleOrNull() else null,
                    lastLon = if (lonIdx != -1 && lonIdx < cols.size) cols[lonIdx].toDoubleOrNull() else null
                )
            } else null
        }
    }

    private fun parseEventsCsv(csv: String, requestedIndividualId: Long): List<SensorEvent> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val headers = lines[0].split(",")
        val latIdx = headers.indexOf("location_lat")
        val lonIdx = headers.indexOf("location_long")
        val tsIdx = headers.indexOf("timestamp")
        val indIdIdx = headers.indexOf("individual_id")

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",")
            if (cols.size > maxOf(latIdx, lonIdx, tsIdx)) {
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()
                if (lat != null && lon != null) {
                    SensorEvent(
                        latitude = lat,
                        longitude = lon,
                        timestamp = 0,
                        individualId = if (indIdIdx != -1 && indIdIdx < cols.size) cols[indIdIdx].toLongOrNull() ?: requestedIndividualId else requestedIndividualId
                    )
                } else null
            } else null
        }
    }
}
