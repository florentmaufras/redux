package com.florentmaufras.redux

sealed class EffectResult<out Effect : Any> {
    data object None : EffectResult<Nothing>()
    data class Some<Effect : Any>(val effect: Effect) : EffectResult<Effect>()
}
