package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversitiesReducerTest {

    private lateinit var reducer: UniversitiesReducer

    @BeforeEach
    fun setup() {
        reducer = UniversitiesReducer(universitiesService = mockk(relaxed = true))
    }

    @Test
    fun loadUniversities_setsLoadingAndCountry() {
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.LoadUniversities("France"))
        assertEquals(
            UniversitiesState(viewState = ViewState.Loading, countrySearched = "France"),
            result.state,
        )
    }

    @Test
    fun universitiesLoaded_setsLoadedState() {
        val universities = emptyList<University>()
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.UniversitiesLoaded(universities))
        assertEquals(ViewState.Loaded(universities), result.state.viewState)
    }

    @Test
    fun loadError_setsErrorState() {
        val result = reducer.reduce(UniversitiesState(), UniversitiesAction.LoadError(null))
        assertEquals(ViewState.Error(null), result.state.viewState)
    }

    @Test
    fun openWebsite_leavesStateUnchanged() {
        val state = UniversitiesState(countrySearched = "France")
        val result = reducer.reduce(state, UniversitiesAction.OpenWebsite("https://example.com"))
        assertEquals(state, result.state)
    }
}
