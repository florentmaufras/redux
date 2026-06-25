package com.florentmaufras.redux

/**
 * The output of [Reducer.reduce]: the next [state] and an optional [effect].
 *
 * [effect] defaults to [EffectResult.None], so a reducer branch that produces no
 * side effect can return `ReduceResult(state)`.
 */
data class ReduceResult<State : Any, Effect : Any>(
    val state: State,
    val effect: EffectResult<Effect> = EffectResult.None
)
