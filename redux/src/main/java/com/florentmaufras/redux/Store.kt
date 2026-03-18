package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class Store<Action : com.florentmaufras.redux.Action, State : com.florentmaufras.redux.State, Effect : com.florentmaufras.redux.Effect>(
    initialState: State
) : ViewModel() {

    protected abstract val reducer: Reducer<Action, State, Effect>
    protected abstract val effectHandler: EffectHandler<Action, Effect>

    val currentState: State
        get() = state.value

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    private val effectJobs = mutableMapOf<String, Job>()

    // Note: dispatch is open so MockK can mock it in tests.
    open fun dispatch(action: Action, cancelId: String? = null) {
        val result = reducer.reduce(action, currentState)
        _state.update { result.state }
        when (val effectResult = result.effect) {
            is EffectResult.Some -> {
                cancelId?.let { effectJobs[it]?.cancel() }
                val job = viewModelScope.launch {
                    effectHandler.handle(effectResult.effect).collect { dispatch(it) }
                }
                cancelId?.let { effectJobs[it] = job }
            }
            EffectResult.None -> Unit
        }
    }
}
