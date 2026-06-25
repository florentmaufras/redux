package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
