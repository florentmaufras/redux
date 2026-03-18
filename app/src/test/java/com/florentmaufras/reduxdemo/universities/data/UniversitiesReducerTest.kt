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
                state.copy(viewState = ViewState.Loading, countrySearched = country),
                EffectResult.Some(UniversitiesEffect.LoadUniversities(country))
            ),
            universitiesReducer.reduce(UniversitiesAction.LoadUniversities(country), state)
        )
    }

    @Test
    fun reduce_shouldHandleUniversitiesLoadedAndReturnNewState() {
        val state = UniversitiesState()
        val universities = emptyList<University>()

        assertEquals(
            ReduceResult(state.copy(viewState = ViewState.Loaded(universities)), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.UniversitiesLoaded(universities), state)
        )
    }

    @Test
    fun reduce_shouldHandleLoadErrorAndReturnNewState() {
        val state = UniversitiesState()

        assertEquals(
            ReduceResult(state.copy(viewState = ViewState.Error(null)), EffectResult.None),
            universitiesReducer.reduce(UniversitiesAction.LoadError(null), state)
        )
    }

    @Test
    fun reduce_shouldHandleOpenWebsiteAndReturnOpenWebsiteEffect() {
        val state = UniversitiesState()
        val url = "https://example.com"

        assertEquals(
            ReduceResult(state, EffectResult.Some(UniversitiesEffect.OpenWebsite(url))),
            universitiesReducer.reduce(UniversitiesAction.OpenWebsite(url), state)
        )
    }
}
