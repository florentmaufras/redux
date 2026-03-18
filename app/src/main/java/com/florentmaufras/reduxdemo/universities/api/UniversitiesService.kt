package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import retrofit2.Retrofit

class UniversitiesService(retrofit: Retrofit) {
    private val api: UniversitiesAPI = retrofit.create(UniversitiesAPI::class.java)

    suspend fun getUniversities(country: String): List<University> =
        api.getUniversities(country)
}
