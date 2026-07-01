package com.florentmaufras.redux

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScopeTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Test-only: flatten an effect tree to the actions it would emit (ignoring cancellation).
    private fun <A> Effect<A>.allActions(): Flow<A> = when (this) {
        Effect.None, is Effect.Cancel -> emptyFlow()
        is Effect.Actions -> flow
        is Effect.Cancellable -> effect.allActions()
        is Effect.Merge -> merge(*effects.map { it.allActions() }.toTypedArray())
    }


    data class ChildState(val count: Int = 0)
    data class ParentState(val child: ChildState = ChildState(), val parentSaw: String? = null)

    sealed interface ChildAction {
        data class Increment(val by: Int) : ChildAction
        data object NotifyParent : ChildAction
        data object StartWork : ChildAction
        data object StopWork : ChildAction
    }

    sealed interface ParentAction {
        data class Child(val action: ChildAction) : ParentAction
    }

    private val childReducer = Reducer<ChildState, ChildAction> { state, action ->
        when (action) {
            is ChildAction.Increment -> ReduceResult(state.copy(count = state.count + action.by))
            ChildAction.NotifyParent -> ReduceResult(state, Effect.send(ChildAction.Increment(100)))
            ChildAction.StartWork ->
                ReduceResult(state, Effect.run<ChildAction> { awaitCancellation() }.cancellable("child-work"))
            ChildAction.StopWork -> ReduceResult(state, Effect.cancel("child-work"))
        }
    }

    private val parentReducer = Reducer<ParentState, ParentAction> { state, action ->
        when (action) {
            is ParentAction.Child ->
                if (action.action is ChildAction.NotifyParent)
                    ReduceResult(state.copy(parentSaw = "notified"))
                else ReduceResult(state)
        }
    }

    private val scope = object : Scope<ParentState, ParentAction, ChildState, ChildAction> {
        override val toChildState: (ParentState) -> ChildState = { it.child }
        override val fromChildState: (ParentState, ChildState) -> ParentState = { p, c -> p.copy(child = c) }
        override val toChildAction: (ParentAction) -> ChildAction? = { (it as? ParentAction.Child)?.action }
        override val embedChildAction: (ChildAction) -> ParentAction = { ParentAction.Child(it) }
    }

    private val composed = parentReducer.scope(scope, childReducer)

    private class ScopedStore(
        override val reducer: Reducer<ParentState, ParentAction>,
    ) : Store<ParentState, ParentAction>(ParentState())

    @Test
    fun childAction_updatesChildStateInParent() {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.Increment(5)))
        assertEquals(ChildState(count = 5), result.state.child)
    }

    @Test
    fun parentReducer_observesChildAction() {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.NotifyParent))
        assertEquals("notified", result.state.parentSaw)
    }

    @Test
    fun childEffect_isLiftedToParentActions() = runTest {
        val result = composed.reduce(ParentState(), ParentAction.Child(ChildAction.NotifyParent))
        assertEquals(
            listOf(ParentAction.Child(ChildAction.Increment(100))),
            result.effect.allActions().toList(),
        )
    }

    @Test
    fun childCancellableEffect_isCancelledThroughParentStore() = runTest {
        val store = ScopedStore(reducer = composed)

        store.send(ParentAction.Child(ChildAction.StartWork))
        assertEquals(1, store.trackedEffectJobCount)   // child effect lifted into the store's namespace

        store.send(ParentAction.Child(ChildAction.StopWork))
        advanceUntilIdle()
        assertEquals(0, store.trackedEffectJobCount)    // cancellation composes through scope
    }
}
