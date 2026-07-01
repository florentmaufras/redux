package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ForEachTest {

    // Test-only: flatten an effect tree to the actions it would emit (ignoring cancellation).
    private fun <A> Effect<A>.allActions(): Flow<A> = when (this) {
        Effect.None, is Effect.Cancel -> emptyFlow()
        is Effect.Actions -> flow
        is Effect.Cancellable -> effect.allActions()
        is Effect.Merge -> merge(*effects.map { it.allActions() }.toTypedArray())
    }

    data class Counter(override val id: Int, val count: Int = 0) : Identifiable<Int>

    sealed interface CounterAction {
        data class Add(val by: Int) : CounterAction
        data object Ping : CounterAction    // effect emits Add(1) back
        data object Work : CounterAction    // starts a cancellable never-ending effect
        data object Stop : CounterAction    // cancels the Work effect
    }

    data class ListState(
        val counters: List<Counter> = emptyList(),
        val pausedAll: Boolean = false,
    )

    sealed interface ListAction {
        data class Element(val id: Int, val action: CounterAction) : ListAction   // element-addressed
        data object PauseAll : ListAction                                          // broadcast
        data class Insert(val counter: Counter) : ListAction
    }

    private val childReducer = Reducer<Counter, CounterAction> { state, action ->
        when (action) {
            is CounterAction.Add -> ReduceResult(state.copy(count = state.count + action.by))
            CounterAction.Ping -> ReduceResult(state, Effect.send(CounterAction.Add(1)))
            CounterAction.Work ->
                ReduceResult(state, Effect.run<CounterAction> { kotlinx.coroutines.awaitCancellation() }.cancellable(state.id))
            CounterAction.Stop -> ReduceResult(state, Effect.cancel(state.id))
        }
    }

    private val parentReducer = Reducer<ListState, ListAction> { state, action ->
        when (action) {
            is ListAction.Insert -> ReduceResult(state.copy(counters = state.counters + action.counter))
            ListAction.PauseAll -> ReduceResult(state.copy(pausedAll = true))
            is ListAction.Element -> ReduceResult(state)   // observed; element routing handled by forEach
        }
    }

    private val scope = object : ForEachScope<ListState, ListAction, Counter, CounterAction, Int> {
        override val toChildren: (ListState) -> List<Counter> = { it.counters }
        override val fromChildren: (ListState, List<Counter>) -> ListState = { s, c -> s.copy(counters = c) }
        override val toChildAction: (ListAction) -> Pair<Int, CounterAction>? =
            { a -> (a as? ListAction.Element)?.let { it.id to it.action } }
        override val embedChildAction: (Int, CounterAction) -> ListAction =
            { id, a -> ListAction.Element(id, a) }
    }

    private val composed = parentReducer.forEach(scope, childReducer)

    @Test
    fun elementAction_updatesOnlyMatchingChild() {
        val state = ListState(counters = listOf(Counter(1), Counter(2)))
        val result = composed.reduce(state, ListAction.Element(2, CounterAction.Add(5)))
        assertEquals(listOf(Counter(1, 0), Counter(2, 5)), result.state.counters)
    }

    @Test
    fun broadcastAction_handledByParent() {
        val state = ListState(counters = listOf(Counter(1)))
        val result = composed.reduce(state, ListAction.PauseAll)
        assertEquals(true, result.state.pausedAll)
    }

    @Test
    fun childEffect_isLiftedWithIdEmbedded() = runTest {
        val state = ListState(counters = listOf(Counter(7)))
        val result = composed.reduce(state, ListAction.Element(7, CounterAction.Ping))
        assertEquals(
            listOf(ListAction.Element(7, CounterAction.Add(1))),
            result.effect.allActions().toList(),
        )
    }

    @Test
    fun unknownElementId_isNoOp() = runTest {
        val state = ListState(counters = listOf(Counter(1)))
        val result = composed.reduce(state, ListAction.Element(99, CounterAction.Add(5)))
        assertEquals(state, result.state)
        assertEquals(emptyList<ListAction>(), result.effect.allActions().toList())
    }
}
