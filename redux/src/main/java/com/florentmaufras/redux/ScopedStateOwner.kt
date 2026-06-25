package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A [StateOwner] over a child slice of a parent [StateOwner], defined by a state
 * lens. Reads project the parent state to the child; writes transform the child
 * and fold the result back into the parent.
 *
 * This is the mechanism for nested-stores composition: an effectful child
 * feature is its own [Store] (with its own reducer and [EffectHandler]) whose
 * state lives inside the parent's, shared through this owner. The child handles
 * its own effects locally — they are never propagated to the parent.
 */
class ScopedStateOwner<ParentState : Any, ChildState : Any>(
    private val parent: StateOwner<ParentState>,
    private val toChildState: (ParentState) -> ChildState,
    private val fromChildState: (ParentState, ChildState) -> ParentState
) : StateOwner<ChildState> {

    override val currentState: ChildState get() = toChildState(parent.currentState)

    override val state: Flow<ChildState> =
        parent.state.map { toChildState(it) }.distinctUntilChanged()

    override fun updateState(transform: (ChildState) -> ChildState) {
        parent.updateState { parentState ->
            val newChild = transform(toChildState(parentState))
            fromChildState(parentState, newChild)
        }
    }
}
