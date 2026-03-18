package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.EffectResult
import com.florentmaufras.redux.ReduceResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversitiesReducerTest {

    private lateinit var universitiesReducer: UniversitiesReducer

    @BeforeEach
    fun setup() {
        universitiesReducer = UniversitiesReducer()
    }

    @Test
    fun reduce_shouldHandleLoadUniversitiesAndReturnLoadUniversitiesEffectAndNewState() {
        val state = UniversitiesState()
        val country = ""

        assertEquals(
            ReduceResult(
                state.copy(isLoading = true, countrySearched = country, hasError = false),
                EffectResult.Some(UniversitiesEffect.LoadUniversities(country))
            ),
            universitiesReducer.reduce(UniversitiesAction.LoadUniversities(country), state)
        )
    }

    @Test
    fun reduce_shouldHandleUniversitiesLoadedAndReturnAndNewState() {
        val state = UniversitiesState()
        val universities = emptyList<University>()

        assertEquals(
            ReduceResult(state.copy(isLoading = false, universities = universities), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.UniversitiesLoaded(universities), state)
        )
    }

    @Test
    fun reduce_shouldHandleUniversitiesLoadErrorAndReturnAndNewState() {
        val state = UniversitiesState()

        assertEquals(
            ReduceResult(state.copy(isLoading = false, hasError = true), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.LoadError, state)
        )
    }

    @Test
    fun reduce_shouldHandleUniversitiesLoadWebsiteAndReturnAndNewState() {
        val state = UniversitiesState()
        val website = ""

        assertEquals(
            ReduceResult(state.copy(website = website), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.LoadWebsite(website), state)
        )
    }

    @Test
    fun reduce_shouldHandleUniversitiesWebsiteLoadedAndReturnAndNewState() {
        val state = UniversitiesState()

        assertEquals(
            ReduceResult(state.copy(website = null), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.WebsiteLoaded, state)
        )
    }
}
