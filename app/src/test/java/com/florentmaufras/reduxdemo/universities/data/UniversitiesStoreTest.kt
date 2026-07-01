package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.TestStore
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UniversitiesStoreTest {

    private val service: UniversitiesService = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadUniversities_emitsLoadedStateFromService() = runTest {
        val universities = listOf(mockk<University>())
        coEvery { service.getUniversities("France") } returns universities

        val store = TestStore(
            UniversitiesState(countrySearched = ""),
            UniversitiesReducer(universitiesService = service),
        )

        store.send(UniversitiesAction.LoadUniversities("France"))
        advanceUntilIdle()

        assertEquals(ViewState.Loaded(universities), store.currentState.viewState)
        assertEquals(UniversitiesAction.UniversitiesLoaded(universities), store.receivedActions.last())
    }

    @Test
    fun loadUniversities_emitsErrorStateOnFailure() = runTest {
        coEvery { service.getUniversities("France") } throws RuntimeException("boom")

        val store = TestStore(
            UniversitiesState(countrySearched = ""),
            UniversitiesReducer(universitiesService = service),
        )

        store.send(UniversitiesAction.LoadUniversities("France"))
        advanceUntilIdle()

        assertEquals(ViewState.Error("boom"), store.currentState.viewState)
        assertEquals(UniversitiesAction.LoadError("boom"), store.receivedActions.last())
    }
}
