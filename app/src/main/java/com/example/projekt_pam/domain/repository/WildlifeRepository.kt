package com.example.projekt_pam.domain.repository

import com.example.projekt_pam.domain.model.Individual
import com.example.projekt_pam.domain.model.SensorEvent
import com.example.projekt_pam.domain.model.Study

interface WildlifeRepository {
    suspend fun getStudies(): Result<List<Study>>
    suspend fun getIndividuals(studyId: Long): Result<List<Individual>>
    suspend fun getEvents(studyId: Long, individualId: Long?): Result<List<Individual>>
}
