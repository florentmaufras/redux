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
class TestStoreTest {

    data class S(val count: Int = 0, val loaded: String? = null)

    sealed interface A {
        data class Add(val by: Int) : A
        data object Load : A
        data class Loaded(val value: String) : A
    }

    private val reducer = Reducer<S, A> { state, action ->
        when (action) {
            is A.Add -> ReduceResult(state.copy(count = state.count + action.by))
            A.Load -> ReduceResult(state, Effect.run { emit(A.Loaded("ok")) })
            is A.Loaded -> ReduceResult(state.copy(loaded = action.value))
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
    fun send_reducesAndExposesState() {
        val store = TestStore(S(), reducer)
        store.send(A.Add(3))
        assertEquals(3, store.currentState.count)
    }

    @Test
    fun recordsProcessedActionsIncludingEffectFedActions() = runTest {
        val store = TestStore(S(), reducer)
        store.send(A.Load)
        advanceUntilIdle()
        assertEquals(listOf(A.Load, A.Loaded("ok")), store.receivedActions)
        assertEquals("ok", store.currentState.loaded)
    }
}
