package com.florentmaufras.redux

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface StateOwner<S : Any> {
    val state: StateFlow<S>
    val currentState: S get() = state.value
    fun updateState(transform: (S) -> S)
}

class OwnedStateOwner<S : Any>(initialState: S) : StateOwner<S> {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()
    override fun updateState(transform: (S) -> S) = _state.update(transform)
}
