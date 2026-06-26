package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single-runtime store. [send] reduces an action, commits the new [state], runs the
 * returned [Effect], and re-sends the actions it emits. Cancellation is keyed by an
 * effect's `cancelId`.
 */
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
        runEffectIn(viewModelScope, effect)
    }

    private fun runEffectIn(scope: CoroutineScope, effect: Effect<Action>) {
        when (effect) {
            Effect.None -> Unit
            is Effect.Cancel -> effectJobs.remove(effect.id)?.cancel()
            is Effect.Actions -> {
                scope.launch { effect.flow.collect { send(it) } }
            }
            is Effect.Merge -> effect.effects.forEach { runEffectIn(scope, it) }
            is Effect.Cancellable -> {
                if (effect.cancelInFlight) effectJobs[effect.id]?.cancel()
                // Launch a parent job; the wrapped effect's coroutines run as its
                // children, so cancelling this job cancels the whole subtree.
                val job = scope.launch { runEffectIn(this, effect.effect) }
                effectJobs[effect.id] = job
                job.invokeOnCompletion { effectJobs.remove(effect.id, job) }
            }
        }
    }
}
