package com.florentmaufras.redux

interface EffectHandler<Action: com.florentmaufras.redux.Action, Effect: com.florentmaufras.redux.Effect> {
    suspend fun handle(effect: Effect) : Action?
}