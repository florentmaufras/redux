package com.florentmaufras.reduxdemo.universities.api

import com.florentmaufras.reduxdemo.universities.data.University
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class UniversitiesServiceTest {
    private val baseUrl = "https://dummy.com"
    private val mockedRetrofitBuilder = mockk<Retrofit.Builder>()

    private lateinit var universitiesService: UniversitiesService

    @BeforeEach
    fun setup() {
        universitiesService = UniversitiesService(
            baseUrl,
            mockedRetrofitBuilder
        )
    }

    @Test
    fun getUniversities_should() = runTest {
        val expectedResult = arrayListOf<University>()

        val country = "country"
        val mockedRetrofit = mockk<Retrofit>()
        val mockedUniversitiesAPI = mockk<UniversitiesAPI>()

        every { mockedRetrofitBuilder.baseUrl(baseUrl) }.returns(mockedRetrofitBuilder)
        every { mockedRetrofitBuilder.addConverterFactory(any()) }.returns(mockedRetrofitBuilder)
        every { mockedRetrofitBuilder.build() }.returns(mockedRetrofit)
        every { mockedRetrofit.create<UniversitiesAPI>(any()) }.returns(mockedUniversitiesAPI)
        coEvery { mockedUniversitiesAPI.getUniversities(country) }.returns(expectedResult)

        assertEquals(expectedResult, universitiesService.getUniversities(country))
    }
}