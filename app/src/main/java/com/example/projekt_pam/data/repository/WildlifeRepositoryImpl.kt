package com.example.projekt_pam.data.repository

import com.example.projekt_pam.data.remote.MovebankApi
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study
import com.example.projekt_pam.domain.repository.WildlifeRepository
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

class WildlifeRepositoryImpl @Inject constructor(
    private val api: MovebankApi
) : WildlifeRepository {

    private companion object {
        const val MAX_RETRIES_ON_429 = 2
        const val INITIAL_BACKOFF_MS = 1_000L
    }

    override suspend fun getStudies(): Result<List<Study>> = try {
        val response = executeWithRateLimitRetry { api.getAllStudies() }
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            Result.success(parseStudiesCsv(csv))
        } else {
            val errorMsg = response.errorBody()?.string() ?: rateLimitFriendlyMessage(response.code())
            Result.failure(Exception("HTTP ${response.code()}: $errorMsg"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getIndividuals(studyId: Long): Result<List<Individual>> = try {
        val response = executeWithRateLimitRetry { api.getIndividuals(studyId = studyId) }
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val individuals = parseIndividualsCsv(csv)
            Result.success(individuals)
        } else {
            val errorMsg = response.errorBody()?.string() ?: rateLimitFriendlyMessage(response.code())
            Result.failure(Exception("HTTP ${response.code()}: $errorMsg"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getEvents(studyId: Long, individualId: Long?): Result<List<SensorEvent>> = try {
        val response = executeWithRateLimitRetry { api.getEvents(studyId = studyId, individualId = individualId) }
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val events = parseEventsCsv(csv, individualId ?: 0L)
            Result.success(events)
        } else {
            val errorBody = response.errorBody()?.string() ?: rateLimitFriendlyMessage(response.code())
            Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun executeWithRateLimitRetry(
        request: suspend () -> Response<ResponseBody>
    ): Response<ResponseBody> {
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS

        while (true) {
            val response = request()
            if (response.code() != 429 || attempt >= MAX_RETRIES_ON_429) {
                return response
            }

            val retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
            response.errorBody()?.close()
            response.body()?.close()

            kotlinx.coroutines.delay(retryAfterSeconds?.times(1_000L) ?: backoffMs)
            backoffMs *= 2
            attempt++
        }
    }

    private fun rateLimitFriendlyMessage(code: Int): String {
        return if (code == 429) {
            "Za dużo zapytań do serwera (HTTP 429). Odczekaj chwilę i spróbuj ponownie."
        } else {
            "Unknown Server Error"
        }
    }

    private fun parseStudiesCsv(csv: String): List<Study> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val headers = lines[0].split(",")
        val idIdx = headers.indexOf("id")
        val nameIdx = headers.indexOf("name")
        val latIdx = headers.indexOf("main_location_lat")
        val lonIdx = headers.indexOf("main_location_long")

        return lines.drop(1).mapNotNull { line ->
            // This regex is important! It handles commas inside "Study Name, Country"
            val cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())

            if (cols.size > maxOf(idIdx, nameIdx, latIdx, lonIdx)) {
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()

                if (lat != null && lon != null) {
                    Study(
                        id = cols[idIdx].toLongOrNull() ?: 0L,
                        name = cols[nameIdx].trim('"'),
                        latitude = lat,
                        longitude = lon
                    )
                } else null
            } else null
        }
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

        // Jeśli brakuje wymaganych kolumn, nie próbuj parsować
        if (latIdx == -1 || lonIdx == -1) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            if (cols.size > maxOf(latIdx, lonIdx, tsIdx)) {
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()
                if (lat != null && lon != null) {
                    SensorEvent(
                        latitude = lat,
                        longitude = lon,
                        timestamp = if (tsIdx != -1 && tsIdx < cols.size) 0L else 0L,
                        individualId = if (indIdIdx != -1 && indIdIdx < cols.size)
                            cols[indIdIdx].toLongOrNull() ?: requestedIndividualId
                        else requestedIndividualId
                    )
                } else null
            } else null
        }
    }
}