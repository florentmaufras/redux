package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import retrofit2.http.GET
import retrofit2.http.Query

interface UniversitiesAPI {

    /***
     * example: http://universities.hipolabs.com/search?country=United+States
     */
    @GET("search")
    suspend fun getUniversities(
        @Query("country") country: String
    ): ArrayList<University>
}