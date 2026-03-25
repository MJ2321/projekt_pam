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
    override suspend fun getStudies(): Result<List<Study>> = try {
        val studies = api.getStudies().map { 
            Study(it.id, it.name, it.main_location_lat, it.main_location_long)
        }
        Result.success(studies)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getIndividuals(studyId: Long): Result<List<Individual>> = try {
        val individuals = api.getIndividuals(studyId = studyId).map {
            Individual(it.id, it.local_identifier, it.taxon_canonical_name)
        }
        Result.success(individuals)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun getEvents(studyId: Long, individualId: Long?): Result<List<SensorEvent>> = try {
        val events = api.getEvents(studyId = studyId, individualId = individualId)
            .filter { it.location_lat != null && it.location_long != null }
            .map {
                SensorEvent(it.location_lat!!, it.location_long!!, it.timestamp, it.individual_id)
            }
        Result.success(events)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
