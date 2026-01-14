package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UniversitiesService(
    val baseUrl: String = "http://universities.hipolabs.com",
    val retrofitBuilder: Retrofit.Builder = Retrofit.Builder()
) {

    private val retrofit: Retrofit by lazy {
        retrofitBuilder
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val universitiesAPI by lazy { retrofit.create(UniversitiesAPI::class.java) }

    suspend fun getUniversities(country: String): ArrayList<University> =
        universitiesAPI.getUniversities(country)
}