package com.florentmaufras.redux

sealed class EffectResult<out Effect : com.florentmaufras.redux.Effect> {
    data object None : EffectResult<Nothing>()
    data class Some<Effect : com.florentmaufras.redux.Effect>(val effect: Effect) : EffectResult<Effect>()
}
