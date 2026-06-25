package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface StateOwner<S : Any> {
    val state: Flow<S>
    val currentState: S
    fun updateState(transform: (S) -> S)
}

class OwnedStateOwner<S : Any>(initialState: S) : StateOwner<S> {
    private val _state = MutableStateFlow(initialState)
    override val state: Flow<S> = _state.asStateFlow()
    override val currentState: S get() = _state.value
    override fun updateState(transform: (S) -> S) = _state.update(transform)
}
