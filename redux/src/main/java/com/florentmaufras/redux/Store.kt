package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A [ViewModel] that drives one feature's unidirectional data flow.
 *
 * [dispatch] runs an [Action] through the [reducer], commits the new state to the
 * [stateOwner], and routes any produced [Effect] to [handleEffect]. Effects run
 * asynchronously and feed their resulting actions back through [dispatch],
 * closing the loop.
 *
 * Subclasses provide the [reducer] and wire effect execution by overriding
 * [handleEffect] — typically delegating to an [EffectHandler] via [launchEffect].
 * That seam is intentionally open so a feature can route effects however it needs
 * (one handler, several, or none).
 *
 * State ownership is injected as a [StateOwner], so the same [Store] works whether
 * it owns its state ([OwnedStateOwner]) or shares a slice of a parent's
 * ([ScopedStateOwner]).
 */
abstract class Store<Action : Any, State : Any, Effect : Any>(
    private val stateOwner: StateOwner<State>
) : ViewModel() {

    protected abstract val reducer: Reducer<Action, State, Effect>

    /** The latest state, read synchronously. */
    val currentState: State get() = stateOwner.currentState

    /**
     * The state as a hot [StateFlow] for the UI. Shared eagerly so `value` always
     * mirrors [currentState], even with no collectors.
     */
    val state: StateFlow<State> = stateOwner.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = stateOwner.currentState
    )

    private val effectJobs = mutableMapOf<String, Job>()

    // Visible for tests (module-internal, not part of the public API): number of
    // in-flight effect jobs currently tracked by cancelId.
    internal val trackedEffectJobCount: Int get() = effectJobs.size

    /**
     * Reduces [action] into the next state and runs any resulting effect. An
     * optional [cancelId] lets a later effect with the same id cancel an
     * in-flight one (e.g. debouncing a search).
     */
    // Note: dispatch is open so MockK can mock it in tests.
    open fun dispatch(action: Action, cancelId: String? = null) {
        val result = reducer.reduce(action, currentState)
        stateOwner.updateState { result.state }
        when (val effectResult = result.effect) {
            is EffectResult.Some -> handleEffect(effectResult.effect, cancelId)
            EffectResult.None -> Unit
        }
    }

    /**
     * Hook for executing an [Effect]. The default is a no-op; override it to run
     * effects, usually via [launchEffect]. A store with no effects can leave it
     * as is.
     */
    protected open fun handleEffect(effect: Effect, cancelId: String? = null) {}

    /**
     * Launches [block] for [effect] in [viewModelScope], dispatching each emitted
     * action back through [dispatch]. If [cancelId] is given, any in-flight job
     * under that id is cancelled first, and the job is dropped from tracking once
     * it completes.
     */
    protected fun launchEffect(
        effect: Effect,
        cancelId: String? = null,
        block: suspend (Effect) -> Flow<Action>
    ) {
        cancelId?.let { effectJobs[it]?.cancel() }
        val job = viewModelScope.launch {
            block(effect).collect { dispatch(it) }
        }
        cancelId?.let { id ->
            effectJobs[id] = job
            // Drop the entry once the job finishes so completed jobs don't
            // accumulate. Only remove if still mapped to this job, in case a
            // newer effect already replaced it under the same cancelId.
            job.invokeOnCompletion { effectJobs.remove(id, job) }
        }
    }
}
