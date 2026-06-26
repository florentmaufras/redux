package com.florentmaufras.redux

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
class StoreTest {

    data class CounterState(val count: Int = 0, val loaded: String? = null)

    sealed interface CounterAction {
        data class Add(val by: Int) : CounterAction
        data object Load : CounterAction
        data class Loaded(val value: String) : CounterAction
        data object StartNever : CounterAction
        data object CancelNever : CounterAction
    }

    private class CounterStore(
        initial: CounterState = CounterState(),
        override val reducer: Reducer<CounterState, CounterAction>,
    ) : Store<CounterState, CounterAction>(initial)

    private val reducer = Reducer<CounterState, CounterAction> { state, action ->
        when (action) {
            is CounterAction.Add -> ReduceResult(state.copy(count = state.count + action.by))
            CounterAction.Load -> ReduceResult(state, Effect.run { emit(CounterAction.Loaded("ok")) })
            is CounterAction.Loaded -> ReduceResult(state.copy(loaded = action.value))
            CounterAction.StartNever -> ReduceResult(
                state,
                Effect.run<CounterAction> { kotlinx.coroutines.awaitCancellation() }
                    .cancellable("never"),
            )
            CounterAction.CancelNever -> ReduceResult(state, Effect.cancel("never"))
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun send_reducesAndUpdatesState() {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Add(3))
        assertEquals(3, store.currentState.count)
        assertEquals(CounterState(count = 3), store.state.value)
    }

    @Test
    fun effect_feedsEmittedActionBackThroughSend() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Load)
        advanceUntilIdle()
        assertEquals("ok", store.currentState.loaded)
    }

    @Test
    fun completedEffectJob_isRemovedFromTracking() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.Load)   // finite effect, no cancelId
        advanceUntilIdle()
        assertEquals(0, store.trackedEffectJobCount)
    }

    @Test
    fun cancel_stopsInFlightEffectUnderId() = runTest {
        val store = CounterStore(reducer = reducer)
        store.send(CounterAction.StartNever)
        assertEquals(1, store.trackedEffectJobCount)
        store.send(CounterAction.CancelNever)
        advanceUntilIdle()
        assertEquals(0, store.trackedEffectJobCount)
    }
}
