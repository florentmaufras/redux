package com.florentmaufras.redux

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ScopedStateOwner<ParentState : Any, ChildState : Any>(
    private val parent: StateOwner<ParentState>,
    private val toChildState: (ParentState) -> ChildState,
    private val fromChildState: (ParentState, ChildState) -> ParentState
) : StateOwner<ChildState> {

    override val currentState: ChildState get() = toChildState(parent.currentState)

    override val state: StateFlow<ChildState> = object : StateFlow<ChildState> {
        override val value: ChildState get() = currentState
        override val replayCache: List<ChildState> get() = listOf(value)
        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ChildState>): Nothing {
            parent.state.map { toChildState(it) }.collect(collector)
            error("unreachable")
        }
    }

    override fun updateState(transform: (ChildState) -> ChildState) {
        parent.updateState { parentState ->
            val newChild = transform(toChildState(parentState))
            fromChildState(parentState, newChild)
        }
    }
}
