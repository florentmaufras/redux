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
 * returned [Effect], and re-sends the actions it emits. Cancellation is keyed by the
 * id an effect is tagged with via [Effect.cancellable]; an [Effect.cancel] for that id
 * tears down the running job and every effect nested under it.
 */
abstract class Store<State, Action>(initialState: State) : ViewModel() {

    protected abstract val reducer: Reducer<State, Action>

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()
    val currentState: State get() = _state.value

    // Cancel ids share one store-global namespace and each may have several jobs in
    // flight at once (effects tagged with the same id and cancelInFlight = false run
    // concurrently); [Effect.cancel] cancels every job under the id.
    private val effectJobs = mutableMapOf<Any, MutableList<Job>>()
    internal val trackedEffectJobCount: Int get() = effectJobs.values.sumOf { it.size }

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
            is Effect.Cancel -> cancelJobs(effect.id)
            is Effect.Actions -> {
                scope.launch { effect.flow.collect { send(it) } }
            }
            is Effect.Merge -> effect.effects.forEach { runEffectIn(scope, it) }
            is Effect.Cancellable -> {
                if (effect.cancelInFlight) cancelJobs(effect.id)
                // Launch a parent job; the wrapped effect's coroutines run as its
                // children, so cancelling this job cancels the whole subtree.
                val job = scope.launch { runEffectIn(this, effect.effect) }
                effectJobs.getOrPut(effect.id) { mutableListOf() }.add(job)
                job.invokeOnCompletion { untrack(effect.id, job) }
            }
        }
    }

    private fun cancelJobs(id: Any) {
        effectJobs.remove(id)?.forEach { it.cancel() }
    }

    private fun untrack(id: Any, job: Job) {
        val jobs = effectJobs[id] ?: return
        jobs.remove(job)
        if (jobs.isEmpty()) effectJobs.remove(id)
    }
}
