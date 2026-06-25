package com.florentmaufras.redux

data class ReduceResult<State : Any, Effect : Any>(
    val state: State,
    val effect: EffectResult<Effect> = EffectResult.None
)
