package com.florentmaufras.redux

/** Output of [Reducer.reduce]: the next [state] and an [effect] (default [Effect.none]). */
data class ReduceResult<State, Action>(
    val state: State,
    val effect: Effect<Action> = Effect.none(),
)
