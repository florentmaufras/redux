package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow

interface EffectHandler<Action : com.florentmaufras.redux.Action, Effect : com.florentmaufras.redux.Effect> {
    fun handle(effect: Effect): Flow<Action>
}
