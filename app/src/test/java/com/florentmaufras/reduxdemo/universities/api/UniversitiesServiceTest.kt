package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class UniversitiesServiceTest {

    @Test
    fun getUniversities_shouldDelegateToApi() = runTest {
        val country = "country"
        val expected = emptyList<University>()
        val mockedApi = mockk<UniversitiesAPI>()
        val mockedRetrofit = mockk<Retrofit>()

        every { mockedRetrofit.create(UniversitiesAPI::class.java) }.returns(mockedApi)
        coEvery { mockedApi.getUniversities(country) }.returns(expected)

        val service = UniversitiesService(mockedRetrofit)

        assertEquals(expected, service.getUniversities(country))
    }
}
