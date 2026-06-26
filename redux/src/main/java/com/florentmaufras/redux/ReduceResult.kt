package com.florentmaufras.redux

data class ReduceResult<State, Action>(
    val state: State,
    val effect: Effect<Action> = Effect.none(),
)
