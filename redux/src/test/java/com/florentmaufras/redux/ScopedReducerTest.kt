package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import org.junit.Test

class ScopedReducerTest {

    data class ParentState(val child: ChildState = ChildState())
    data class ChildState(val count: Int = 0)

    sealed class ParentAction {
        data class IncrementChild(val by: Int) : ParentAction()
        data object Logout : ParentAction()
    }

    sealed class ChildAction {
        data class Increment(val by: Int) : ChildAction()
        data object RequestLogout : ChildAction()
    }

    object ChildReducer : Reducer<ChildAction, ChildState, Nothing> {
        override fun reduce(action: ChildAction, state: ChildState): ReduceResult<ChildState, Nothing> =
            when (action) {
                is ChildAction.Increment -> ReduceResult(state.copy(count = state.count + action.by), EffectResult.None)
                ChildAction.RequestLogout -> ReduceResult(state, EffectResult.None)
            }
    }

    private val scope = object : Scope<ParentAction, ParentState, ChildAction, ChildState> {
        override val toChildState: (ParentState) -> ChildState = { it.child }
        override val fromChildState: (ParentState, ChildState) -> ParentState = { p, c -> p.copy(child = c) }
        override val toChildAction: (ParentAction) -> ChildAction? = { action ->
            when (action) {
                is ParentAction.IncrementChild -> ChildAction.Increment(action.by)
                ParentAction.Logout -> ChildAction.RequestLogout
            }
        }
        override val fromChildAction: (ChildAction) -> ParentAction? = { childAction ->
            when (childAction) {
                ChildAction.RequestLogout -> ParentAction.Logout
                else -> null
            }
        }
    }

    private val scopedReducer = ScopedReducer(scope, ChildReducer)

    @Test
    fun incrementChild_updatesChildStateInParent() {
        val initial = ParentState(child = ChildState(count = 3))
        val result = scopedReducer.reduce(ParentAction.IncrementChild(2), initial)
        assertEquals(ParentState(child = ChildState(count = 5)), result.state)
        assertEquals(EffectResult.None, result.effect)
    }

    @Test
    fun childActionWithFromChildAction_bubblesParentActionAsEffect() {
        val result = scopedReducer.reduce(ParentAction.Logout, ParentState())
        assertEquals(EffectResult.Some(ParentAction.Logout), result.effect)
    }

    @Test
    fun childActionWithNoFromChildActionMapping_returnsNoEffect() {
        val result = scopedReducer.reduce(ParentAction.IncrementChild(1), ParentState())
        assertEquals(EffectResult.None, result.effect)
    }
}
