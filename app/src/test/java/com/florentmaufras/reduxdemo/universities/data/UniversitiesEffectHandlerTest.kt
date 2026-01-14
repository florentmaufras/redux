package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

class UniversitiesEffectHandlerTest {

    private val mockedUniversitiesService: UniversitiesService = mockk()
    private val mockedTimber: Timber.Tree = mockk()

    private lateinit var universitiesEffectHandler: UniversitiesEffectHandler

    private val country = "country"

    @BeforeEach
    fun setup() {
        universitiesEffectHandler = UniversitiesEffectHandler(
            mockedUniversitiesService,
            mockedTimber
        )
    }

    @Test
    fun handle_shouldReturnUniversitiesLoaded_whenSuccessful() = runTest {
        val universities = arrayListOf<University>()

        coEvery { mockedUniversitiesService.getUniversities(country) }.coAnswers { universities }

        assertEquals(
            UniversitiesAction.UniversitiesLoaded(universities),
            universitiesEffectHandler.handle(UniversitiesEffect.LoadUniversities(country))
        )
    }

    @Test
    fun handle_shouldReturnLoadError_whenNotSuccessful() = runTest {
        coEvery { mockedUniversitiesService.getUniversities(country) }.throws(Exception())
        justRun { mockedTimber.e(any<Exception>()) }

        assertEquals(
            UniversitiesAction.LoadError,
            universitiesEffectHandler.handle(UniversitiesEffect.LoadUniversities(country))
        )
    }
}