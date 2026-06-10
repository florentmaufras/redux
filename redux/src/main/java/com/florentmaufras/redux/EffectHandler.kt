package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow

interface EffectHandler<Action : Any, Effect : Any> {
    fun handle(effect: Effect): Flow<Action>
}
