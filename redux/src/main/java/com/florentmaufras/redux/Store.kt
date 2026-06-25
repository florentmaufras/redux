package com.florentmaufras.redux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class Store<Action : Any, State : Any, Effect : Any>(
    private val stateOwner: StateOwner<State>
) : ViewModel() {

    protected abstract val reducer: Reducer<Action, State, Effect>

    val currentState: State get() = stateOwner.currentState
    val state: StateFlow<State> = stateOwner.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = stateOwner.currentState
    )

    private val effectJobs = mutableMapOf<String, Job>()

    // Note: dispatch is open so MockK can mock it in tests.
    open fun dispatch(action: Action, cancelId: String? = null) {
        val result = reducer.reduce(action, currentState)
        stateOwner.updateState { result.state }
        when (val effectResult = result.effect) {
            is EffectResult.Some -> handleEffect(effectResult.effect, cancelId)
            EffectResult.None -> Unit
        }
    }

    protected open fun handleEffect(effect: Effect, cancelId: String? = null) {}

    protected fun launchEffect(
        effect: Effect,
        cancelId: String? = null,
        block: suspend (Effect) -> Flow<Action>
    ) {
        cancelId?.let { effectJobs[it]?.cancel() }
        val job = viewModelScope.launch {
            block(effect).collect { dispatch(it) }
        }
        cancelId?.let { effectJobs[it] = job }
    }
}
