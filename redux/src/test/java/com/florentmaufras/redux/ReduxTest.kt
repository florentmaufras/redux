package com.florentmaufras.redux

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReduxTest {

    private val initialName = "initialName"
    private val reducerTest = ReducerTest()
    private val mockedRepository = mockk<Any>()
    private val effectHandlerTest = EffectHandlerTest(
        mockedRepository,
        UnconfinedTestDispatcher()
    )

    lateinit var storeTest: StoreTest

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        storeTest = StoreTest(
            initialName,
            reducerTest,
            effectHandlerTest
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun dispatchingActionSave_shouldEndUpInStateWithNameUpdated_withValuePassedInAction() = runTest {
        val name = "qwetwerweqreqw"
        coEvery { mockedRepository.toString() }.returns("")

        storeTest.dispatch(ActionTest.Save(name))

        assertEquals(storeTest.currentState, StateTest(name))
    }

    @Test
    fun dispatchingActionRollback_shouldEndUpInStateInitial() = runTest {
        val name = storeTest.currentState.name
        coEvery { mockedRepository.toString() }.returns("")

        storeTest.dispatch(ActionTest.Rollback)

        assertEquals(storeTest.currentState, StateTest(name))
    }

    @Test
    fun dispatchingActionWithSameCancelId_shouldCancelPreviousEffect() = runTest {
        coEvery { mockedRepository.toString() }.returns("")

        storeTest.dispatch(ActionTest.Save("first"), cancelId = "search")
        storeTest.dispatch(ActionTest.Save("second"), cancelId = "search")

        assertEquals(storeTest.currentState, StateTest("second"))
    }

    @Test
    fun dispatchingActionWithEffectWithAction_shouldEndUpInStateWithNameUpdated_withValuePassedInActionForEffect() = runTest {
        val name = "opewjrsijhewrsdcads"
        coEvery { mockedRepository.toString() }.returns("")

        storeTest.dispatch(ActionTest.ActionWithEffectWithAction(name))

        assertEquals(storeTest.currentState, StateTest(name))
    }

    @Test
    fun stateFlow_reflectsDispatchedState() = runTest {
        coEvery { mockedRepository.toString() }.returns("")

        assertEquals(StateTest(initialName), storeTest.state.value)

        storeTest.dispatch(ActionTest.Save("updated"))
        advanceUntilIdle()

        assertEquals(StateTest("updated"), storeTest.state.value)
    }

    @Test
    fun completedEffectJob_isRemovedFromTracking() = runTest {
        coEvery { mockedRepository.toString() }.returns("")

        storeTest.dispatch(ActionTest.Save("x"), cancelId = "save")
        advanceUntilIdle()

        // The SaveAPICall effect completes, so its tracked job must be dropped.
        assertEquals(0, storeTest.trackedEffectJobCount)
    }
}