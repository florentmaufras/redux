package com.florentmaufras.redux

import kotlinx.coroutines.flow.Flow

/**
 * Interprets an [Effect] — a serializable description of side-effecting work — by
 * performing it and emitting follow-up actions. Each store owns one handler and
 * runs only its own effects; effects are never handed across a feature boundary.
 */
interface EffectHandler<Action : Any, Effect : Any> {
    fun handle(effect: Effect): Flow<Action>
}
