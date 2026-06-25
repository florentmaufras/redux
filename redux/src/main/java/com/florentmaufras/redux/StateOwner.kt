package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns a feature's state: a [state] stream for observers, a synchronous
 * [currentState] read, and an [updateState] write. Abstracting this lets a
 * [Store] own its state directly ([OwnedStateOwner]) or delegate it into a slice
 * of a parent's state ([ScopedStateOwner]) without the store knowing which.
 *
 * [state] is a plain [Flow]; a UI that needs a `StateFlow` can collect it via
 * `stateIn` (as [Store] does).
 */
interface StateOwner<S : Any> {
    val state: Flow<S>
    val currentState: S
    fun updateState(transform: (S) -> S)
}

/** A [StateOwner] backed by its own [MutableStateFlow]. */
class OwnedStateOwner<S : Any>(initialState: S) : StateOwner<S> {
    private val _state = MutableStateFlow(initialState)
    override val state: Flow<S> = _state.asStateFlow()
    override val currentState: S get() = _state.value
    override fun updateState(transform: (S) -> S) = _state.update(transform)
}
