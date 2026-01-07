package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class Store<Action : com.florentmaufras.redux.Action, State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect, R : Reducer<Action, State, Effect>, E : EffectHandler<Action, Effect>>(
    initialState: State
) : ViewModel() {

    abstract val reducer: R
    abstract val effectHandler: E

    val currentState: State
        get() = state.value

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    fun dispatch(action: Action) {
        val result = reducer.reduce(action, currentState)
        _state.update { result.state }
        result.effect?.let { effect->
            viewModelScope.launch {
                effectHandler.handle(effect)?.let { effectAction ->
                    dispatch(effectAction)
                }
            }
        }
    }
}