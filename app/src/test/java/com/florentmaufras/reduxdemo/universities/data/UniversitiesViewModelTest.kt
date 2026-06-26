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
    private lateinit var viewModel: UniversitiesViewModel

    private val mockedState = mockk<UniversitiesState>()
    private val stateFlow = MutableStateFlow(mockedState)

    @BeforeEach
    fun setup() {
        every { mockedStore.state }.returns(stateFlow)
        viewModel = UniversitiesViewModel(mockedStore)
    }

    @Test
    fun dispatchAction_forwardsToStoreSend() {
        val action = mockk<UniversitiesAction>()
        justRun { mockedStore.send(action) }

        viewModel.dispatchAction(action)

        verify(exactly = 1) { mockedStore.send(action) }
    }
}
