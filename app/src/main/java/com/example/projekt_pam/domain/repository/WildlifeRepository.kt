package com.example.projekt_pam.domain.repository

import com.example.projekt_pam.domain.model.AnimalTrack
import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study
import com.example.projekt_pam.util.Resource

interface WildlifeRepository {
    suspend fun getStudies(): Resource<List<Study>>
    suspend fun getIndividuals(studyId: Long): Resource<List<Individual>>
    suspend fun getEvents(studyId: Long, individualId: Long?): Resource<List<SensorEvent>>
    suspend fun getStudyTracks(studyId: Long, licenseMd5: String? = null): Resource<List<AnimalTrack>>
}
