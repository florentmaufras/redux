package com.florentmaufras.reduxdemo.universities.data

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UniversitiesViewModelTest {

    private val mockedStore: UniversitiesStore = mockk()
    private lateinit var universitiesViewModel: UniversitiesViewModel

    private val mockedState = mockk<UniversitiesState>()
    private val stateFlow = MutableStateFlow(mockedState)

    @BeforeEach
    fun setup() {

        every { mockedStore.state }.returns(stateFlow)
        every { mockedState.countrySearched }.returns("")

        universitiesViewModel = UniversitiesViewModel(mockedStore)
    }

    @Test
    fun init_shouldDispatchPresetSearch() {
        val country = "country"

        every { mockedStore.state }.returns(stateFlow)
        every { mockedState.countrySearched }.returns(country)
        justRun { mockedStore.dispatch(UniversitiesAction.LoadUniversities(country)) }

        universitiesViewModel = UniversitiesViewModel(mockedStore)

        verify(exactly = 1) { mockedStore.dispatch(UniversitiesAction.LoadUniversities(country)) }
    }

    @Test
    fun dispatchAction_shouldDispatchActionToTheStore() {
        val action = mockk<UniversitiesAction>()

        justRun { mockedStore.dispatch(action) }

        universitiesViewModel.dispatchAction(action)

        verify(exactly = 1) { mockedStore.dispatch(action) }
    }
}