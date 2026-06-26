package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class Store<State, Action>(initialState: State) : ViewModel() {

    protected abstract val reducer: Reducer<State, Action>

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()
    val currentState: State get() = _state.value

    private val effectJobs = mutableMapOf<Any, Job>()
    internal val trackedEffectJobCount: Int get() = effectJobs.size

    // Note: send is open so MockK can mock it in tests.
    open fun send(action: Action) {
        val result = reducer.reduce(_state.value, action)
        _state.value = result.state
        runEffect(result.effect)
    }

    private fun runEffect(effect: Effect<Action>) {
        val id = effect.cancelId
        if (effect.isCancellation) {
            if (id != null) effectJobs.remove(id)?.cancel()
            return
        }
        if (id != null && effect.cancelInFlight) effectJobs[id]?.cancel()
        val job = viewModelScope.launch { effect.actions.collect { send(it) } }
        if (id != null) {
            effectJobs[id] = job
            job.invokeOnCompletion { effectJobs.remove(id, job) }
        }
    }
}
