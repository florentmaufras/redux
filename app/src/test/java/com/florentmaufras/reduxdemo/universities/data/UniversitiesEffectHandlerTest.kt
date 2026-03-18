package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber

class UniversitiesEffectHandlerTest {

    private val mockedUniversitiesService: UniversitiesService = mockk()
    private val mockedTimber: Timber.Tree = mockk()
    private val country = "country"

    private lateinit var universitiesEffectHandler: UniversitiesEffectHandler

    @BeforeEach
    fun setup() {
        universitiesEffectHandler = UniversitiesEffectHandler(
            mockedUniversitiesService,
            mockedTimber
        )
    }

    @Test
    fun handle_shouldEmitUniversitiesLoaded_whenSuccessful() = runTest {
        val universities = emptyList<University>()
        coEvery { mockedUniversitiesService.getUniversities(country) }.returns(universities)

        val result = universitiesEffectHandler
            .handle(UniversitiesEffect.LoadUniversities(country))
            .toList()

        assertEquals(listOf(UniversitiesAction.UniversitiesLoaded(universities)), result)
    }

    @Test
    fun handle_shouldEmitLoadError_whenNotSuccessful() = runTest {
        coEvery { mockedUniversitiesService.getUniversities(country) }.throws(Exception())
        justRun { mockedTimber.e(any<Exception>()) }

        val result = universitiesEffectHandler
            .handle(UniversitiesEffect.LoadUniversities(country))
            .toList()

        assertEquals(listOf(UniversitiesAction.LoadError(null)), result)
    }
}
