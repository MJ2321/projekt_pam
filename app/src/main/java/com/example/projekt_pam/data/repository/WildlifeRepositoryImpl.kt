package com.example.projekt_pam.data.repository

import com.example.projekt_pam.data.remote.MovebankApi
import com.example.projekt_pam.domain.model.AccessType
import com.example.projekt_pam.domain.model.AnimalTrack
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.Location
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study
import com.example.projekt_pam.domain.repository.WildlifeRepository
import com.example.projekt_pam.util.Resource
import java.security.MessageDigest
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

    private val tracksCache = java.util.concurrent.ConcurrentHashMap<Long, List<AnimalTrack>>()

    override suspend fun getStudies(): Resource<List<Study>> = try {
        // Fetch all viewable studies
        val allResponse = executeWithRateLimitRetry { api.getAllStudies() }
        
        // Fetch downloadable study IDs
        val downloadableResponse = executeWithRateLimitRetry { api.getDownloadableStudies() }

        if (allResponse.isSuccessful && downloadableResponse.isSuccessful) {
            val allCsv = allResponse.body()?.string().orEmpty()
            val downloadCsv = downloadableResponse.body()?.string().orEmpty()

            val downloadableIds = parseDownloadableIdsCsv(downloadCsv)
            val studies = parseStudiesCsv(allCsv, downloadableIds)
            
            Resource.Success(studies)
        } else {
            val errorMsg = if (!allResponse.isSuccessful) {
                allResponse.errorBody()?.string() ?: rateLimitFriendlyMessage(allResponse.code())
            } else {
                downloadableResponse.errorBody()?.string() ?: rateLimitFriendlyMessage(downloadableResponse.code())
            }
            Resource.Error("HTTP Error: $errorMsg")
        }
    } catch (e: Exception) {
        Resource.Error("Failed to fetch studies: ${e.message}")
    }

    override suspend fun getIndividuals(studyId: Long): Resource<List<Individual>> = try {
        val response = executeWithRateLimitRetry { api.getIndividuals(studyId = studyId) }
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val individuals = parseIndividualsCsv(csv)
            Resource.Success(individuals)
        } else {
            val errorMsg = response.errorBody()?.string() ?: rateLimitFriendlyMessage(response.code())
            Resource.Error("HTTP ${response.code()}: $errorMsg")
        }
    } catch (e: Exception) {
        Resource.Error("Failed to fetch individuals: ${e.message}")
    }

    override suspend fun getEvents(studyId: Long, individualId: Long?): Resource<List<SensorEvent>> = try {
        val response = executeWithRateLimitRetry { api.getEvents(studyId = studyId, individualId = individualId) }
        if (response.isSuccessful) {
            val csv = response.body()?.string().orEmpty()
            val events = parseEventsCsv(csv, individualId ?: 0L)
            Resource.Success(events)
        } else {
            val errorBody = response.errorBody()?.string() ?: rateLimitFriendlyMessage(response.code())
            Resource.Error("HTTP ${response.code()}: $errorBody")
        }
    } catch (e: Exception) {
        Resource.Error("Failed to fetch events: ${e.message}")
    }

    override suspend fun getStudyTracks(studyId: Long, licenseMd5: String?): Resource<List<AnimalTrack>> {
        if (licenseMd5 == null && tracksCache.containsKey(studyId)) {
            return Resource.Success(tracksCache[studyId]!!)
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val baseUrl = "https://www.movebank.org/movebank/service/direct-read?entity_type=event&study_id=$studyId&sensor_type_id=653&attributes=timestamp,location_lat,location_long,individual_id&max_events_per_individual=5000&format=json"
            val url = if (licenseMd5 != null) "$baseUrl&license-md5=$licenseMd5" else baseUrl

            try {
                val response = executeWithRateLimitRetry { api.getEvents(url) }

                if (response.isSuccessful) {
                    val body = response.body() ?: return@withContext Resource.Success(emptyList())
                    val responseBytes = body.bytes() // This is what triggers the exception if on main thread!
                    val responseBody = String(responseBytes, Charsets.UTF_8).trim()

                    android.util.Log.d("WildlifeRepo", "Track response length: ${responseBody.length}, first 200 chars: ${responseBody.take(200)}")

                    if (responseBody.contains("License Terms:")) {
                        return@withContext Resource.LicenseRequired(responseBody)
                    }

                    if (responseBody.startsWith("<p>No")) {
                        return@withContext Resource.Error("Brak uprawnie\u0144 lub brak danych (odmowa dost\u0119pu).")
                    }

                    if (responseBody.isEmpty()) {
                        return@withContext Resource.Success(emptyList())
                    }

                    // Try JSON parsing first, fall back to CSV
                    val tracks = if (responseBody.trimStart().startsWith("[")) {
                        parseJsonTracksStream(java.io.ByteArrayInputStream(responseBytes))
                    } else {
                        parseTracks(responseBody)
                    }
                    android.util.Log.d("WildlifeRepo", "Parsed ${tracks.size} tracks, points: ${tracks.sumOf { it.locations.size }}")
                    tracksCache[studyId] = tracks
                    Resource.Success(tracks)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    if (errorBody.contains("License Terms:")) {
                        val md5 = calculateMd5(errorBody)
                        return@withContext getStudyTracks(studyId, md5)
                    }
                    Resource.Error("API Error: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                Resource.Error("Network Error (${e.javaClass.simpleName}): ${e.message ?: "unknown"}")
            }
        }
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

    private fun parseDownloadableIdsCsv(csv: String): Set<Long> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptySet()

        val headers = lines[0].split(",")
        val idIdx = headers.indexOf("id")
        if (idIdx == -1) return emptySet()

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            if (cols.size > idIdx) {
                cols[idIdx].toLongOrNull()
            } else null
        }.toSet()
    }

    private fun parseStudiesCsv(csv: String, downloadableIds: Set<Long>): List<Study> {
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
                val id = cols[idIdx].toLongOrNull() ?: 0L
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()

                if (lat != null && lon != null) {
                    val accessType = if (downloadableIds.contains(id)) AccessType.DOWNLOAD else AccessType.VIEW_ONLY
                    Study(
                        id = id,
                        name = cols[nameIdx].trim('"'),
                        latitude = lat,
                        longitude = lon,
                        accessType = accessType
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

    private fun calculateMd5(input: String): String {
        return MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }

    private fun parseTracks(csvString: String): List<AnimalTrack> {
        val lines = csvString.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()

        val headers = lines[0].split(",")
        val latIdx = headers.indexOf("location_lat")
        val lonIdx = headers.indexOf("location_long")
        val indIdIdx = headers.indexOf("individual_id")

        if (latIdx == -1 || lonIdx == -1 || indIdIdx == -1) return emptyList()

        val locationsByIndividual = mutableMapOf<Long, MutableList<Location>>()

        lines.drop(1).forEach { line ->
            val cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            if (cols.size > maxOf(latIdx, lonIdx, indIdIdx)) {
                val lat = cols[latIdx].toDoubleOrNull()
                val lon = cols[lonIdx].toDoubleOrNull()
                val individualId = cols[indIdIdx].toLongOrNull()

                if (lat != null && lon != null && individualId != null) {
                    locationsByIndividual.getOrPut(individualId) { mutableListOf() }.add(Location(lat, lon))
                }
            }
        }

        return locationsByIndividual.map { (id, locations) ->
            AnimalTrack(individualId = id, locations = locations)
        }
    }

    private fun parseJsonTracksStream(inputStream: java.io.InputStream): List<AnimalTrack> {
        val locationsByIndividual = mutableMapOf<Long, MutableList<Location>>()
        val eventCountsByIndividual = mutableMapOf<Long, Int>()
        val reader = android.util.JsonReader(java.io.InputStreamReader(inputStream, "UTF-8"))
        reader.isLenient = true
        var eventCount = 0
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var lat: Double? = null
                var lon: Double? = null
                var individualId: Long? = null
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    when (key) {
                        "location-lat", "location_lat" -> {
                            val raw = reader.nextString()
                            lat = raw.toDoubleOrNull()
                        }
                        "location-long", "location_long" -> {
                            val raw = reader.nextString()
                            lon = raw.toDoubleOrNull()
                        }
                        "individual-id", "individual_id" -> {
                            val raw = reader.nextString()
                            individualId = raw.toLongOrNull()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                eventCount++
                if (lat != null && lon != null && individualId != null) {
                    val count = eventCountsByIndividual.getOrDefault(individualId, 0)
                    // Keep 1 out of every 500 points to spread them far apart in time
                    if (count % 500 == 0) {
                        locationsByIndividual.getOrPut(individualId) { mutableListOf() }.add(Location(lat, lon))
                    }
                    eventCountsByIndividual[individualId] = count + 1
                }
            }
            reader.endArray()
        } catch (e: Exception) {
            android.util.Log.e("WildlifeRepo", "JSON parse error after $eventCount events: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            reader.close()
        }
        android.util.Log.d("WildlifeRepo", "JSON parsed $eventCount events -> ${locationsByIndividual.size} individuals")
        return locationsByIndividual.map { AnimalTrack(it.key, it.value) }
    }
}