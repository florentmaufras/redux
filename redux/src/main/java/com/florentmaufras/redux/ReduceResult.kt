package com.florentmaufras.redux

data class ReduceResult<State: com.florentmaufras.redux.State, Effect: com.florentmaufras.redux.Effect?>(
    val state: State,
    val effect: Effect
)
