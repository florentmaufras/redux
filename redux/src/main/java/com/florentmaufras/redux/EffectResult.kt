package com.florentmaufras.redux

/**
 * Whether a [ReduceResult] carries an effect: [None] for none, or [Some]
 * wrapping the [Effect] to run. Modelling effects as plain data (rather than
 * launching them in the reducer) keeps reducers pure and lets tests assert which
 * effect a reduction produced without executing it.
 */
sealed class EffectResult<out Effect : Any> {
    data object None : EffectResult<Nothing>()
    data class Some<Effect : Any>(val effect: Effect) : EffectResult<Effect>()
}
