package com.florentmaufras.redux

/**
 * The pure heart of a feature: given the current [State] and an incoming
 * [Action], it computes the next state and, optionally, an [Effect] describing
 * side-effecting work to run.
 *
 * Implementations must be pure — no I/O, no mutation of external state, no
 * launching of work. Anything asynchronous is expressed as an [Effect] in the
 * returned [ReduceResult] and carried out later by an [EffectHandler].
 */
interface Reducer<Action : Any, State : Any, Effect : Any> {
    fun reduce(action: Action, state: State): ReduceResult<State, Effect>
}
